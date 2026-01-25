package com.mouse.bet.service;

import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.repository.ArbitrageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.mouse.bet.orchestrator.Orchestrator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Polls the database for fresh arbitrage opportunities and queues them to the orchestrator.
 * Filters opportunities based on configured bookmakers and enabled sports.
 * Uses ExecutorService for scheduled polling instead of @Scheduled annotation.
 *
 * Default allowed bookmakers: SPORTYBET, _1WIN, MSPORT
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
     * Default: "SPORTYBET,_1WIN,MSPORT"
     * Format: Comma-separated enum names, e.g. "SPORTYBET,_1WIN,MSPORT"
     */
    @Value("${arb.polling.bookmakers:SPORTYBET,_1WIN,MSPORT}")
    private String allowedBookmakersConfig;

    private Set<BookMaker> allowedBookmakers;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Sport-specific fetch enabled flags
    @Value("${fetch.enabled.football:false}")
    private boolean fetchFootballEnabled;

    @Value("${fetch.enabled.basketball:true}")
    private boolean fetchBasketballEnabled;

    @Value("${fetch.enabled.table-tennis:false}")
    private boolean fetchTableTennisEnabled;

    @Value("${fetch.enabled.tennis:false}")
    private boolean fetchTennisEnabled;

    @Value("${fetch.enabled.ice-hockey:false}")
    private boolean fetchIceHockeyEnabled;

    @Value("${fetch.enabled.volleyball:false}")
    private boolean fetchVolleyballEnabled;

    @Value("${fetch.enabled.handball:false}")
    private boolean fetchHandballEnabled;

    @Value("${fetch.enabled.baseball:false}")
    private boolean fetchBaseballEnabled;

    @Value("${fetch.enabled.american-football:false}")
    private boolean fetchAmericanFootballEnabled;

    @Value("${fetch.enabled.e-sports:false}")
    private boolean fetchEsportsEnabled;

    @Value("${fetch.enabled.cricket:false}")
    private boolean fetchCricketEnabled;

    private Map<Sport, Boolean> sportEnabledMap;

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

        // Initialize sport enabled map
        sportEnabledMap = new HashMap<>();
        sportEnabledMap.put(Sport.FOOTBALL, fetchFootballEnabled);
        sportEnabledMap.put(Sport.BASKETBALL, fetchBasketballEnabled);
        sportEnabledMap.put(Sport.TABLE_TENNIS, fetchTableTennisEnabled);
        sportEnabledMap.put(Sport.TENNIS, fetchTennisEnabled);
        sportEnabledMap.put(Sport.ICE_HOCKEY, fetchIceHockeyEnabled);
        sportEnabledMap.put(Sport.VOLLEYBALL, fetchVolleyballEnabled);
        sportEnabledMap.put(Sport.HANDBALL, fetchHandballEnabled);
        sportEnabledMap.put(Sport.BASEBALL, fetchBaseballEnabled);
        sportEnabledMap.put(Sport.AMERICAN_FOOTBALL, fetchAmericanFootballEnabled);
        sportEnabledMap.put(Sport.E_SPORTS, fetchEsportsEnabled);
        sportEnabledMap.put(Sport.CRICKET, fetchCricketEnabled);

        List<Sport> enabledSports = getEnabledSports();
        log.info("Sport filtering enabled | EnabledSports: {} | Total: {}/{}",
                enabledSports, enabledSports.size(), Sport.values().length);

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
            List<ArbitrageOpportunity> freshArbs = arbitrageRepository.findActiveArbsCreatedAfterWithMinProfit(cutoffTime, BigDecimal.valueOf(minProfitPercentage));

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
                    log.info("{} {} Arb queued | ArbId: {} | ExternalId: {} | Sport: {} | Profit: {}% | Bookmakers: {}",
                            EMOJI_QUEUED, EMOJI_FOUND, arb.getId(), arb.getExternalId(),
                            arb.getSport(), arb.getProfitPercentage(), getBookmakers(arb));

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

        if (arb.getCreatedAt().isBefore(LocalDateTime.now().minusSeconds(freshnessSeconds))){
            log.info("it not within the time frame");
            return false;

        }

        // Filter : Check sport is enabled
        if (!isSportEnabled(arb)) {
            Sport sport = parseSport(arb);
            log.debug("{} Filtered (sport disabled) | ArbId: {} | Sport: {} | EnabledSports: {}",
                    EMOJI_FILTERED, arb.getId(), sport != null ? sport : arb.getSport(), getEnabledSports());
            return false;
        }

        // Filter 3: Check bookmakers (if filtering is enabled)
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

        // Filter 4: Check if arb has valid outcomes
        if (arb.getOutcomes() == null || arb.getOutcomes().isEmpty()) {
            log.warn("{} Filtered (no outcomes) | ArbId: {}", EMOJI_FILTERED, arb.getId());
            return false;
        }

        // Filter 5: Verify we have registered workers for all bookmakers
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
     * Check if arbitrage opportunity's sport is enabled
     */
    private boolean isSportEnabled(ArbitrageOpportunity arb) {
        if (arb == null || arb.getSport() == null) {
            log.warn("Arbitrage or sport is null, filtering out | ArbId: {}", arb != null ? arb.getId() : "null");
            return false;
        }

        Sport sport = parseSport(arb);
        if (sport == null) {
            log.warn("Unknown sport '{}' for arbitrage {}, filtering out",
                    arb.getSport(), arb.getExternalId());
            return false;
        }

        return sportEnabledMap.getOrDefault(sport, false);
    }

    /**
     * Parse sport from arbitrage opportunity
     * Tries multiple approaches: sportId, display name, enum name
     */
    private Sport parseSport(ArbitrageOpportunity arb) {
        // First try using sportId if available
        if (arb.getSportId() != null) {
            Sport sport = Sport.fromBreakingBetId(arb.getSportId());
            if (sport != null) {
                return sport;
            }
        }

        // Fall back to parsing sport string
        String sportStr = arb.getSport();
        if (sportStr == null) {
            return null;
        }

        // Try by display name
        Sport sport = Sport.fromDisplayName(sportStr);
        if (sport != null) {
            return sport;
        }

        // Try by enum name (handle spaces and case)
        try {
            String normalizedName = sportStr.toUpperCase()
                    .replace(" ", "_")
                    .replace("-", "_");
            return Sport.valueOf(normalizedName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get list of enabled sports
     */
    private List<Sport> getEnabledSports() {
        return sportEnabledMap.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
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
     * Parse bookmaker names from config string.
     * Handles comma-separated enum names (e.g., "SPORTYBET,_1WIN,MSPORT")
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
                        log.error("Invalid bookmaker name in config: '{}' | Available values: {} | Error: {}",
                                name, getAvailableBookmakers(), e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Get a string of all available bookmaker enum names for logging
     */
    private String getAvailableBookmakers() {
        return java.util.Arrays.stream(BookMaker.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
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
                getEnabledSports(),
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
     * Update sport enabled status at runtime
     */
    public void updateSportEnabled(Sport sport, boolean enabled) {
        sportEnabledMap.put(sport, enabled);
        log.info("Updated sport enabled status | Sport: {} | Enabled: {}", sport, enabled);
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
            List<Sport> enabledSports,
            Orchestrator.QueueStats queueStats,
            boolean taskScheduled
    ) {
        @Override
        public String toString() {
            return String.format(
                    "PollingStatus[running=%s, enabled=%s, interval=%dms, bookmakers=%s, minProfit=%.2f%%, enabledSports=%s, queue=%s, scheduled=%s]",
                    running, enabled, intervalMs, allowedBookmakers, minProfitPercentage, enabledSports, queueStats, taskScheduled
            );
        }
    }
}