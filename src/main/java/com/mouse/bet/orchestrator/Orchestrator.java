package com.mouse.bet.orchestrator;

import com.mouse.bet.converter.ModelConverter;
import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.service.ArbitrageService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.mouse.bet.orchestrator.model.BetLeg;
import com.mouse.bet.orchestrator.model.BetLegTask;
import com.mouse.bet.orchestrator.model.LegResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Thread-safe single-slot orchestrator: processes one ArbitrageOpportunity at a time.
 * Converts outcomes to BetLeg tasks and dispatches to per-bookmaker worker queues.
 *
 * Key improvements:
 * - Deduplication of arbs by externalId to prevent concurrent processing
 * - Better error handling and logging
 * - Cleaner separation of concerns
 * - More defensive null checks
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class Orchestrator {

    // ==================== EMOJI CONSTANTS ====================
    private static final String EMOJI_CLEANUP = "🧹";
    private static final String EMOJI_QUEUE = "📋";
    private static final String EMOJI_REMOVED = "🗑️";
    private static final String EMOJI_EMPTY = "📭";
    private static final String EMOJI_INFO = "ℹ️";
    private static final String EMOJI_SKIP = "⏭️";
    private static final String EMOJI_START = "🚀";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_FAILED = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_PROCESSING = "⚙️";

    // ==================== QUEUES & WORKERS ====================

    /** Single active ArbitrageOpportunity slot. */
    @Getter
    private final BlockingQueue<ArbitrageOpportunity> arbQueue = new LinkedBlockingQueue<>(100);

    /** Per-bookmaker worker task queues. */
    @Getter
    private final ConcurrentMap<BookMaker, BlockingQueue<BetLegTask>> workerQueues = new ConcurrentHashMap<>();

    /** Set of registered workers (keys of workerQueues). */
    @Getter
    private volatile Set<BookMaker> registeredWorkers = ConcurrentHashMap.newKeySet();

    /** Track arbs currently being processed to prevent duplicates */
    private final Set<String> processingArbs = ConcurrentHashMap.newKeySet();

    // ==================== DEPENDENCIES ====================

    private final ArbitrageService arbitrageService;

    // ==================== CONFIGURATION ====================

    @Value("${sporty.poll.interval.ms:1000}")
    private long pollIntervalMs;

    @Value("${arb.leg.max.retries:2}")
    private int maxRetries;

    @Value("${arb.leg.retry.backoff.seconds:3}")
    private long retryBackoffSeconds;

    @Value("${arb.human.delay.min.ms:15000}")
    private int humanDelayMinMs;

    @Value("${arb.human.delay.max.ms:25000}")
    private int humanDelayMaxMs;

    // ==================== EXECUTOR & LIFECYCLE ====================

    /** Orchestrator loop executor. */
    private final ExecutorService orchestratorExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "arb-orchestrator");
        t.setDaemon(false); // Changed to false to ensure graceful shutdown
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);

    // ==================== WORKER REGISTRATION ====================

    /**
     * Register a worker queue for a bookmaker. Call this at startup after creating workers.
     * Thread-safe operation.
     */
    public void registerWorker(BookMaker bookmaker, BlockingQueue<BetLegTask> queue) {
        Objects.requireNonNull(bookmaker, "Bookmaker cannot be null");
        Objects.requireNonNull(queue, "Queue cannot be null");

        log.info("{} Registering worker | Bookmaker: {} | QueueCapacity: {}",
                EMOJI_START, bookmaker, queue.remainingCapacity() + queue.size());

        workerQueues.put(bookmaker, queue);
        registeredWorkers.add(bookmaker);

        log.info("{} Worker registered successfully | Bookmaker: {} | TotalRegisteredWorkers: {}",
                EMOJI_SUCCESS, bookmaker, registeredWorkers.size());
    }

    /**
     * Unregister a worker (useful for dynamic worker management)
     */
    public void unregisterWorker(BookMaker bookmaker) {
        if (bookmaker == null) {
            log.warn("{} Cannot unregister null bookmaker", EMOJI_WARNING);
            return;
        }

        BlockingQueue<BetLegTask> removed = workerQueues.remove(bookmaker);
        registeredWorkers.remove(bookmaker);

        if (removed != null) {
            log.info("{} Worker unregistered | Bookmaker: {} | RemainingWorkers: {}",
                    EMOJI_INFO, bookmaker, registeredWorkers.size());
        } else {
            log.warn("{} Attempted to unregister non-existent worker | Bookmaker: {}",
                    EMOJI_WARNING, bookmaker);
        }
    }

    // ==================== ARB LOADING ====================

    /**
     * Non-blocking: put an ArbitrageOpportunity into the queue.
     * Returns false if queue is full or arb is already being processed.
     * Thread-safe with deduplication.
     */
    public boolean tryLoadArb(ArbitrageOpportunity arb) {
        if (arb == null) {
            log.warn("{} Cannot load null ArbitrageOpportunity", EMOJI_WARNING);
            return false;
        }

        if (!running.get()) {
            log.warn("{} Orchestrator not running, cannot load arb | ArbId: {}",
                    EMOJI_WARNING, arb.getId());
            return false;
        }

        // Deduplicate by externalId
        String externalId = arb.getExternalId();
        if (externalId != null && processingArbs.contains(externalId)) {
            log.debug("{} Arb already in queue or processing | ExternalId: {} | Skipping duplicate",
                    EMOJI_SKIP, externalId);
            return false;
        }

        boolean loaded = arbQueue.offer(arb);

        if (loaded) {
            if (externalId != null) {
                processingArbs.add(externalId);
            }
            log.info("{} Arb loaded into queue (non-blocking) | ArbId: {} | ExternalId: {} | QueueSize: {}",
                    EMOJI_QUEUE, arb.getId(), externalId, arbQueue.size());
        } else {
            log.warn("{} Failed to load arb (queue full) | ArbId: {} | QueueSize: {} | QueueCapacity: {}",
                    EMOJI_WARNING, arb.getId(), arbQueue.size(), arbQueue.remainingCapacity());
        }

        return loaded;
    }

    /**
     * Blocking: put an ArbitrageOpportunity into the queue.
     * Waits until space is available. Thread-safe with deduplication.
     */
    public void loadArb(ArbitrageOpportunity arb) throws InterruptedException {
        if (arb == null) {
            log.warn("{} Cannot load null ArbitrageOpportunity", EMOJI_WARNING);
            return;
        }

        if (!running.get()) {
            log.warn("{} Orchestrator not running, cannot load arb | ArbId: {}",
                    EMOJI_WARNING, arb.getId());
            return;
        }

        String externalId = arb.getExternalId();

        // Deduplicate by externalId
        if (externalId != null && processingArbs.contains(externalId)) {
            log.debug("{} Arb already in queue or processing | ExternalId: {} | Skipping duplicate",
                    EMOJI_SKIP, externalId);
            return;
        }

        log.info("{} Attempting to load arb (blocking) | ArbId: {} | ExternalId: {} | QueueSize: {}",
                EMOJI_QUEUE, arb.getId(), externalId, arbQueue.size());

        arbQueue.put(arb);

        if (externalId != null) {
            processingArbs.add(externalId);
        }

        log.info("{} Arb loaded into queue (blocking completed) | ArbId: {} | QueueSize: {}",
                EMOJI_SUCCESS, arb.getId(), arbQueue.size());
    }

    /**
     * Batch load arbs with automatic deduplication
     */
    public int loadArbs(List<ArbitrageOpportunity> arbs) {
        if (arbs == null || arbs.isEmpty()) {
            log.debug("{} No arbs to load", EMOJI_INFO);
            return 0;
        }

        // Deduplicate by externalId before loading
        Map<String, ArbitrageOpportunity> uniqueArbs = arbs.stream()
                .filter(arb -> arb != null && arb.getExternalId() != null)
                .collect(Collectors.toMap(
                        ArbitrageOpportunity::getExternalId,
                        arb -> arb,
                        (existing, replacement) -> {
                            // Keep the one with higher profit
                            return existing.getProfitPercentage()
                                    .compareTo(replacement.getProfitPercentage()) > 0
                                    ? existing : replacement;
                        }
                ));

        int loaded = 0;
        for (ArbitrageOpportunity arb : uniqueArbs.values()) {
            if (tryLoadArb(arb)) {
                loaded++;
            }
        }

        log.info("{} Batch load completed | TotalSubmitted: {} | Unique: {} | Loaded: {} | Rejected: {}",
                EMOJI_INFO, arbs.size(), uniqueArbs.size(), loaded, uniqueArbs.size() - loaded);

        return loaded;
    }

    // ==================== LIFECYCLE MANAGEMENT ====================

    /**
     * Start the orchestrator loop. Safe to call multiple times.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("{} Starting Orchestrator | RegisteredWorkers: {} | PollIntervalMs: {}",
                    EMOJI_START, registeredWorkers, pollIntervalMs);
            orchestratorExec.submit(this::runLoop);
        } else {
            log.debug("{} Orchestrator start() called but already running", EMOJI_INFO);
        }
    }

    /**
     * Stop the orchestrator loop gracefully.
     */
    public void stop() {
        if (!running.get()) {
            log.debug("{} Orchestrator already stopped", EMOJI_INFO);
            return;
        }

        log.info("{} Stopping Orchestrator | CurrentQueueSize: {} | ProcessingArbs: {}",
                EMOJI_CLEANUP, arbQueue.size(), processingArbs.size());

        running.set(false);

        try {
            orchestratorExec.shutdown();
            if (!orchestratorExec.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("{} Orchestrator did not terminate gracefully, forcing shutdown", EMOJI_WARNING);
                orchestratorExec.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("{} Interrupted during shutdown", EMOJI_WARNING);
            orchestratorExec.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("{} Orchestrator stopped successfully", EMOJI_SUCCESS);
    }

    // ==================== MAIN PROCESSING LOOP ====================

    private void runLoop() {
        log.info("{} Orchestrator loop started | Thread: {}",
                EMOJI_START, Thread.currentThread().getName());

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Poll with timeout to check running flag periodically
                ArbitrageOpportunity arb = arbQueue.poll(1, TimeUnit.SECONDS);

                if (arb == null) {
                    continue; // No arb available, continue loop
                }

                log.info("{} ======== Processing ArbitrageOpportunity ========", EMOJI_PROCESSING);
                log.info("{} ArbId: {} | ExternalId: {} | Status: {} | Outcomes: {} | Profit: {}%",
                        EMOJI_INFO, arb.getId(), arb.getExternalId(), arb.getStatus(),
                        arb.getOutcomes() != null ? arb.getOutcomes().size() : 0,
                        arb.getProfitPercentage());

                processOneArb(arb);

                log.info("{} ======== Completed ArbitrageOpportunity ========", EMOJI_SUCCESS);
                log.info("{} ArbId: {} | FinalStatus: {}", EMOJI_INFO, arb.getId(), arb.getStatus());

                // Human delay between arbs
                randomHumanDelay(humanDelayMinMs, humanDelayMaxMs);

            } catch (InterruptedException ie) {
                log.warn("{} Orchestrator loop interrupted", EMOJI_WARNING);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.error("{} Unexpected error in orchestrator loop | Type: {} | Message: {}",
                        EMOJI_FAILED, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            }
        }

        log.info("{} Orchestrator loop stopped | FinalQueueSize: {}",
                EMOJI_CLEANUP, arbQueue.size());
    }

    // ==================== ARB PROCESSING ====================

    /**
     * Process a single arbitrage opportunity.
     * Thread-safe operation that handles the complete lifecycle.
     */
    public void processOneArb(ArbitrageOpportunity arb) throws InterruptedException {
        String externalId = arb.getExternalId();

        try {
            log.info("{} Starting arb processing | ArbId: {} | ExternalId: {} | CurrentStatus: {}",
                    EMOJI_PROCESSING, arb.getId(), externalId, arb.getStatus());

            // Validate arb before processing
            if (!validateArb(arb)) {
                log.error("{} Arb validation failed | ArbId: {}", EMOJI_FAILED, arb.getId());
                markArbAsFailed(arb, "Validation failed");
                return;
            }

            // Mark as IN_PROGRESS
            arb.setStatus(ArbStatus.IN_PROGRESS);
            arbitrageService.saveOrUpdateArbitrage(arb); // Use thread-safe method
            log.info("{} Arb marked as IN_PROGRESS | ArbId: {}", EMOJI_INFO, arb.getId());

            // Convert outcomes to BetLeg tasks grouped by bookmaker
            Map<BookMaker, BetLeg> legsByBookmaker = convertOutcomesToLegs(arb);
            log.info("{} Outcomes converted to BetLegs | ArbId: {} | BookmakersCount: {} | Bookmakers: {}",
                    EMOJI_INFO, arb.getId(), legsByBookmaker.size(), legsByBookmaker.keySet());

            // If no legs, complete immediately
            if (legsByBookmaker.isEmpty()) {
                log.warn("{} Arb has no valid outcomes, completing immediately | ArbId: {}",
                        EMOJI_WARNING, arb.getId());
                arb.setStatus(ArbStatus.COMPLETED);
                arbitrageService.saveOrUpdateArbitrage(arb);
                return;
            }

            // Check all required workers are registered
            Set<BookMaker> missingWorkers = new HashSet<>(legsByBookmaker.keySet());
            missingWorkers.removeAll(registeredWorkers);

            if (!missingWorkers.isEmpty()) {
                log.error("{} Arb rejected - missing workers | ArbId: {} | MissingWorkers: {} | RegisteredWorkers: {}",
                        EMOJI_FAILED, arb.getId(), missingWorkers, registeredWorkers);
                markArbAsFailed(arb, "Missing workers: " + missingWorkers);
                return;
            }

            log.info("{} All required workers available | ArbId: {} | RequiredBookmakers: {}",
                    EMOJI_SUCCESS, arb.getId(), legsByBookmaker.keySet());

            // Dispatch legs to workers and wait for completion
            dispatchAndWait(arb, legsByBookmaker);

        } catch (Exception e) {
            log.error("{} Error processing arb | ArbId: {} | Error: {}",
                    EMOJI_FAILED, arb.getId(), e.getMessage(), e);
            markArbAsFailed(arb, "Processing error: " + e.getMessage());
        } finally {
            // Always remove from processing set
            if (externalId != null) {
                processingArbs.remove(externalId);
                log.debug("{} Removed arb from processing set | ExternalId: {}",
                        EMOJI_INFO, externalId);
            }
        }
    }

    /**
     * Validate arb before processing
     */
    private boolean validateArb(ArbitrageOpportunity arb) {
        if (arb == null) {
            log.error("{} Arb is null", EMOJI_FAILED);
            return false;
        }

        if (arb.getOutcomes() == null || arb.getOutcomes().isEmpty()) {
            log.error("{} Arb has no outcomes | ArbId: {}", EMOJI_FAILED, arb.getId());
            return false;
        }

        if (arb.getStatus() == ArbStatus.COMPLETED || arb.getStatus() == ArbStatus.EXPIRED) {
            log.warn("{} Arb already processed | ArbId: {} | Status: {}",
                    EMOJI_WARNING, arb.getId(), arb.getStatus());
            return false;
        }

        return true;
    }

    /**
     * Mark arb as failed with a reason
     */
    private void markArbAsFailed(ArbitrageOpportunity arb, String reason) {
        try {
            arb.setStatus(ArbStatus.FAILED);
            arbitrageService.saveOrUpdateArbitrage(arb);
            log.error("{} Arb marked as FAILED | ArbId: {} | Reason: {}",
                    EMOJI_FAILED, arb.getId(), reason);
        } catch (Exception e) {
            log.error("{} Failed to mark arb as FAILED | ArbId: {} | Error: {}",
                    EMOJI_FAILED, arb.getId(), e.getMessage(), e);
        }
    }

    // ==================== OUTCOME CONVERSION ====================

    /**
     * Convert ArbitrageOpportunity outcomes to BetLeg tasks grouped by bookmaker.
     * Thread-safe operation.
     */
    private Map<BookMaker, BetLeg> convertOutcomesToLegs(ArbitrageOpportunity arb) {
        if (arb.getOutcomes() == null || arb.getOutcomes().isEmpty()) {
            log.warn("{} No outcomes found for ArbitrageOpportunity | ArbId: {}",
                    EMOJI_WARNING, arb.getId());
            return Collections.emptyMap();
        }

        Map<BookMaker, BetLeg> legsByBookmaker = new HashMap<>();

        for (ArbOutcome outcome : arb.getOutcomes()) {
            try {
                BookMaker bookmaker = outcome.getBookmakerName();

                if (bookmaker == null) {
                    log.warn("{} Outcome has null bookmaker | ArbId: {} | OutcomeId: {}",
                            EMOJI_WARNING, arb.getId(), outcome.getId());
                    continue;
                }

                BetLeg betLeg = ModelConverter.convertFromArbOutcome(outcome);

                legsByBookmaker.put(bookmaker, betLeg);

                log.debug("{} Converted outcome to BetLeg | ArbId: {} | Bookmaker: {} | Outcome: {} | Odds: {} | Stake: {}",
                        EMOJI_INFO, arb.getId(), bookmaker, outcome.getOutComeName(),
                        outcome.getOdds(), outcome.getStake());

            } catch (Exception e) {
                log.error("{} Failed to convert outcome to BetLeg | ArbId: {} | OutcomeId: {} | Error: {}",
                        EMOJI_FAILED, arb.getId(), outcome.getId(), e.getMessage(), e);
            }
        }

        if (legsByBookmaker.isEmpty()) {
            log.error("{} No valid legs created from outcomes | ArbId: {} | TotalOutcomes: {}",
                    EMOJI_FAILED, arb.getId(), arb.getOutcomes().size());
        }

        return legsByBookmaker;
    }

    // ==================== LEG DISPATCH & COORDINATION ====================

    /**
     * Dispatch BetLeg tasks to workers and wait for all to complete.
     * Uses Phaser for synchronization.
     */
    private void dispatchAndWait(ArbitrageOpportunity arb, Map<BookMaker, BetLeg> legsByBookmaker)
            throws InterruptedException {

        List<BookMaker> targets = new ArrayList<>(legsByBookmaker.keySet());
        Phaser barrier = new Phaser(targets.size());
        ConcurrentMap<BookMaker, LegResult> results = new ConcurrentHashMap<>();

        log.info("{} Initializing leg dispatch | ArbId: {} | TargetsCount: {} | PhaserParties: {}",
                EMOJI_PROCESSING, arb.getId(), targets.size(), barrier.getRegisteredParties());

        // Dispatch BetLegTask wrappers to matching workers
        for (BookMaker bookmaker : targets) {
            BlockingQueue<BetLegTask> queue = workerQueues.get(bookmaker);

            if (queue == null) {
                log.error("{} Worker queue missing at dispatch time | ArbId: {} | Bookmaker: {}",
                        EMOJI_FAILED, arb.getId(), bookmaker);
                markArbAsFailed(arb, "Worker queue missing for " + bookmaker);
                return;
            }

            BetLeg betLeg = legsByBookmaker.get(bookmaker);

            // Create BetLegTask wrapper with orchestration metadata
            BetLegTask task = BetLegTask.builder()
                    .betLeg(betLeg)
                    .arbId(arb.getId())
                    .arb(arb)
                    .bookmaker(bookmaker)
                    .barrier(barrier)
                    .results(results)
                    .maxRetries(maxRetries)
                    .retryBackoff(Duration.ofSeconds(retryBackoffSeconds))
                    .build();

            log.info("{} Dispatching BetLegTask | ArbId: {} | Bookmaker: {} | Outcome: {} | Odds: {} | Stake: {} | QueueSize: {}",
                    EMOJI_QUEUE, arb.getId(), bookmaker, betLeg.outcome(), betLeg.expectedOdds(),
                    betLeg.stakeAmount(), queue.size());

            queue.put(task); // Blocking put

            log.info("{} BetLegTask dispatched successfully | ArbId: {} | Bookmaker: {} | QueueSize: {}",
                    EMOJI_SUCCESS, arb.getId(), bookmaker, queue.size());
        }

        log.info("{} ==================== BLOCKED: Waiting for {} legs to complete ====================",
                EMOJI_INFO, targets.size());

        // BLOCK until ALL workers complete their tasks (via barrier.arriveAndDeregister())
        int phase = barrier.getPhase();
        barrier.awaitAdvance(phase);

        log.info("{} All BetLegTasks completed | ArbId: {} | ResultsReceived: {} | ExpectedResults: {}",
                EMOJI_SUCCESS, arb.getId(), results.size(), targets.size());

        // Log individual leg results
        results.forEach((bookmaker, result) -> {
            String emoji = result.isSuccess() ? EMOJI_SUCCESS : EMOJI_FAILED;
            log.info("{} Leg result | ArbId: {} | Bookmaker: {} | Success: {} | Message: {}",
                    emoji, arb.getId(), bookmaker, result.isSuccess(), result.getMessage());
        });

        // Finalize arb status based on results
        finalizeArb(arb, results, targets.size());
    }

    /**
     * Finalize arb status based on leg results
     */
    private void finalizeArb(ArbitrageOpportunity arb, Map<BookMaker, LegResult> results, int expectedCount) {
        boolean allSuccess = results.size() == expectedCount
                && results.values().stream().allMatch(LegResult::isSuccess);

        ArbStatus finalStatus = allSuccess ? ArbStatus.COMPLETED : ArbStatus.FAILED;
        arb.setStatus(finalStatus);
        arb.setResultMap(results);
        arbitrageService.saveOrUpdateArbitrage(arb);

        if (allSuccess) {
            log.info("{} Arb completed successfully | ArbId: {} | Status: {} | SuccessfulLegs: {}/{}",
                    EMOJI_SUCCESS, arb.getId(), finalStatus, results.size(), expectedCount);
        } else {
            long failedCount = results.values().stream().filter(r -> !r.isSuccess()).count();
            log.error("{} Arb failed | ArbId: {} | Status: {} | FailedLegs: {} | SuccessfulLegs: {} | TotalLegs: {}",
                    EMOJI_FAILED, arb.getId(), finalStatus, failedCount,
                    results.size() - failedCount, expectedCount);
        }
    }

    // ==================== SCHEDULED CLEANUP ====================

    /**
     * Scheduled cleanup of stale items in queues.
     * Runs every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void cleanupQueues() {
        if (!running.get()) {
            return; // Don't cleanup if not running
        }

        log.trace("{} Starting scheduled queue cleanup", EMOJI_CLEANUP);

        int totalRemoved = 0;
        int arbsRemoved = cleanArbQueueIfNotEmpty();
        int legTasksRemoved = cleanNonEmptyWorkerQueues();
        totalRemoved = arbsRemoved + legTasksRemoved;

        if (totalRemoved > 0) {
            log.info("{} Cleanup completed | RemovedArbs: {} | RemovedLegTasks: {} | Total: {}",
                    EMOJI_CLEANUP, arbsRemoved, legTasksRemoved, totalRemoved);
        } else {
            log.trace("{} {} Cleanup skipped | All queues empty", EMOJI_CLEANUP, EMOJI_EMPTY);
        }
    }

    private int cleanArbQueueIfNotEmpty() {
        if (arbQueue.isEmpty()) {
            return 0;
        }

        int removed = 0;
        ArbitrageOpportunity arb;

        while ((arb = arbQueue.poll()) != null) {
            removed++;
            String externalId = arb.getExternalId();

            // Remove from processing set
            if (externalId != null) {
                processingArbs.remove(externalId);
            }

            log.debug("{} Removed ArbitrageOpportunity from queue | ArbId: {} | ExternalId: {}",
                    EMOJI_REMOVED, arb.getId(), externalId);
        }

        log.info("{} Cleared arbQueue | Removed: {} arb(s)", EMOJI_CLEANUP, removed);
        return removed;
    }

    private int cleanNonEmptyWorkerQueues() {
        int totalRemoved = 0;

        for (Map.Entry<BookMaker, BlockingQueue<BetLegTask>> entry : workerQueues.entrySet()) {
            BookMaker bookmaker = entry.getKey();
            BlockingQueue<BetLegTask> queue = entry.getValue();

            if (queue.isEmpty()) {
                continue;
            }

            int removed = 0;
            BetLegTask task;

            while ((task = queue.poll()) != null) {
                removed++;
                log.trace("{} Removed BetLegTask from {} queue | TaskId: {} | ArbId: {}",
                        EMOJI_REMOVED, bookmaker, task.getTaskId(), task.getArbId());
            }

            if (removed > 0) {
                log.debug("{} Cleared {} queue | Removed: {} task(s)",
                        EMOJI_CLEANUP, bookmaker, removed);
                totalRemoved += removed;
            }
        }

        return totalRemoved;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Get current queue statistics
     */
    public QueueStats getQueueStats() {
        int arbQueueSize = arbQueue.size();
        int totalLegTasks = workerQueues.values().stream()
                .mapToInt(BlockingQueue::size)
                .sum();
        int processingCount = processingArbs.size();

        return new QueueStats(arbQueueSize, totalLegTasks, workerQueues.size(), processingCount);
    }

    public record QueueStats(
            int arbQueueSize,
            int totalLegTasks,
            int workerQueueCount,
            int processingArbsCount
    ) {
        @Override
        public String toString() {
            return String.format("QueueStats[arbs=%d, legTasks=%d, workers=%d, processing=%d]",
                    arbQueueSize, totalLegTasks, workerQueueCount, processingArbsCount);
        }
    }

    /**
     * Force immediate cleanup (useful for testing/debugging)
     */
    public void forceCleanup() {
        log.warn("{} Force cleanup triggered manually", EMOJI_CLEANUP);
        cleanupQueues();
    }

    /**
     * Check if arb queue is empty
     */
    public boolean isArbQueueEmpty() {
        return arbQueue.isEmpty();
    }

    /**
     * Check if orchestrator is running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get count of arbs currently being processed
     */
    public int getProcessingArbsCount() {
        return processingArbs.size();
    }

    /**
     * Clear processing arbs set (use with caution - mainly for testing)
     */
    public void clearProcessingArbs() {
        log.warn("{} Clearing processing arbs set | Count: {}", EMOJI_WARNING, processingArbs.size());
        processingArbs.clear();
    }

    /**
     * Random human-like delay between operations
     */
    private void randomHumanDelay(int minMs, int maxMs) throws InterruptedException {
        int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs + 1);
        log.debug("{} Human delay | DurationMs: {}", EMOJI_INFO, delay);
        Thread.sleep(delay);
    }
}