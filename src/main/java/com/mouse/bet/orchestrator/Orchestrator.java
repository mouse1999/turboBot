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

/**
 * Single-slot orchestrator: processes one ArbitrageOpportunity at a time.
 * Converts outcomes to BetLeg tasks and dispatches to per-bookmaker worker queues.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class Orchestrator {

    private static final String EMOJI_CLEANUP = "🧹";
    private static final String EMOJI_QUEUE = "📋";
    private static final String EMOJI_REMOVED = "🗑️";
    private static final String EMOJI_EMPTY = "📭";
    private static final String EMOJI_INFO = "ℹ️";
    private static final String EMOJI_SKIP = "⏭️";

    /** Single active ArbitrageOpportunity slot. */
    @Getter
    private final BlockingQueue<ArbitrageOpportunity> arbQueue = new ArrayBlockingQueue<>(1);

    /** Per-bookmaker worker task queues. */
    @Getter
    private final ConcurrentMap<BookMaker, BlockingQueue<BetLegTask>> workerQueues = new ConcurrentHashMap<>();


    /** Set of registered workers (keys of workerQueues). */
    @Getter
    private volatile Set<BookMaker> registeredWorkers = Set.of();

    private final ArbitrageService arbitrageService;

    @Value("${sporty.poll.interval.ms:2000}")
    private long pollIntervalMs;

    /** Retry policy for each Leg. */
    @Value("${arb.leg.max.retries:3}")
    private int maxRetries;

    @Value("${arb.leg.retry.backoff.seconds:2}")
    private long retryBackoffSeconds;

    /** Orchestrator loop. */
    private final ExecutorService orchestratorExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "arb-orchestrator");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);

   
    /** Register a worker queue for a bookmaker. Call this at startup after creating workers. */
    public void registerWorker(BookMaker bookmaker, BlockingQueue<BetLegTask> queue) {
        Objects.requireNonNull(bookmaker, "Bookmaker cannot be null");
        Objects.requireNonNull(queue, "Queue cannot be null");

        log.info("Registering worker | Bookmaker: {} | QueueCapacity: {}",
                bookmaker, queue.remainingCapacity() + queue.size());

        workerQueues.put(bookmaker, queue);
        registeredWorkers = Set.copyOf(workerQueues.keySet());

        log.info("Worker registered successfully | Bookmaker: {} | TotalRegisteredWorkers: {}",
                bookmaker, registeredWorkers.size());
    }

    /** Non-blocking: put an ArbitrageOpportunity into the single slot; returns false if busy. */
    public boolean tryLoadArb(ArbitrageOpportunity arb) {
        Objects.requireNonNull(arb, "ArbitrageOpportunity cannot be null");
        boolean loaded = arbQueue.offer(arb);

        if (loaded) {
            log.info("Arb loaded into queue (non-blocking) | ArbId: {} | ExternalId: {} | ArbStatus: {}",
                    arb.getId(), arb.getExternalId(), arb.getStatus());
        } else {
            log.warn("Failed to load Arb (queue full) | ArbId: {} | QueueSize: {}",
                    arb.getId(), arbQueue.size());
        }

        return loaded;
    }

    /** Blocking: put an ArbitrageOpportunity into the single slot; waits until free. */
    public void loadArb(ArbitrageOpportunity arb) throws InterruptedException {
        Objects.requireNonNull(arb, "ArbitrageOpportunity cannot be null");
        log.info("Attempting to load Arb (blocking) | ArbId: {} | QueueSize: {}",
                arb.getId(), arbQueue.size());

        arbQueue.put(arb);

        log.info("Arb loaded into queue (blocking completed) | ArbId: {} | ArbStatus: {}",
                arb.getId(), arb.getStatus());
    }

    /** Start the orchestrator loop. Safe to call multiple times. */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting Orchestrator | RegisteredWorkers: {} | PollIntervalMs: {}",
                    registeredWorkers, pollIntervalMs);
            orchestratorExec.submit(this::runLoop);
        } else {
            log.debug("Orchestrator start() called but already running");
        }
    }

    /** Stop the orchestrator loop. */
    public void stop() {
        log.info("Stopping Orchestrator | CurrentQueueSize: {}", arbQueue.size());
        running.set(false);
        orchestratorExec.shutdownNow();
        log.info("Orchestrator shutdown initiated");
    }

    private void runLoop() {
        log.info("Orchestrator loop started | Thread: {}", Thread.currentThread().getName());

        while (running.get()) {
            try {
                log.trace("Polling arb queue | QueueSize: {}", arbQueue.size());
                ArbitrageOpportunity arb = arbQueue.peek();

                if (arb == null) {
                    Thread.sleep(100); // Small sleep to reduce CPU usage during idle
                    continue;
                }

                log.info("=== Processing ArbitrageOpportunity | ArbId: {} | ExternalId: {} | ArbStatus: {} | OutcomesCount: {} ===",
                        arb.getId(), arb.getExternalId(), arb.getStatus(),
                        arb.getOutcomes() != null ? arb.getOutcomes().size() : 0);

                processOneArb(arb);

                log.info("=== Completed Processing ArbitrageOpportunity | ArbId: {} | FinalArbStatus: {} ===",
                        arb.getId(), arb.getStatus());

                arbQueue.clear();

                // Random human delay between arbs
                randomHumanDelay(15000, 25000);

            } catch (InterruptedException ie) {
                log.warn("Orchestrator loop interrupted", ie);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.error("Unexpected error in orchestrator loop | Type: {} | Message: {}",
                        ex.getClass().getSimpleName(), ex.getMessage(), ex);
            }
        }

        log.info("Orchestrator loop stopped | FinalQueueSize: {}", arbQueue.size());
    }

    public void processOneArb(ArbitrageOpportunity arb) throws InterruptedException {
        log.info("Starting arb processing | ArbId: {} | ExternalId: {} | CurrentArbStatus: {}",
                arb.getId(), arb.getExternalId(), arb.getStatus());

        // Mark as IN_PROGRESS
        arb.setStatus(ArbStatus.IN_PROGRESS);
        arbitrageService.saveArbitrageOpportunity(arb);
        log.info("Arb marked as IN_PROGRESS | ArbId: {}", arb.getId());

        // Convert outcomes to BetLeg tasks grouped by bookmaker
        Map<BookMaker, BetLeg> legsByBookmaker = convertOutcomesToLegs(arb);
        log.info("Outcomes converted to BetLegs | ArbId: {} | BookmakersCount: {} | Bookmakers: {}",
                arb.getId(), legsByBookmaker.size(), legsByBookmaker.keySet());

        // If no legs, complete immediately
        if (legsByBookmaker.isEmpty()) {
            log.warn("Arb has no valid outcomes, completing immediately | ArbId: {}", arb.getId());
            arb.setStatus(ArbStatus.COMPLETED);
            arbitrageService.saveArbitrageOpportunity(arb);
            return;
        }

        // Check all required workers are registered
        Set<BookMaker> missingWorkers = new HashSet<>(legsByBookmaker.keySet());
        missingWorkers.removeAll(registeredWorkers);

        if (!missingWorkers.isEmpty()) {
            log.error("Arb rejected - missing workers | ArbId: {} | MissingWorkers: {} | RegisteredWorkers: {}",
                    arb.getId(), missingWorkers, registeredWorkers);
            arb.setStatus(ArbStatus.FAILED);
            arbitrageService.saveArbitrageOpportunity(arb);
            return;
        }

        log.info("All required workers available | ArbId: {} | RequiredBookmakers: {}",
                arb.getId(), legsByBookmaker.keySet());

        // Dispatch legs to workers and wait for completion
        dispatchAndWait(arb, legsByBookmaker);
    }

    private Map<BookMaker, BetLeg> convertOutcomesToLegs(ArbitrageOpportunity arb) {
        if (arb.getOutcomes() == null || arb.getOutcomes().isEmpty()) {
            log.warn("No outcomes found for ArbitrageOpportunity | ArbId: {}", arb.getId());
            return Collections.emptyMap();
        }

        Map<BookMaker, BetLeg> legsByBookmaker = new HashMap<>();

        for (ArbOutcome outcome : arb.getOutcomes()) {
            try {
                BookMaker bookmaker = outcome.getBookmakerName();

                BetLeg betLeg = ModelConverter.convertFromArbOutcome(outcome);

                legsByBookmaker.put(bookmaker, betLeg);

                log.debug("Converted outcome to BetLeg | ArbId: {} | Bookmaker: {} | Outcome: {} | Odds: {} | Stake: {}",
                        arb.getId(), bookmaker, outcome.getOutcomeName(), outcome.getOdds(), outcome.getStake());

            } catch (Exception e) {
                log.error("Failed to convert outcome to BetLeg | ArbId: {} | OutcomeId: {} | Error: {}",
                        arb.getId(), outcome.getId(), e.getMessage(), e);
            }
        }

        return legsByBookmaker;
    }

    private void dispatchAndWait(ArbitrageOpportunity arb, Map<BookMaker, BetLeg> legsByBookmaker)
            throws InterruptedException {

        List<BookMaker> targets = new ArrayList<>(legsByBookmaker.keySet());
        Phaser barrier = new Phaser(targets.size());
        ConcurrentMap<BookMaker, LegResult> results = new ConcurrentHashMap<>();

        log.info("Initializing leg dispatch | ArbId: {} | TargetsCount: {} | PhaserParties: {}",
                arb.getId(), targets.size(), barrier.getRegisteredParties());

        // Dispatch BetLegTask wrappers to matching workers
        for (BookMaker bookmaker : targets) {
            BlockingQueue<BetLegTask> queue = workerQueues.get(bookmaker);

            if (queue == null) {
                log.error("Worker queue missing at dispatch time | ArbId: {} | Bookmaker: {}",
                        arb.getId(), bookmaker);
                arb.setStatus(ArbStatus.FAILED);
                arbitrageService.saveArbitrageOpportunity(arb);
                return;
            }

            BetLeg betLeg = legsByBookmaker.get(bookmaker);

            // Create BetLegTask wrapper with orchestration metadata
            BetLegTask task = BetLegTask.builder()
                    .betLeg(betLeg)
                    .arbId(arb.getId())
                    .bookmaker(bookmaker)
                    .barrier(barrier)
                    .results(results)
                    .maxRetries(maxRetries)
                    .retryBackoff(Duration.ofSeconds(retryBackoffSeconds))
                    .build();

            log.info("Dispatching BetLegTask | ArbId: {} | Bookmaker: {} | Outcome: {} | Odds: {} | Stake: {} | QueueSize: {}",
                    arb.getId(), bookmaker, betLeg.outcome(), betLeg.expectedOdds(),
                    betLeg.stakeAmount(), queue.size());

            queue.put(task); // Blocking put

            log.info("BetLegTask dispatched successfully | ArbId: {} | Bookmaker: {} | QueueSize: {}",
                    arb.getId(), bookmaker, queue.size());
        }

        log.info("All BetLegTasks dispatched, waiting for completion | ArbId: {} | TotalLegs: {} | Phase: {}",
                arb.getId(), targets.size(), barrier.getPhase());

        // BLOCK until ALL workers complete their tasks (via barrier.arriveAndDeregister())
        log.info("==================== BLOCKED until all legs finish ====================");
        int phase = barrier.getPhase();
        barrier.awaitAdvance(phase);

        log.info("All BetLegTasks completed | ArbId: {} | ResultsReceived: {} | ExpectedResults: {}",
                arb.getId(), results.size(), targets.size());

        // Log individual leg results
        results.forEach((bookmaker, result) -> {
            log.info("Leg result | ArbId: {} | Bookmaker: {} | Success: {} | Message: {}",
                    arb.getId(), bookmaker, result.isSuccess(), result.getMessage());
        });

        // Finalize arb ArbStatus based on results
        finalizeArb(arb, results, targets.size());
    }

    private void finalizeArb(ArbitrageOpportunity arb, Map<BookMaker, LegResult> results, int expectedCount) {
        boolean allSuccess = results.size() == expectedCount
                && results.values().stream().allMatch(LegResult::isSuccess);

        ArbStatus finalArbStatus = allSuccess ? ArbStatus.COMPLETED : ArbStatus.FAILED;
        arb.setStatus(finalArbStatus);
        arbitrageService.saveArbitrageOpportunity(arb);

        if (allSuccess) {
            log.info("Arb completed successfully | ArbId: {} | ArbStatus: {} | SuccessfulLegs: {}/{}",
                    arb.getId(), finalArbStatus, results.size(), expectedCount);
        } else {
            long failedCount = results.values().stream().filter(r -> !r.isSuccess()).count();
            log.error("Arb failed | ArbId: {} | ArbStatus: {} | FailedLegs: {} | SuccessfulLegs: {} | TotalLegs: {}",
                    arb.getId(), finalArbStatus, failedCount, results.size() - failedCount, expectedCount);
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void cleanupQueues() {
        log.debug("{} Starting scheduled queue cleanup", EMOJI_CLEANUP);

        int totalRemoved = 0;
        int arbsRemoved = cleanArbQueueIfNotEmpty();
        int legTasksRemoved = cleanNonEmptyWorkerQueues();
        totalRemoved = arbsRemoved + legTasksRemoved;

        if (totalRemoved > 0) {
            log.info("{} {} Cleanup completed | Removed: {} arbs, {} leg tasks (Total: {})",
                    EMOJI_CLEANUP, EMOJI_REMOVED, arbsRemoved, legTasksRemoved, totalRemoved);
        } else {
            log.trace("{} {} Cleanup skipped | All queues empty", EMOJI_CLEANUP, EMOJI_EMPTY);
        }
    }

    private int cleanArbQueueIfNotEmpty() {
        if (arbQueue.isEmpty()) {
            log.trace("{} {} arbQueue is empty, skipping cleanup", EMOJI_SKIP, EMOJI_EMPTY);
            return 0;
        }

        int removed = 0;
        ArbitrageOpportunity arb;

        while ((arb = arbQueue.poll()) != null) {
            removed++;
            log.debug("{} Removed ArbitrageOpportunity from queue | ArbId: {} | ExternalId: {}",
                    EMOJI_REMOVED, arb.getId(), arb.getExternalId());
        }

        log.info("{} {} Cleared arbQueue | Removed {} arb(s)", EMOJI_CLEANUP, EMOJI_QUEUE, removed);
        return removed;
    }

    private int cleanNonEmptyWorkerQueues() {
        int totalRemoved = 0;
        int skippedQueues = 0;
        int cleanedQueues = 0;

        for (Map.Entry<BookMaker, BlockingQueue<BetLegTask>> entry : workerQueues.entrySet()) {
            BookMaker bookmaker = entry.getKey();
            BlockingQueue<BetLegTask> queue = entry.getValue();

            if (queue.isEmpty()) {
                log.trace("{} {} {} queue is empty, skipping cleanup",
                        EMOJI_SKIP, EMOJI_EMPTY, bookmaker);
                skippedQueues++;
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
                log.debug("{} {} Cleared {} queue | Removed {} task(s)",
                        EMOJI_CLEANUP, EMOJI_QUEUE, bookmaker, removed);
                totalRemoved += removed;
                cleanedQueues++;
            }
        }

        if (skippedQueues > 0 || cleanedQueues > 0) {
            log.debug("{} Worker queues summary | Cleaned: {}, Skipped: {}, Total: {}",
                    EMOJI_INFO, cleanedQueues, skippedQueues, workerQueues.size());
        }

        return totalRemoved;
    }

    public QueueStats getQueueStats() {
        int arbQueueSize = arbQueue.size();
        int totalLegTasks = workerQueues.values().stream()
                .mapToInt(BlockingQueue::size)
                .sum();

        return new QueueStats(arbQueueSize, totalLegTasks, workerQueues.size());
    }

    public record QueueStats(int arbQueueSize, int totalLegTasks, int workerQueueCount) {
        @Override
        public String toString() {
            return String.format("QueueStats[arbs=%d, legTasks=%d, workers=%d]",
                    arbQueueSize, totalLegTasks, workerQueueCount);
        }
    }

    public void forceCleanup() {
        log.warn("{} Force cleanup triggered manually", EMOJI_CLEANUP);
        cleanupQueues();
    }

    public boolean isArbQueueEmpty() {
        return arbQueue.isEmpty();
    }

    private void randomHumanDelay(int minMs, int maxMs) throws InterruptedException {
        int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs + 1);
        log.debug("Human delay | DurationMs: {}", delay);
        Thread.sleep(delay);
    }
}