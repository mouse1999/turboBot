package com.mouse.bet.service;

import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.repository.ArbitrageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.mouse.bet.orchestrator.Orchestrator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Polls the database for fresh arbitrage opportunities and queues them to the orchestrator.
 * Filters opportunities based on configured bookmakers.
 * Uses ExecutorService for scheduled polling instead of @Scheduled annotation.
 */
@Slf4j
@Data
@Service
@RequiredArgsConstructor
public class ArbPollingService {

    private static final String EMOJI_POLL = "🔍";
    private static final String EMOJI_FOUND = "✅";
    private static final String EMOJI_FILTERED = "🔽";
    private static final String EMOJI_QUEUED = "📥";
    private static final String EMOJI_SKIPPED = "⏭️";
    private static final String EMOJI_ERROR = "❌";

    private final ArbitrageRepository arbitrageRepository;
    private final Orchestrator orchestrator;

    @Value("${arb.polling.enabled:true}")
    private boolean pollingEnabled;

    @Value("${arb.polling.interval.ms:5000}")
    private long pollingIntervalMs;

    @Value("${arb.polling.initial.delay.ms:10000}")
    private long initialDelayMs;

    @Value("${arb.polling.freshness.seconds:2}")
    private int freshnessSeconds;

    @Value("${arb.polling.min.profit:5}")
    private double minProfitPercentage;

    /**
     * Bookmakers to filter for. Only arbs with outcomes from these bookmakers will be processed.
     * Format: "BET365,BETWAY,SPORTYBET"
     */
    @Value("${arb.polling.bookmakers:}")
    private String allowedBookmakersConfig;

    private Set<BookMaker> allowedBookmakers;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Scheduled executor for polling task */
    private ScheduledExecutorService pollingExecutor;
    private ScheduledFuture<?> pollingTask;

