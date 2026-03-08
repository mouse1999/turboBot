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
 * OPTIMIZED VERSION: Retrieves all active arbs and filters in-memory for maximum speed.
 * Filters opportunities based on configured bookmakers and enabled sports.
 * Uses ExecutorService for scheduled polling instead of @Scheduled annotation.
 *
 * Default allowed bookmakers: SPORTYBET, BET9JA, MSPORT
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
    private static final String EMOJI_FAST = "⚡";

    private final ArbitrageRepository arbitrageRepository;
    private final Orchestrator orchestrator;

    @Value("${arb.polling.enabled:true}")
    private boolean pollingEnabled;

    @Value("${arb.polling.interval.ms:5000}")
    private long pollingIntervalMs;

    @Value("${arb.polling.initial.delay.ms:10000}")
    private long initialDelayMs;

    @Value("${arb.polling.freshness.seconds:10}")
    private int freshnessSeconds;

    @Value("${arb.polling.freshness.seconds:10}")
    private int  maxArbAgeSeconds;

    @Value("${arb.polling.min.profit:1.5}")
    private double minProfitPercentage;

    /**
     * Bookmakers to filter for. Only arbs with outcomes from these bookmakers will be processed.
     * Default: "SPORTYBET,BET9JA,MSPORT"
     * Format: Comma-separated enum names, e.g. "SPORTYBET,BET9JA,MSPORT"
     */
    @Value("${arb.polling.bookmakers:SPORTYBET,BET9JA,MSPORT}")
    private String allowedBookmakersConfig;

    /**
     * Use fast retrieval mode (fetch all active arbs and filter in memory)
     * Default: true for maximum speed
     */
    @Value("${arb.polling.fast.mode:true}")
    private boolean fastMode;

    /**
     * Maximum age in seconds for arbs to be considered (based on createdAt)
     * This prevents processing very old arbs that might still be marked as ACTIVE
     */
    @Value("${arb.polling.max.age.seconds:90}")
    private int maxAgeSeconds;

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

    // Performance tracking
    private volatile long lastPollDurationMs = 0;

    private volatile long lastRetrievalTimeMs = 0;
    private volatile long lastFilterTimeMs = 0;
    private volatile int lastArbsRetrieved = 0;

    @PostConstruct
    public void init() {
        // Parse allowed bookmakers from config
        if (allowedBookmakersConfig != null && !allowedBookmakersConfig.trim().isEmpty()) {
            allowedBookmakers = parseBookmakers(allowedBookmakersConfig);
            log.info("ArbPollingService initialized | PollingEnabled: {} | Interval: {}ms | FastMode: {} | AllowedBookmakers: {} | MinProfit: {}% | MaxAge: {}s",
                    pollingEnabled, pollingIntervalMs, fastMode, allowedBookmakers, minProfitPercentage, maxAgeSeconds);
        } else {
            allowedBookmakers = Set.of(); // Empty set means no filtering
            log.info("ArbPollingService initialized | PollingEnabled: {} | Interval: {}ms | FastMode: {} | BookmakerFilter: DISABLED | MinProfit: {}% | MaxAge: {}s",
                    pollingEnabled, pollingIntervalMs, fastMode, minProfitPercentage, maxAgeSeconds);
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
            log.info("Starting ArbPollingService | InitialDelay: {}ms | Interval: {}ms | FastMode: {}",
                    initialDelayMs, pollingIntervalMs, fastMode);

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
     * OPTIMIZED: Retrieves all arbs and filters in-memory for maximum speed
     */
    private void pollAndQueueArbitrage() {
        if (!running.get() || !pollingEnabled) {
            log.trace("Polling skipped | Running: {} | Enabled: {}", running.get(), pollingEnabled);
            return;
        }

        long pollStartTime = System.currentTimeMillis();

        try {
            log.debug("{} {} Polling for fresh arbitrage opportunities (Fast Mode: {})",
                    EMOJI_POLL, EMOJI_FAST, fastMode);

            List<ArbitrageOpportunity> allArbs;
            long retrievalStartTime = System.currentTimeMillis();

            if (fastMode) {
                // FAST MODE: Retrieve all active arbs by max age (faster query)
                allArbs = arbitrageRepository.findActiveArbsByMaxAge(maxAgeSeconds);
            } else {
                // LEGACY MODE: Use the original query with database filtering
                LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(freshnessSeconds);
                allArbs = arbitrageRepository.findActiveArbsCreatedAfterWithMinProfit(
                        cutoffTime,
                        BigDecimal.valueOf(minProfitPercentage)
                );
            }

            lastRetrievalTimeMs = System.currentTimeMillis() - retrievalStartTime;
            lastArbsRetrieved = allArbs.size();

            if (allArbs.isEmpty()) {
                log.trace("{} No arbitrage opportunities found", EMOJI_SKIPPED);
                lastPollDurationMs = System.currentTimeMillis() - pollStartTime;
                return;
            }

            log.info("{} {} Retrieved {} arbs from DB in {}ms | MaxAge: {}s",
                    EMOJI_POLL, EMOJI_FOUND, allArbs.size(), lastRetrievalTimeMs, maxAgeSeconds);

            // Apply in-memory filtering
            long filterStartTime = System.currentTimeMillis();
            List<ArbitrageOpportunity> filteredArbs = filterArbs(allArbs);
            lastFilterTimeMs = System.currentTimeMillis() - filterStartTime;

            if (filteredArbs.isEmpty()) {
                log.debug("{} All arbs filtered out | Retrieved: {} | FilterTime: {}ms",
                        EMOJI_FILTERED, allArbs.size(), lastFilterTimeMs);
                lastPollDurationMs = System.currentTimeMillis() - pollStartTime;
                return;
            }

            log.info("{} Filtering complete | Retrieved: {} | Passed: {} | Filtered: {} | FilterTime: {}ms",
                    EMOJI_FOUND, allArbs.size(), filteredArbs.size(),
                    allArbs.size() - filteredArbs.size(), lastFilterTimeMs);

            // Sort by profit percentage (highest first)
            filteredArbs.sort((a1, a2) -> {
                BigDecimal p1 = a1.getProfitPercentage();
                BigDecimal p2 = a2.getProfitPercentage();
                if (p1 == null && p2 == null) return 0;
                if (p1 == null) return 1;
                if (p2 == null) return -1;
                return p2.compareTo(p1); // Descending order
            });

            // Try to queue arbs (process highest profit first)
            int queued = 0;
            int skipped = 0;

            for (ArbitrageOpportunity arb : filteredArbs) {
                // Try to queue (non-blocking)
                boolean success = orchestrator.tryLoadArb(arb);

                if (success) {
                    queued++;
                    log.info("{} {} Arb queued | ArbId: {} | ExternalId: {} | Sport: {} | Profit: {}% | Age: {}s | Bookmakers: {}",
                            EMOJI_QUEUED, EMOJI_FOUND, arb.getId(), arb.getExternalId(),
                            arb.getSport(), arb.getProfitPercentage(),
                            getAgeInSeconds(arb), getBookmakers(arb));

                    // Mark as in progress to avoid re-processing
//                    arb.setStatus(ArbStatus.IN_PROGRESS);
//                    arbitrageRepository.save(arb);

                    // Only queue one arb per poll cycle (single-slot orchestrator)
                    break;

                } else {
                    skipped++;
                    log.debug("{} Arb skipped (queue full) | ArbId: {} | ExternalId: {} | Profit: {}%",
                            EMOJI_SKIPPED, arb.getId(), arb.getExternalId(), arb.getProfitPercentage());
                }
            }

            lastPollDurationMs = System.currentTimeMillis() - pollStartTime;

            log.info("{} Polling cycle complete | Retrieved: {} | Passed: {} | Queued: {} | Skipped: {} | TotalTime: {}ms | RetrievalTime: {}ms | FilterTime: {}ms | QueueStats: {}",
                    EMOJI_FAST, allArbs.size(), filteredArbs.size(), queued, skipped,
                    lastPollDurationMs, lastRetrievalTimeMs, lastFilterTimeMs,
                    orchestrator.getQueueStats());

        } catch (Exception e) {
            lastPollDurationMs = System.currentTimeMillis() - pollStartTime;
            log.error("{} Error during polling cycle | Duration: {}ms | Error: {}",
                    EMOJI_ERROR, lastPollDurationMs, e.getMessage(), e);
        }
    }

    /**
     * Apply all filters to a list of arbs in-memory
     * OPTIMIZED: Filters in a single pass with early exits
     */
    private List<ArbitrageOpportunity> filterArbs(List<ArbitrageOpportunity> arbs) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime freshnessCutoff = now.minusSeconds(freshnessSeconds);
        Set<BookMaker> registeredWorkers = orchestrator.getRegisteredWorkers();

        return arbs.stream()
                .filter(arb -> {
                    // Filter 1: Must have profit percentage
                    if (arb.getProfitPercentage() == null) {
                        log.trace("{} Filtered (null profit) | ArbId: {}", EMOJI_FILTERED, arb.getExternalId());
                        return false;
                    }

                    // Filter 2: Minimum profit percentage
                    if (arb.getProfitPercentage().doubleValue() < minProfitPercentage) {
                        log.trace("{} Filtered (low profit) | ArbId: {} | Profit: {}% | MinRequired: {}%",
                                EMOJI_FILTERED, arb.getExternalId(), arb.getProfitPercentage(), minProfitPercentage);
                        return false;
                    }

                    // Filter 3: Freshness (updatedAt within threshold)
                    if (arb.getUpdatedAt() == null || arb.getUpdatedAt().isBefore(freshnessCutoff)) {
                        log.trace("{} Filtered (stale) | ArbId: {} | Updated: {} | Required: after {}",
                                EMOJI_FILTERED, arb.getExternalId(), arb.getUpdatedAt(), freshnessCutoff);
                        return false;
                    }

                    if (arb.getArbAgeSeconds() == null || arb.getArbAgeSeconds() > maxArbAgeSeconds) {
                        log.trace("{} Filtered (too old) | ArbId: {} | Age: {}s | Max allowed: {}s",
                                EMOJI_FILTERED, arb.getExternalId(), arb.getArbAgeSeconds(), maxArbAgeSeconds);
                        return false;
                    }


                    // Filter 4: Sport must be enabled
                    if (!isSportEnabled(arb)) {
                        Sport sport = parseSport(arb);
                        log.trace("{} Filtered (sport disabled) | ArbId: {} | Sport: {}",
                                EMOJI_FILTERED, arb.getExternalId(), sport != null ? sport : arb.getSport());
                        return false;
                    }

                    // Filter 5: Must have exactly 2 outcomes
                    if (arb.getOutcomes() == null || arb.getOutcomes().size() != 2) {
                        log.trace("{} Filtered (invalid outcome count) | ArbId: {} | Count: {}",
                                EMOJI_FILTERED, arb.getExternalId(),
                                arb.getOutcomes() != null ? arb.getOutcomes().size() : 0);
                        return false;
                    }

                    // Filter 6: Both outcomes must have valid outcome names
                    List<ArbOutcome> outcomes = arb.getOutcomes();
                    if (!hasValidOutcomeName(outcomes.get(0)) || !hasValidOutcomeName(outcomes.get(1))) {
                        log.trace("{} Filtered (invalid outcome name) | ArbId: {} | Outcome1: '{}' | Outcome2: '{}'",
                                EMOJI_FILTERED, arb.getExternalId(),
                                outcomes.get(0).getOutComeName(), outcomes.get(1).getOutComeName());
                        return false;
                    }

                    // Get bookmakers for next filters
                    Set<BookMaker> arbBookmakers = getBookmakers(arb);

                    // Filter 7: Must contain BET9JA (temporary requirement)
//                    if (!arbBookmakers.contains(BookMaker.BET9JA)) {
//                        log.trace("{} Filtered (missing BET9JA) | ArbId: {} | Bookmakers: {}",
//                                EMOJI_FILTERED, arb.getExternalId(), arbBookmakers);
//                        return false;
//                    }

                    // Filter 8: Bookmakers filter (if configured)
                    if (!allowedBookmakers.isEmpty()) {
                        boolean allAllowed = arbBookmakers.stream().allMatch(allowedBookmakers::contains);
                        if (!allAllowed) {
                            log.trace("{} Filtered (bookmaker mismatch) | ArbId: {} | ArbBookmakers: {} | Allowed: {}",
                                    EMOJI_FILTERED, arb.getExternalId(), arbBookmakers, allowedBookmakers);
                            return false;
                        }
                    }

                    // Filter 9: All required workers must be registered
                    if (!registeredWorkers.containsAll(arbBookmakers)) {
                        Set<BookMaker> missing = arbBookmakers.stream()
                                .filter(bm -> !registeredWorkers.contains(bm))
                                .collect(Collectors.toSet());
                        log.trace("{} Filtered (missing workers) | ArbId: {} | Missing: {}",
                                EMOJI_FILTERED, arb.getExternalId(), missing);
                        return false;
                    }

                    // All filters passed
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * DEPRECATED: Old filter method (kept for reference)
     * Use filterArbs() instead for better performance
     */
    @Deprecated
    private boolean passesFilters(ArbitrageOpportunity arb) {
        // This method is deprecated - use filterArbs() for batch filtering
        throw new UnsupportedOperationException("Use filterArbs() for batch filtering");
    }

    private boolean hasValidOutcomeName(ArbOutcome outcome) {
        if (outcome == null) {
            return false;
        }

        String name = outcome.getOutComeName();

        if (name == null) {
            return false;
        }

        String trimmed = name.trim();

        if (trimmed.isEmpty()) {
            return false;
        }

        String lower = trimmed.toLowerCase();

        // Explicitly block common invalid values
        return !lower.equals("n/a") &&
                !lower.equals("unknown") &&
                !lower.equals("none") &&
                !lower.equals("null");
    }

    /**
     * Check if arbitrage opportunity's sport is enabled
     */
    private boolean isSportEnabled(ArbitrageOpportunity arb) {
        if (arb == null || arb.getSport() == null) {
            return false;
        }

        Sport sport = parseSport(arb);
        if (sport == null) {
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
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Calculate age of arb in seconds (based on createdAt)
     */
    private long getAgeInSeconds(ArbitrageOpportunity arb) {
        if (arb.getCreatedAt() == null) {
            return -1;
        }
        return java.time.Duration.between(arb.getCreatedAt(), LocalDateTime.now()).getSeconds();
    }

    /**
     * Parse bookmaker names from config string.
     * Handles comma-separated enum names (e.g., "SPORTYBET,BET9JA,MSPORT")
     */
    private Set<BookMaker> parseBookmakers(String config) {
        return Arrays.stream(config.split(","))
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
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Get a string of all available bookmaker enum names for logging
     */
    private String getAvailableBookmakers() {
        return Arrays.stream(BookMaker.values())
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
                pollingTask != null && !pollingTask.isCancelled(),
                fastMode,
                maxAgeSeconds,
                lastPollDurationMs,
                lastRetrievalTimeMs,
                lastFilterTimeMs,
                lastArbsRetrieved
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
     * Toggle fast mode at runtime
     */
    public void setFastMode(boolean enabled) {
        this.fastMode = enabled;
        log.info("Fast mode {} | Enabled: {}", enabled ? "enabled" : "disabled", enabled);
    }

    /**
     * Update max age seconds at runtime
     */
    public void updateMaxAgeSeconds(int seconds) {
        this.maxAgeSeconds = seconds;
        log.info("Updated max age | New: {}s", maxAgeSeconds);
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
            boolean taskScheduled,
            boolean fastMode,
            int maxAgeSeconds,
            long lastPollDurationMs,
            long lastRetrievalTimeMs,
            long lastFilterTimeMs,
            int lastArbsRetrieved
    ) {
        @Override
        public String toString() {
            return String.format(
                    "PollingStatus[running=%s, enabled=%s, interval=%dms, bookmakers=%s, minProfit=%.2f%%, " +
                            "enabledSports=%s, queue=%s, scheduled=%s, fastMode=%s, maxAge=%ds, " +
                            "lastPoll=%dms, lastRetrieval=%dms, lastFilter=%dms, lastRetrieved=%d]",
                    running, enabled, intervalMs, allowedBookmakers, minProfitPercentage, enabledSports,
                    queueStats, taskScheduled, fastMode, maxAgeSeconds,
                    lastPollDurationMs, lastRetrievalTimeMs, lastFilterTimeMs, lastArbsRetrieved
            );
        }
    }
}