    @PostConstruct
    public void init() {
        // Parse allowed bookmakers from config
        if (allowedBookmakersConfig != null && !allowedBookmakersConfig.trim().isEmpty()) {
            allowedBookmakers = parseBookmakers(allowedBookmakersConfig);
            log.info("ArbPollingService initialized | PollingEnabled: {} | Interval: {}ms | AllowedBookmakers: {} | MinProfit: {}%",
                    pollingEnabled, pollingIntervalMs, allowedBookmakers, minProfitPercentage);
        } else {
            allowedBookmakers = Set.of(); // Empty set means no filtering
            log.info("ArbPollingService initialized | PollingEnabled: {} | Interval: {}ms | BookmakerFilter: DISABLED | MinProfit: {}%",
                    pollingEnabled, pollingIntervalMs, minProfitPercentage);
        }

        // Create executor service for polling
        pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "arb-polling-service");
            t.setDaemon(true);
            return t;
        });

        if (pollingEnabled) {
            start();
        }
    }

    @PreDestroy
    public void shutdown() {
        stop();

        if (pollingExecutor != null) {
            log.info("Shutting down polling executor");
            pollingExecutor.shutdownNow();

            try {
                if (!pollingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Polling executor did not terminate within timeout");
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for polling executor shutdown", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Start the polling service
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting ArbPollingService | InitialDelay: {}ms | Interval: {}ms",
                    initialDelayMs, pollingIntervalMs);

            // Schedule polling task with fixed delay
            pollingTask = pollingExecutor.scheduleWithFixedDelay(
                    this::pollAndQueueArbitrage,
                    initialDelayMs,
                    pollingIntervalMs,
                    TimeUnit.MILLISECONDS
            );

            log.info("ArbPollingService started successfully");
        } else {
            log.debug("ArbPollingService already running");
        }
    }

    /**
     * Stop the polling service
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping ArbPollingService");

            if (pollingTask != null && !pollingTask.isCancelled()) {
                pollingTask.cancel(false);
                log.info("Polling task cancelled");
            }

            log.info("ArbPollingService stopped");
        }
    }

    /**
     * Scheduled polling task - runs at configured interval
     */
    private void pollAndQueueArbitrage() {
        if (!running.get() || !pollingEnabled) {
            log.trace("Polling skipped | Running: {} | Enabled: {}", running.get(), pollingEnabled);
            return;
        }

        try {
            log.debug("{} Polling for fresh arbitrage opportunities", EMOJI_POLL);

            // Calculate cutoff time for freshness
            LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(freshnessSeconds);

            // Fetch fresh active arbs from database
            List<ArbitrageOpportunity> freshArbs = arbitrageRepository.findFreshActiveArbs(cutoffTime);

            if (freshArbs.isEmpty()) {
                log.trace("{} No fresh arbitrage opportunities found", EMOJI_SKIPPED);
                return;
            }

            log.info("{} {} Found fresh arbs | Count: {} | Cutoff: {}",
                    EMOJI_POLL, EMOJI_FOUND, freshArbs.size(), cutoffTime);

            // Filter and queue arbs
            int queued = 0;
            int filtered = 0;
            int skipped = 0;

            for (ArbitrageOpportunity arb : freshArbs) {
                // Apply filters
                if (!passesFilters(arb)) {
                    filtered++;
                    continue;
                }

                // Try to queue (non-blocking)
                boolean success = orchestrator.tryLoadArb(arb);

                if (success) {
                    queued++;
                    log.info("{} {} Arb queued | ArbId: {} | ExternalId: {} | Profit: {}% | Bookmakers: {}",
                            EMOJI_QUEUED, EMOJI_FOUND, arb.getId(), arb.getExternalId(),
                            arb.getProfitPercentage(), getBookmakers(arb));

                    // Mark as in progress to avoid re-processing
                    arb.setStatus(ArbStatus.IN_PROGRESS);
                    arbitrageRepository.save(arb);

                    // Only queue one arb per poll cycle (single-slot orchestrator)
                    break;

                } else {
                    skipped++;
                    log.debug("{} Arb skipped (queue full) | ArbId: {} | ExternalId: {}",
                            EMOJI_SKIPPED, arb.getId(), arb.getExternalId());
                }
            }

            if (queued > 0 || filtered > 0) {
                log.info("Polling cycle complete | Found: {} | Filtered: {} | Queued: {} | Skipped: {} | QueueStats: {}",
                        freshArbs.size(), filtered, queued, skipped, orchestrator.getQueueStats());
            }

        } catch (Exception e) {
            log.error("{} Error during polling cycle | Error: {}",
                    EMOJI_ERROR, e.getMessage(), e);
        }
    }

    /**
     * Apply filters to determine if arb should be processed
     */
    private boolean passesFilters(ArbitrageOpportunity arb) {
        // Filter 1: Check profit percentage
        if (arb.getProfitPercentage().doubleValue() < minProfitPercentage) {
            log.debug("{} Filtered (low profit) | ArbId: {} | Profit: {}% | MinRequired: {}%",
                    EMOJI_FILTERED, arb.getId(), arb.getProfitPercentage(), minProfitPercentage);
            return false;
        }

        // Filter 2: Check bookmakers (if filtering is enabled)
        if (!allowedBookmakers.isEmpty()) {
            Set<BookMaker> arbBookmakers = getBookmakers(arb);

            // Check if ALL outcomes are from allowed bookmakers
            boolean allAllowed = arbBookmakers.stream()
                    .allMatch(allowedBookmakers::contains);

            if (!allAllowed) {
                log.debug("{} Filtered (bookmaker mismatch) | ArbId: {} | ArbBookmakers: {} | AllowedBookmakers: {}",
                        EMOJI_FILTERED, arb.getId(), arbBookmakers, allowedBookmakers);
                return false;
            }
        }

        // Filter 3: Check if arb has valid outcomes
        if (arb.getOutcomes() == null || arb.getOutcomes().isEmpty()) {
            log.warn("{} Filtered (no outcomes) | ArbId: {}", EMOJI_FILTERED, arb.getId());
            return false;
        }

        // Filter 4: Verify we have registered workers for all bookmakers
        Set<BookMaker> arbBookmakers = getBookmakers(arb);
        Set<BookMaker> registeredWorkers = orchestrator.getRegisteredWorkers();

        boolean allWorkersAvailable = registeredWorkers.containsAll(arbBookmakers);

        if (!allWorkersAvailable) {
            Set<BookMaker> missingWorkers = arbBookmakers.stream()
                    .filter(bm -> !registeredWorkers.contains(bm))
                    .collect(Collectors.toSet());

            log.debug("{} Filtered (missing workers) | ArbId: {} | MissingWorkers: {} | RegisteredWorkers: {}",
                    EMOJI_FILTERED, arb.getId(), missingWorkers, registeredWorkers);
            return false;
        }

        return true;
    }

    /**
     * Extract unique bookmakers from arb outcomes
     */
    private Set<BookMaker> getBookmakers(ArbitrageOpportunity arb) {
        if (arb.getOutcomes() == null) {
            return Set.of();
        }

        return arb.getOutcomes().stream()
                .map(ArbOutcome::getBookmakerName)
                .collect(Collectors.toSet());
    }

    /**
     * Parse bookmaker names from config string
     */
    private Set<BookMaker> parseBookmakers(String config) {
        return Set.of(config.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(name -> {
                    try {
                        return BookMaker.valueOf(name.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid bookmaker name in config: {} | Error: {}", name, e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Manual trigger for polling (useful for testing or admin actions)
     */
    public void triggerPoll() {
        log.info("Manual poll triggered");
        pollAndQueueArbitrage();
    }

    /**
     * Get polling status for monitoring
     */
    public PollingStatus getStatus() {
        return new PollingStatus(
                running.get(),
                pollingEnabled,
                pollingIntervalMs,
                allowedBookmakers,
                minProfitPercentage,
                orchestrator.getQueueStats(),
                pollingTask != null && !pollingTask.isCancelled()
        );
    }

    /**
     * Update allowed bookmakers at runtime
     */
    public void updateAllowedBookmakers(Set<BookMaker> bookmakers) {
        this.allowedBookmakers = Set.copyOf(bookmakers);
        log.info("Updated allowed bookmakers | New: {}", allowedBookmakers);
    }

    /**
     * Update minimum profit percentage at runtime
     */
    public void updateMinProfitPercentage(double minProfit) {
        this.minProfitPercentage = minProfit;
        log.info("Updated minimum profit percentage | New: {}%", minProfitPercentage);
    }

    /**
     * Restart polling with new configuration
     */
    public void restart() {
        log.info("Restarting ArbPollingService");
        stop();
        try {
            Thread.sleep(1000); // Brief pause before restart
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        start();
    }

    public record PollingStatus(
            boolean running,
            boolean enabled,
            long intervalMs,
            Set<BookMaker> allowedBookmakers,
            double minProfitPercentage,
            Orchestrator.QueueStats queueStats,
            boolean taskScheduled
    ) {
        @Override
        public String toString() {
            return String.format(
                    "PollingStatus[running=%s, enabled=%s, interval=%dms, bookmakers=%s, minProfit=%.2f%%, queue=%s, scheduled=%s]",
                    running, enabled, intervalMs, allowedBookmakers, minProfitPercentage, queueStats, taskScheduled
            );
        }
    }
}