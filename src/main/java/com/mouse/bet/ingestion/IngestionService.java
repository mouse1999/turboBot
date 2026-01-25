package com.mouse.bet.ingestion;

import com.mouse.bet.client.BreakingBetClient;
import com.mouse.bet.dto.*;
import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.mapper.MSportBetMapper;
import com.mouse.bet.mapper.OneWinMapper;
import com.mouse.bet.mapper.OppositeOutcomeMapper;
import com.mouse.bet.mapper.SportyBetMapper;
import com.mouse.bet.mapper.model.MarketOutcome;
import com.mouse.bet.monitoring.ArbitrageDataValidator;
import com.mouse.bet.repository.ArbOutcomeRepository;
import com.mouse.bet.repository.ArbitrageRepository;
import com.mouse.bet.service.ArbitrageService;
import com.mouse.bet.transformation.BookMakerMapper;
import com.mouse.bet.util.ArbCalculator;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Refactored IngestionService:
 * - Each Event → ParsedArbitrageData
 * - Each SubEvent → Outcome
 * - Items provide odds values via sub_event_id matching
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final String EMOJI_INGESTION = "📥";
    private static final String EMOJI_TRANSFORM = "🔄";
    private static final String EMOJI_SAVE = "💾";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_INFO = "ℹ️";
    private static final String EMOJI_STATS = "📊";
    private static final String EMOJI_CONCURRENT = "⚡";
    private static final String EMOJI_STARTUP = "🚀";
    private static final String EMOJI_POLLING = "🔄";
    private static final String EMOJI_CRUMBS = "🍪";
    private static final String EMOJI_MARKET = "🎯";
    private static final BigDecimal TOTAL_STAKE = BigDecimal.valueOf(500);


    List<BookMaker> PREFERRED_BOOKMAKERS = Arrays.asList(
            BookMaker.SPORTYBET,  // First priority
            BookMaker.MSPORT // Second priority
    );


    private final BreakingBetClient breakingBetClient;
    private final ArbitrageService arbitrageService;
    private final ArbitrageDataValidator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ExecutorService processingExecutor;
    private ScheduledExecutorService pollingScheduler;
    private ScheduledFuture<?> pollingTask;

    private volatile boolean isProcessing = false;
    private volatile boolean isRunning = false;
    private SportyBetMapper sportyBetMapper;
    private MSportBetMapper mSportBetMapper;
    private OneWinMapper oneWinMapper;
    @Getter
    private int pollCount = 0;

    @PostConstruct
    public void init() {
        log.info("{} {} Initializing IngestionService...", EMOJI_STARTUP, EMOJI_INFO);

        processingExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("arb-processor-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );

        pollingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("arb-poller");
            t.setDaemon(true);
            return t;
        });

        log.info("{} {} Thread pool created with {} threads",
                EMOJI_SUCCESS, EMOJI_CONCURRENT, Runtime.getRuntime().availableProcessors());
        log.info("{} {} Polling scheduler initialized", EMOJI_SUCCESS, EMOJI_POLLING);

        log.info("");
        sportyBetMapper = new SportyBetMapper();
        mSportBetMapper = new MSportBetMapper();
        oneWinMapper = new OneWinMapper();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("{} {} Application ready - starting polling...", EMOJI_STARTUP, EMOJI_INFO);
        startPolling();
    }

    public void startPolling() {
        if (isRunning) {
            log.warn("{} {} Polling already running", EMOJI_WARNING, EMOJI_POLLING);
            return;
        }

        isRunning = true;
        pollCount = 0;

        log.info("{} {} {} Starting live arbs polling every 2 seconds...",
                EMOJI_STARTUP, EMOJI_POLLING, EMOJI_INFO);

        pollingTask = pollingScheduler.scheduleAtFixedRate(
                this::pollLiveArbs,
                0,
                2,
                TimeUnit.SECONDS
        );

        log.info("{} {} Polling started successfully", EMOJI_SUCCESS, EMOJI_POLLING);
    }

    public void stopPolling() {
        if (!isRunning) {
            log.warn("{} {} Polling not running", EMOJI_WARNING, EMOJI_POLLING);
            return;
        }

        log.info("{} {} Stopping polling...", EMOJI_INFO, EMOJI_POLLING);
        isRunning = false;

        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(false);
            log.info("{} {} Polling stopped after {} polls", EMOJI_SUCCESS, EMOJI_POLLING, pollCount);
        }
    }

    private void pollLiveArbs() {
        if (!isRunning) {
            return;
        }

        pollCount++;
        log.info("{} {} [Poll #{}] Starting live arbs fetch...", EMOJI_POLLING, EMOJI_INGESTION, pollCount);

        if (isProcessing) {
            log.debug("{} {} [Poll #{}] Previous ingestion still running, skipping",
                    EMOJI_WARNING, EMOJI_POLLING, pollCount);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                isProcessing = true;
                executeIngestion(true);
            } catch (Exception e) {
                log.error("{} {} [Poll #{}] Polling failed: {}",
                        EMOJI_ERROR, EMOJI_POLLING, pollCount, e.getMessage(), e);
            } finally {
                isProcessing = false;
            }
        }, processingExecutor);
    }

    @PreDestroy
    public void shutdown() {
        log.info("{} {} Shutting down IngestionService...", EMOJI_INFO, EMOJI_SAVE);
        stopPolling();

        if (pollingScheduler != null) {
            pollingScheduler.shutdown();
            try {
                if (!pollingScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    pollingScheduler.shutdownNow();
                    log.warn("{} {} Forced polling scheduler shutdown", EMOJI_WARNING, EMOJI_SAVE);
                }
            } catch (InterruptedException e) {
                pollingScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (processingExecutor != null) {
            processingExecutor.shutdown();
            try {
                if (!processingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    processingExecutor.shutdownNow();
                    log.warn("{} {} Forced executor shutdown", EMOJI_WARNING, EMOJI_SAVE);
                }
            } catch (InterruptedException e) {
                processingExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("{} {} IngestionService shutdown complete. Total polls: {}",
                EMOJI_SUCCESS, EMOJI_SAVE, pollCount);
    }

    private void executeIngestion(boolean isLive) {
        try {
            long startTime = System.currentTimeMillis();

            log.debug("{} Fetching {} arbs from API...", EMOJI_INGESTION, isLive ? "live" : "prematch");
            BreakingBetResponse response = isLive
                    ? breakingBetClient.fetchLiveArbsAsObject()
                    : breakingBetClient.fetchPrematchArbsAsObject();

            log.debug("{} {} API returned {} events", EMOJI_SUCCESS, EMOJI_INGESTION,
                    response.getEvents() != null ? response.getEvents().size() : 0);

            List<ParsedArbitrageData> parsedData = enrichEventsToArbitrages(response, isLive);

            log.debug("{} {} Transforming {} parsed items to entities...", EMOJI_TRANSFORM, EMOJI_INFO, parsedData.size());
            List<ArbitrageOpportunity> opportunities = transformToEntitiesConcurrent(parsedData);

            log.debug("{} {} Saving {} opportunities to database...", EMOJI_SAVE, EMOJI_INFO, opportunities.size());
            int saved = saveArbitrageOpportunitiesSequential(opportunities); // Changed here

            long duration = System.currentTimeMillis() - startTime;
            log.info("{} {} {} arbs ingestion completed: {} opportunities saved in {}ms",
                    EMOJI_SUCCESS, EMOJI_INGESTION, isLive ? "Live" : "Prematch", saved, duration);

            if (isLive && pollCount % 30 == 0) {
                logStatistics();
            }
        } catch (IOException e) {
            log.error("{} {} Failed to fetch {} arbs: {}", EMOJI_ERROR, EMOJI_INGESTION,
                    isLive ? "live" : "prematch", e.getMessage(), e);
        } catch (Exception e) {
            log.error("{} {} Unexpected error during {} arbs ingestion: {}", EMOJI_ERROR, EMOJI_INGESTION,
                    isLive ? "live" : "prematch", e.getMessage(), e);
        }
    }

    @Transactional
    public int ingestLiveArbsManual() throws IOException {
        log.info("{} {} Manual live arbs ingestion triggered", EMOJI_INGESTION, EMOJI_INFO);
        BreakingBetResponse response = breakingBetClient.fetchLiveArbsAsObject();
        List<ParsedArbitrageData> parsedData = enrichEventsToArbitrages(response, true);
        List<ArbitrageOpportunity> opportunities = transformToEntitiesConcurrent(parsedData);
        return saveArbitrageOpportunitiesSequential(opportunities);
    }

    @Transactional
    public int ingestPrematchArbsManual() throws IOException {
        log.info("{} {} Manual prematch arbs ingestion triggered", EMOJI_INGESTION, EMOJI_INFO);
        BreakingBetResponse response = breakingBetClient.fetchPrematchArbsAsObject();
        List<ParsedArbitrageData> parsedData = enrichEventsToArbitrages(response, false);
        List<ArbitrageOpportunity> opportunities = transformToEntitiesConcurrent(parsedData);
        return saveArbitrageOpportunitiesSequential(opportunities);
    }

    // === NEW APPROACH: Event → ArbitrageData, SubEvents → Outcomes ===

    /**
     * Convert Events to Arbitrages:
     * 1. Each Event becomes a ParsedArbitrageData
     * 2. Each SubEvent becomes an Outcome
     * 3. Items provide odds values and profit info
     */
    private List<ParsedArbitrageData> enrichEventsToArbitrages(BreakingBetResponse response, boolean isLive) {
        log.debug("{} {} {} Processing {} events...", EMOJI_TRANSFORM, EMOJI_CONCURRENT, EMOJI_INFO,
                response.getEvents() != null ? response.getEvents().size() : 0);

        ArbitrageDataValidator.ValidationResult validationResult = validator.validateResponse(response);
        if (!validationResult.isValid()) {
            log.error("{} {} Response validation failed: {}", EMOJI_ERROR, EMOJI_TRANSFORM, validationResult.getSummary());
        } else if (validationResult.hasWarnings()) {
            log.warn("{} {} Response has warnings: {}", EMOJI_WARNING, EMOJI_TRANSFORM, validationResult.getSummary());
        }

        if (response.getEvents() == null || response.getEvents().isEmpty()) {
            log.warn("{} {} No events in response", EMOJI_WARNING, EMOJI_TRANSFORM);
            return Collections.emptyList();
        }

        // Build lookup map: sub_event_id → Odd (for odds values)
        Map<String, Odd> oddsMap = buildOddsMap(response.getItems());

        // Build lookup map: event_id → ArbItem (for profit info)
        Map<String, ArbItem> itemMap = buildItemMap(response.getItems());

        // Process each event concurrently
        List<CompletableFuture<ParsedArbitrageData>> futures = response.getEvents().stream()
                .map(event -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return convertEventToArbitrage(event, itemMap.get(event.getId()), oddsMap, isLive);
                    } catch (Exception e) {
                        log.warn("{} {} Failed to convert event {}: {}",
                                EMOJI_WARNING, EMOJI_TRANSFORM, event.getId(), e.getMessage());
                        return null;
                    }
                }, processingExecutor))
                .toList();

        List<ParsedArbitrageData> enrichedData = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.debug("{} {} {} Successfully converted {} events to arbitrages",
                EMOJI_SUCCESS, EMOJI_CONCURRENT, EMOJI_TRANSFORM, enrichedData.size());
        return enrichedData;
    }

    /**
     * Build map: sub_event_id → Odd
     */
    private Map<String, Odd> buildOddsMap(List<ArbItem> items) {
        if (items == null) return Collections.emptyMap();

        Map<String, Odd> map = new HashMap<>();
        for (ArbItem item : items) {
            if (item.getOdds() != null) {
                for (Odd odd : item.getOdds()) {
                    map.put(odd.getSubEventId(), odd);
                }
            }
        }
        return map;
    }

    /**
     * Build map: event_id → ArbItem (for profit info)
     */
    private Map<String, ArbItem> buildItemMap(List<ArbItem> items) {
        if (items == null) return Collections.emptyMap();

        return items.stream()
                .collect(Collectors.toMap(ArbItem::getEventId, item -> item, (a, b) -> a));
    }

    /**
     * Convert single Event to ParsedArbitrageData
     * SubEvents become Outcomes
     *
     * LOGIC: Save crumbs and oid from first non-1WIN sub-event,
     * then use them for 1WIN sub-event to get opposite outcome
     */
    /**
     * Convert single Event to ParsedArbitrageData
     * SubEvents become Outcomes
     *
     * LOGIC: Save crumbs and oid from first non-1WIN sub-event,
     * then use them for 1WIN sub-event to get opposite outcome
     */
    private ParsedArbitrageData convertEventToArbitrage(Event event,
                                                        ArbItem item,
                                                        Map<String, Odd> oddsMap,
                                                        boolean isLive) {
        log.trace("{} {} Converting event {} to arbitrage...", EMOJI_TRANSFORM, EMOJI_INFO, event.getId());

        // Validate: Only 2-way arbs
        if (event.getSubEvents() == null || event.getSubEvents().size() != 2) {
            log.debug("{} {} Ignoring event {} - has {} sub-events (only accepting 2-way)",
                    EMOJI_INFO, EMOJI_TRANSFORM, event.getId(),
                    event.getSubEvents() != null ? event.getSubEvents().size() : 0);
            return null;
        }

        log.trace("{} {} Event {} has 2 sub-events, proceeding with conversion",
                EMOJI_SUCCESS, EMOJI_TRANSFORM, event.getId());

        // Extract profit info
        ArbitrageProfitInfo profitInfo = ArbitrageProfitInfo.from(item, event);

        // Sort sub-events by bookmaker priority
        List<SubEvent> sortedSubEvents = getSortedSubEvents(event);

        log.trace("{} {} Processing {} sub-events for event {}...",
                EMOJI_TRANSFORM, EMOJI_INFO, sortedSubEvents.size(), event.getId());

        // Process sub-events using streams
        CrumbsHolder crumbsHolder = new CrumbsHolder();
        MarketInfoHolder marketHolder = new MarketInfoHolder();

        List<OutcomeData> outcomes = IntStream.range(0, sortedSubEvents.size())
                .mapToObj(index -> {
                    SubEvent subEvent = sortedSubEvents.get(index);
                    log.info("{} {} Processing sub-event {}/{} (ID: {})...",
                            EMOJI_TRANSFORM, EMOJI_INFO, index + 1, sortedSubEvents.size(), subEvent.getId());

                    return processSubEvent(subEvent, oddsMap, event, crumbsHolder, marketHolder, isLive, profitInfo);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Build and return ParsedArbitrageData
        return buildArbitrageData(event, profitInfo, marketHolder, outcomes, isLive);
    }

    /**
     * Immutable holder for profit-related information
     */
    @Value
    @Builder
    private static class ArbitrageProfitInfo {
        String arbId;
        BigDecimal profitPercentage;
        BigDecimal roi;
        LocalDateTime created;
        List<Integer> groupsIds;

        static ArbitrageProfitInfo from(ArbItem item, Event event) {
            return ArbitrageProfitInfo.builder()
                    .arbId(item != null ? item.getId() : event.getId())
                    .profitPercentage(item != null ? item.getValue() : BigDecimal.ZERO)
                    .roi(item != null ? item.getRoi() : null)
                    .created(item != null ? parseTimestamp(item.getCreated(), "yyyy-MM-dd HH:mm:ss") : null)
                    .groupsIds(item != null ? item.getGroupsIds() : null)
                    .build();
        }
    }

    /**
     * Mutable holder for sharing crumbs between sub-events
     */
    private static class CrumbsHolder {
        private Map<String, String> savedCrumbs;
        private String savedOid;

        void saveCrumbs(Map<String, String> crumbs, String oid) {
            if (this.savedCrumbs == null) {
                this.savedCrumbs = crumbs;
                this.savedOid = oid;
            }
        }

        boolean hasCrumbs() {
            return savedCrumbs != null && savedOid != null;
        }
    }

    /**
     * Mutable holder for market information
     */
    private static class MarketInfoHolder {
        private String marketType = "";
        private String lastOutcome = "";

        void updateMarket(String marketType, String outcome) {
            if (marketType != null) {
                this.marketType = marketType;
            }
            if (outcome != null) {
                this.lastOutcome = outcome;
            }
        }
    }

    /**
     * Process a single sub-event and create OutcomeData
     */
    private OutcomeData processSubEvent(SubEvent subEvent,
                                        Map<String, Odd> oddsMap,
                                        Event event,
                                        CrumbsHolder crumbsHolder,
                                        MarketInfoHolder marketHolder,
                                        boolean isLive, ArbitrageProfitInfo arbitrageProfitInfo) {
        Odd odd = oddsMap.get(subEvent.getId());
        BookMaker bookMaker = BookMakerMapper.getBookmakerName(subEvent.getBookmakerId());

        log.info("{} {} Sub-event {} bookmaker: {} (ID: {})",
                EMOJI_INFO, EMOJI_TRANSFORM, subEvent.getId(), bookMaker, subEvent.getBookmakerId());

        if (odd == null) {
            log.warn("{} {} No odds found for sub_event_id: {}",
                    EMOJI_WARNING, EMOJI_TRANSFORM, subEvent.getId());
            return null;
        }

        // Extract odds information
        OddsInfo oddsInfo = extractOddsInfo(odd);

        // Extract market information based on bookmaker type
        MarketInfo marketInfo = bookMaker == BookMaker._1WIN
                ? extractOneWinMarketInfo(odd, crumbsHolder, bookMaker)
                : extractStandardMarketInfo(odd, crumbsHolder, bookMaker);

        // Update market holder
        marketHolder.updateMarket(marketInfo.marketType, marketInfo.outcome);

        // Determine final outcome name
        String finalOutcome = bookMaker == BookMaker._1WIN
                ? oneWinOutcomeStyle(marketInfo.outcome, subEvent.getTeam2(), subEvent.getTeam1())
                : marketInfo.outcome;

        // Build bookmaker URL from SubEvent crumbs
        Map<String, String> subEventCrumbs = subEvent.getCrumbs();
        String bookmakerUrl = buildBookmakerUrlWithMatchType(bookMaker, subEventCrumbs, subEvent.getId(), isLive);

        if (bookmakerUrl != null) {
            log.info("📎 Built URL for {}: {}", bookMaker, bookmakerUrl);
        } else {
            log.debug("No URL built for bookmaker: {}", bookMaker);
        }

        // Build and return outcome
        OutcomeData outcome = buildOutcomeData(subEvent, bookMaker, oddsInfo, marketInfo, finalOutcome, bookmakerUrl, arbitrageProfitInfo);

        log.info("✅ Built outcome - bookMaker: {}, outcome: '{}', odds: {}, url: {}",
                bookMaker, finalOutcome, oddsInfo.value, bookmakerUrl != null ? "✓" : "✗");

        return outcome;
    }

    /**
     * Immutable holder for odds information
     */
    @Value
    @Builder
    private static class OddsInfo {
        BigDecimal value;
        BigDecimal previousValue;
        Boolean initiator;
        String index;
        LocalDateTime updated;

        static OddsInfo from(Odd odd) {
            return OddsInfo.builder()
                    .value(Optional.ofNullable(odd.getValue())
                            .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                            .orElse(BigDecimal.valueOf(2.00)))
                    .previousValue(odd.getPrev())
                    .initiator(odd.getInitiator())
                    .index(removeSinglePrefix(odd.getIndex()))
                    .updated(parseTimestamp(odd.getUpdated(), "yyyy-MM-dd HH:mm:ss"))
                    .build();
        }
    }

    public static String removeSinglePrefix(String input) {
        if (input == null) {
            return null;
        }

        if (input.isEmpty()) {
            return input;
        }

        String trimmed = input.trim();

        // Remove only single leading + or - sign
        if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
            return trimmed.substring(1);
        }

        return trimmed;
    }

    /**
     * Extract odds information from Odd object
     */
    private OddsInfo extractOddsInfo(Odd odd) {
        log.trace("{} {} Found odds: value={}, prev={}",
                EMOJI_SUCCESS, EMOJI_TRANSFORM, odd.getValue(), odd.getPrev());

        return OddsInfo.from(odd);
    }

    /**
     * Immutable holder for market information
     */
    @Value
    @Builder
    private static class MarketInfo {
        String marketType;
        String outcome;

        static MarketInfo empty() {
            return MarketInfo.builder().marketType("").outcome("").build();
        }

        static MarketInfo of(String marketType, String outcome) {
            return MarketInfo.builder()
                    .marketType(marketType != null ? marketType : "")
                    .outcome(outcome != null ? outcome : "")
                    .build();
        }
    }

    /**
     * Extract market info for standard (non-1WIN) bookmakers
     */
    private MarketInfo extractStandardMarketInfo(Odd odd,
                                                 CrumbsHolder crumbsHolder,
                                                 BookMaker bookMaker) {
        Map<String, String> crumbs = odd.getCrumbs();

        if (crumbs == null || crumbs.isEmpty()) {
            log.warn("{} {} No crumbs found for bookmaker: {}",
                    EMOJI_WARNING, EMOJI_CRUMBS, bookMaker);
            return MarketInfo.empty();
        }

        String oid = crumbs.get("oid");

        // Save crumbs for 1WIN to use later
        crumbsHolder.saveCrumbs(crumbs, oid);

        log.info("{} {} {} Saved crumbs with oid='{}' for bookmaker: {}",
                EMOJI_SUCCESS, EMOJI_CRUMBS, EMOJI_INFO, oid, bookMaker);

        // Get market outcome
        return Optional.ofNullable(getMarketOutcome(bookMaker, crumbs))
                .map(outcome -> {
                    String marketType = outcome.getName().trim();
                    String outcomeName = outcome.getOutcome(oid).trim();

                    log.info("{} {} {} Market resolved: marketType='{}', oid='{}', outcome='{}'",
                            EMOJI_SUCCESS, EMOJI_MARKET, EMOJI_CRUMBS, marketType, oid, outcomeName);

                    return MarketInfo.of(marketType, outcomeName);
                })
                .orElseGet(() -> {
                    log.warn("{} {} {} Market outcome returned null for bookmaker: {}",
                            EMOJI_WARNING, EMOJI_MARKET, EMOJI_CRUMBS, bookMaker);
                    return MarketInfo.empty();
                });
    }

    /**
     * Extract market info for 1WIN bookmaker using saved crumbs
     */
    private MarketInfo extractOneWinMarketInfo(Odd odd,
                                               CrumbsHolder crumbsHolder,
                                               BookMaker bookMaker) {
        if (!crumbsHolder.hasCrumbs()) {
            log.warn("{} {} {} No saved crumbs available for 1WIN bookmaker",
                    EMOJI_WARNING, EMOJI_CRUMBS, EMOJI_INFO);
            return MarketInfo.empty();
        }

        log.info("{} {} {} Using SAVED crumbs with oid='{}' for 1WIN",
                EMOJI_SUCCESS, EMOJI_CRUMBS, EMOJI_INFO, crumbsHolder.savedOid);

        return Optional.ofNullable(getMarketOutcome(bookMaker, crumbsHolder.savedCrumbs))
                .map(outcome -> {
                    String oppositeOid = OppositeOutcomeMapper.getOppositeKey(crumbsHolder.savedOid);
                    String marketType = outcome.getName().trim();
                    String outcomeName = outcome.getOutcome(oppositeOid).trim();

                    log.info("{} {} {} Market outcome for 1WIN: marketType='{}', originalOid='{}', oppositeOid='{}', outcome='{}'",
                            EMOJI_SUCCESS, EMOJI_MARKET, EMOJI_CRUMBS, marketType,
                            crumbsHolder.savedOid, oppositeOid, outcomeName);

                    return MarketInfo.of(marketType, outcomeName);
                })
                .orElseGet(() -> {
                    log.warn("{} {} {} Market outcome returned null for 1WIN",
                            EMOJI_WARNING, EMOJI_MARKET, EMOJI_CRUMBS);
                    return MarketInfo.empty();
                });
    }

    /**
     * Build OutcomeData object
     */
    private OutcomeData buildOutcomeData(SubEvent subEvent,
                                         BookMaker bookMaker,
                                         OddsInfo oddsInfo,
                                         MarketInfo marketInfo,
                                         String finalOutcome,
                                         String bookmakerUrl, ArbitrageProfitInfo profitInfo) {
        String marketType = bookMaker == BookMaker.SPORTYBET
                ? (Sport.fromDisplayName(subEvent.getSport()) == Sport.BASKETBALL
                ? marketInfo.marketType + " " + oddsInfo.index
                : marketInfo.marketType)
                : marketInfo.marketType;

        BigDecimal stakeAmount = ArbCalculator.calculateStakeFromProfit(profitInfo.profitPercentage, oddsInfo.value, TOTAL_STAKE);

        return OutcomeData.builder()
                .subEventId(subEvent.getId())
                .odds(oddsInfo.value)
                .previousOdds(oddsInfo.previousValue)
                .initiator(oddsInfo.initiator)
                .bookmakerId(subEvent.getBookmakerId())
                .bookmakerName(bookMaker)
                .sport(subEvent.getSport())
                .stake(stakeAmount)
                .league(subEvent.getLeague())
                .marketType(marketType)
                .team1(subEvent.getTeam1())
                .team2(subEvent.getTeam2())
                .progress(subEvent.getProgress())
                .originalId(subEvent.getOriginalId())
                .reordered(subEvent.getReordered())
                .outComeName(finalOutcome)
                .updated(oddsInfo.updated)
                .bookmakerUrl(bookmakerUrl)
                .build();
    }

    /**
     * Build final ParsedArbitrageData object
     */
    private ParsedArbitrageData buildArbitrageData(Event event,
                                                   ArbitrageProfitInfo profitInfo,
                                                   MarketInfoHolder marketHolder,
                                                   List<OutcomeData> outcomes,
                                                   boolean isLive) {
        LocalDateTime matchStart = parseTimestamp(event.getStart(), "yyyy-MM-dd HH:mm");

        log.trace("{} {} Parsed timestamps: created={}, matchStart={}",
                EMOJI_INFO, EMOJI_TRANSFORM, profitInfo.created, matchStart);

        ParsedArbitrageData parsed = ParsedArbitrageData.builder()
                .arbId(profitInfo.arbId)
                .eventId(event.getId())
                .profitPercentage(profitInfo.profitPercentage)
                .roi(profitInfo.roi)
                .generalMarketType(marketHolder.marketType)
                .generalOutcomeName(marketHolder.lastOutcome)
                .groupsIds(profitInfo.groupsIds)
                .sportId(event.getSportId())
                .sportName(BookMakerMapper.getSportName(event.getSportId()))
                .league(event.getLeague())
                .team1(event.getTeam1())
                .team2(event.getTeam2())
                .matchStart(matchStart)
                .created(profitInfo.created)
                .isLive(isLive)
                .progress(!outcomes.isEmpty() ? outcomes.get(0).getProgress() : null)
                .outcomes(outcomes)
                .build();

        log.debug("{} {} {} Converted event {} to arb: {} ({}) vs {} ({}) | {}% profit | marketType='{}' | outcome='{}'",
                EMOJI_SUCCESS, EMOJI_TRANSFORM, EMOJI_MARKET, event.getId(),
                outcomes.get(0).getBookmakerName(), outcomes.get(0).getOdds(),
                outcomes.get(1).getBookmakerName(), outcomes.get(1).getOdds(),
                profitInfo.profitPercentage, marketHolder.marketType, marketHolder.lastOutcome);

        return parsed;
    }

    /**
     * Replace "home"/"away" with actual team names in outcome string
     */
    private String oneWinOutcomeStyle(String outCome, String homeTeam, String awayTeam) {
        log.debug("🎯 oneWinOutcomeStyle - Input: outCome='{}', homeTeam='{}', awayTeam='{}'",
                outCome, homeTeam, awayTeam);

        if (outCome == null) {
            log.warn("⚠️ oneWinOutcomeStyle - outCome is NULL, returning null");
            return null;
        }

        String trimmed = outCome.trim();

        // Exact match (fast path)
        String exactMatch = Stream.of(
                        new AbstractMap.SimpleEntry<>("home", homeTeam),
                        new AbstractMap.SimpleEntry<>("away", awayTeam)
                )
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> trimmed.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (exactMatch != null) {
            log.info("✅ Exact match '{}' -> '{}'", trimmed.toLowerCase(), exactMatch);
            return exactMatch;
        }

        // Partial replacement
        String result = outCome;

        if (homeTeam != null) {
            String beforeReplace = result;
            result = replaceIgnoreCase(result, "home", homeTeam);
            if (!beforeReplace.equals(result)) {
                log.debug("🔄 Replaced 'home' with '{}': '{}' -> '{}'", homeTeam, beforeReplace, result);
            }
        }

        if (awayTeam != null) {
            String beforeReplace = result;
            result = replaceIgnoreCase(result, "away", awayTeam);
            if (!beforeReplace.equals(result)) {
                log.debug("🔄 Replaced 'away' with '{}': '{}' -> '{}'", awayTeam, beforeReplace, result);
            }
        }

        log.info("✅ oneWinOutcomeStyle result: '{}' (original: '{}')", result, outCome);
        return result;
    }

    /**
     * Case-insensitive string replacement
     */
    private String replaceIgnoreCase(String source, String target, String replacement) {
        return Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE)
                .matcher(source)
                .replaceAll(Matcher.quoteReplacement(replacement));
    }

    private @NonNull List<SubEvent> getSortedSubEvents(Event event) {
        List<SubEvent> sortedSubEvents = new ArrayList<>(event.getSubEvents());
        sortedSubEvents.sort((se1, se2) -> {
            BookMaker bm1 = BookMakerMapper.getBookmakerName(se1.getBookmakerId());
            BookMaker bm2 = BookMakerMapper.getBookmakerName(se2.getBookmakerId());

            int index1 = PREFERRED_BOOKMAKERS.indexOf(bm1);
            int index2 = PREFERRED_BOOKMAKERS.indexOf(bm2);

            // If bookmaker not in preferred list, assign high index
            if (index1 == -1) index1 = Integer.MAX_VALUE;
            if (index2 == -1) index2 = Integer.MAX_VALUE;

            return Integer.compare(index1, index2);
        });
        return sortedSubEvents;
    }

    /**
     * Builds a bookmaker-specific URL from SubEvent crumbs
     */
    public static String buildBookmakerUrlWithMatchType(BookMaker bookMaker,
                                                        Map<String, String> crumbs,
                                                        String subEventId,
                                                        boolean isLive) {
        if (crumbs == null || crumbs.isEmpty()) {
            log.debug("Cannot build URL - crumbs are null or empty for bookmaker: {}", bookMaker);
            return null;
        }

        // Add match_type to crumbs if needed
        Map<String, String> enrichedCrumbs = new HashMap<>(crumbs);
        enrichedCrumbs.put("match_type", isLive ? "live" : "result");

        return buildBookmakerUrl(bookMaker, enrichedCrumbs, subEventId);
    }

    /**
     * Builds a bookmaker-specific URL from SubEvent crumbs
     */
    public static String buildBookmakerUrl(BookMaker bookMaker, Map<String, String> crumbs, String subEventId) {
        if (crumbs == null || crumbs.isEmpty()) {
            return null;
        }

        try {
            switch (bookMaker) {
                case MSPORT:
                    return buildMSportUrl(crumbs);

                case SPORTYBET:
                    return buildSportyBetUrl(crumbs);

                case _1WIN:
                    return buildOneWinUrl(crumbs);

                case BET9JA:
                    return buildBet9jaUrl(crumbs);

                default:
                    log.debug("URL building not implemented for bookmaker: {}", bookMaker);
                    return null;
            }
        } catch (Exception e) {
            log.error("Failed to build URL for bookmaker {}: {}", bookMaker, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build MSport URL from crumbs
     */
    /**
     * Build MSport URL from crumbs
     * Format: <a href="https://www.msport.com/ng/web/sports/">...</a>{sport}/{live_or_result}/{league}/{team1}_vs_{team2}/sr:match:{event_id}
     *
     * Note: Sport names with spaces need URL encoding (e.g., "Table Tennis" -> "Table%20Tennis")
     *
     * Example crumbs:
     * {
     *   "event_id": "67941228",
     *   "league": "International_TT_Cup",
     *   "sport": "Table_Tennis",
     *   "team1": "Kus__Ondrej",
     *   "team2": "Vanous__Jiri"
     * }
     *
     * Result: <a href="https://www.msport.com/ng/web/sports/Table%20Tennis/live/International_TT_Cup/Kus__Ondrej_vs_Vanous__Jiri/sr:match:67941228">...</a>
     */
    private static String buildMSportUrl(Map<String, String> crumbs) {
        String eventId = crumbs.get("event_id");
        String league = crumbs.get("league");
        String sport = crumbs.get("sport");
        String team1 = crumbs.get("team1");
        String team2 = crumbs.get("team2");

        if (eventId == null || league == null || sport == null || team1 == null || team2 == null) {
            log.warn("Missing required crumbs for MSport URL. Available: {}", crumbs.keySet());
            return null;
        }

        String matchType = crumbs.getOrDefault("match_type", "live");

        // Convert sport name: "Table_Tennis" -> "Table Tennis" -> "Table%20Tennis"
        // Replace underscores with spaces, then URL encode
        String sportFormatted = sport.replace("_", " ");
        String sportEncoded = urlEncode(sportFormatted);

        String url = String.format("https://www.msport.com/ng/web/sports/%s/%s/%s/%s_vs_%s/sr:match:%s",
                sportEncoded,
                matchType,
                league,
                team1,
                team2,
                eventId
        );

        log.debug("Built MSport URL: {}", url);
        return url;
    }

    /**
     * URL encode a string (handles spaces and special characters)
     */
    private static String urlEncode(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20"); // Replace + with %20 for spaces
    }


    /**
     * Build SportyBet URL from crumbs
     * Format: <a href="https://www.sportybet.com/ng/sport/">...</a>{sport}/{live_or_result}/{country}/{league}/{team1}_vs_{team2}/sr:match:{event_id}
     *
     * Example crumbs:
     * {
     *   "country": "Germany",
     *   "country_id": "sr:category:111",
     *   "event_id": "62670809",
     *   "league": "BBL",
     *   "league_id": "sr:tournament:227",
     *   "path": "/ng/sport/basketball/live/sr:category:111/sr:tournament:227/sr:match:62670809",
     *   "sport": "basketball",
     *   "team1": "Niners_Chemnitz",
     *   "team2": "Bayern_Munich"
     * }
     *
     * Result: <a href="https://www.sportybet.com/ng/sport/basketball/live/Germany/BBL/Niners_Chemnitz_vs_Bayern_Munich/sr:match:62670809">...</a>
     */
    private static String buildSportyBetUrl(Map<String, String> crumbs) {
        String eventId = crumbs.get("event_id");
        String sport = crumbs.get("sport");
        String country = crumbs.get("country");
        String league = crumbs.get("league");
        String team1 = crumbs.get("team1");
        String team2 = crumbs.get("team2");

        if (eventId == null || sport == null || country == null || league == null || team1 == null || team2 == null) {
            log.warn("Missing required crumbs for SportyBet URL. Available: {}", crumbs.keySet());
            return null;
        }

        String matchType = crumbs.getOrDefault("match_type", "live");

        String url = String.format("https://www.sportybet.com/ng/sport/%s/%s/%s/%s/%s_vs_%s/sr:match:%s",
                sport,
                matchType,
                country,
                league,
                team1,
                team2,
                eventId
        );

        log.debug("Built SportyBet URL: {}", url);
        return url;
    }

    /**
     * Build 1WIN URL from crumbs
     * Format: <a href="https://1win.pro/betting/match/sport/">...</a>{team1}-vs-{team2}-{event_id}
     *
     * Example crumbs:
     * {
     *   "category_id": "228",
     *   "event_id": "32089527",
     *   "sport_id": "23",
     *   "team1": "chemnitz",
     *   "team2": "bayern-munich",
     *   "tournament_id": "1343"
     * }
     *
     * Result: <a href="https://1win.pro/betting/match/sport/chemnitz-vs-bayern-munich-32089527">...</a>
     */
    private static String buildOneWinUrl(Map<String, String> crumbs) {
        String eventId = crumbs.get("event_id");
        String team1 = crumbs.get("team1");
        String team2 = crumbs.get("team2");

        if (eventId == null || team1 == null || team2 == null) {
            log.warn("Missing required crumbs for 1WIN URL. Available: {}", crumbs.keySet());
            return null;
        }

        // Team names are already in lowercase with hyphens format in crumbs
        String url = String.format("https://1win.pro/betting/match/sport/%s-vs-%s-%s",
                team1,
                team2,
                eventId
        );

        log.debug("Built 1WIN URL: {}", url);
        return url;
    }


    /**
     * Build Bet9ja URL from crumbs
     */
    private static String buildBet9jaUrl(Map<String, String> crumbs) {
        String eventId = crumbs.get("event_id");

        if (eventId == null) {
            log.warn("Missing event_id for Bet9ja URL");
            return null;
        }

        String url = String.format("https://web.bet9ja.com/Sport/EventDetails?eventId=%s", eventId);

        log.debug("Built Bet9ja URL: {}", url);
        return url;
    }

    private MarketOutcome getMarketOutcome(BookMaker bookMaker, Map<String, String> crumbs) {
        log.debug("{} {} {} Getting market outcome for bookmaker: {}",
                EMOJI_MARKET, EMOJI_CRUMBS, EMOJI_INFO, bookMaker);

        switch (bookMaker) {
            case _1WIN -> {
                log.debug("{} {} Processing MSport crumbs....", EMOJI_MARKET, EMOJI_CRUMBS);

                String mid = crumbs.get("mid");
                String spec = crumbs.get("spec");

                log.debug("{} {} Extracted crumbs:-- mid='{}', spec='{}'",
                        EMOJI_CRUMBS, EMOJI_INFO, mid, spec);

                if (mid == null) {
                    log.error("{} {} {} Missing required crumbs for oneWin: mid={}, spec={}",
                            EMOJI_ERROR, EMOJI_MARKET, EMOJI_CRUMBS, mid, spec);
                    throw new IllegalArgumentException("Missing required crumbs for onewin: mid, spec");
                }

                log.info("{} {} Searching oneWinBetMapper for market: mid='{}', spec='{}'",
                        EMOJI_MARKET, EMOJI_INFO, mid, spec);

                MarketOutcome result = oneWinMapper.searchMarket(mid, spec);

                if (result != null) {
                    log.info("{} {} {} Market found: name='{}', outcomes={}",
                            EMOJI_SUCCESS, EMOJI_MARKET, EMOJI_CRUMBS,
                            result.getName(), result.getOutcomes().size());
                } else {
                    log.warn("{} {} {} No market found for mid='{}', spec='{}'",
                            EMOJI_WARNING, EMOJI_MARKET, EMOJI_CRUMBS, mid, spec);
                }

                return result;

            }
            case MSPORT -> {
                log.debug("{} {} Processing MSport crumbs...", EMOJI_MARKET, EMOJI_CRUMBS);

                String mid = crumbs.get("mid");
                String spec = crumbs.get("spec");

                log.debug("{} {} Extracted crumbs:- mid='{}', spec='{}'",
                        EMOJI_CRUMBS, EMOJI_INFO, mid, spec);

                if (mid == null) {
                    log.error("{} {} {} Missing required crumbs for MSport: mid={}, spec={}",
                            EMOJI_ERROR, EMOJI_MARKET, EMOJI_CRUMBS, mid, spec);
                    throw new IllegalArgumentException("Missing required crumbs for MSport: mid, spec");
                }

                log.info("{} {} Searching MSportBetMapper for market: mid='{}', spec='{}'",
                        EMOJI_MARKET, EMOJI_INFO, mid, spec);

                MarketOutcome result = mSportBetMapper.searchMarket(mid, spec);

                if (result != null) {
                    log.info("{} {} {} Market found: name='{}', outcomes={}",
                            EMOJI_SUCCESS, EMOJI_MARKET, EMOJI_CRUMBS,
                            result.getName(), result.getOutcomes().size());
                } else {
                    log.warn("{} {} {} No market found for mid='{}', spec='{}'",
                            EMOJI_WARNING, EMOJI_MARKET, EMOJI_CRUMBS, mid, spec);
                }

                return result;

            }

            case SPORTYBET -> {
                log.debug("{} {} Processing SPORTYBET crumbs...", EMOJI_MARKET, EMOJI_CRUMBS);

                String mid = crumbs.get("mid");
                String spec = crumbs.get("spec");

                log.debug("{} {} Extracted crumbs: mid='{}', spec='{}'",
                        EMOJI_CRUMBS, EMOJI_INFO, mid, spec);

                if (mid == null) {
                    log.error("{} {} {} Missing required crumbs for SPORTYBET: mid={}, spec={}",
                            EMOJI_ERROR, EMOJI_MARKET, EMOJI_CRUMBS, mid, spec);
                    throw new IllegalArgumentException("Missing required crumbs for SPORTYBET: mid, spec");
                }

                log.info("{} {} Searching SportyBetMapper for market: mid='{}', spec='{}'",
                        EMOJI_MARKET, EMOJI_INFO, mid, spec);

                MarketOutcome result = sportyBetMapper.searchMarket(mid, spec);

                if (result != null) {
                    log.info("{} {} {} Market found: name='{}', outcomes={}",
                            EMOJI_SUCCESS, EMOJI_MARKET, EMOJI_CRUMBS,
                            result.getName(), result.getOutcomes().size());
                } else {
                    log.warn("{} {} {} No market found for mid='{}', spec='{}'",
                            EMOJI_WARNING, EMOJI_MARKET, EMOJI_CRUMBS, mid, spec);
                }

                return result;
            }

            default -> {
                log.error("{} {} No market outcome mapping implemented for bookmaker: {}",
                        EMOJI_ERROR, EMOJI_MARKET, bookMaker);
                throw new UnsupportedOperationException(
                        "No market outcome mapping implemented for bookmaker: " + bookMaker
                );
            }
        }
    }

    private static LocalDateTime parseTimestamp(String timestamp, String pattern) {
        if (timestamp == null) return null;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.parse(timestamp, formatter);
        } catch (DateTimeParseException e) {
            log.debug("Could not parse timestamp: {}", timestamp);
            return null;
        }
    }

    private List<ArbitrageOpportunity> transformToEntitiesConcurrent(List<ParsedArbitrageData> parsedDataList) {
        log.debug("{} {} {} Transforming {} parsed arbs to entities...",
                EMOJI_TRANSFORM, EMOJI_CONCURRENT, EMOJI_INFO, parsedDataList.size());

        List<CompletableFuture<ArbitrageOpportunity>> futures = parsedDataList.stream()
                .map(data -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return transformSingleToEntity(data);
                    } catch (Exception e) {
                        log.warn("{} {} Failed to transform arb {}: {}",
                                EMOJI_WARNING, EMOJI_TRANSFORM, data.getArbId(), e.getMessage());
                        return null;
                    }
                }, processingExecutor))
                .toList();

        List<ArbitrageOpportunity> entities = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.debug("{} {} {} Transformed {} entities",
                EMOJI_SUCCESS, EMOJI_CONCURRENT, EMOJI_TRANSFORM, entities.size());
        return entities;
    }

    private ArbitrageOpportunity transformSingleToEntity(ParsedArbitrageData data) {
        if (data.getOutcomes().size() != 2) {
            return null;
        }

        ArbitrageOpportunity arb = ArbitrageOpportunity.builder()
                .externalId(data.getArbId())
                .eventId(data.getEventId())
                .sport(data.getSportName())
                .sportId(data.getSportId())
                .leagueName(data.getLeague())
                .homeTeam(data.getTeam1())
                .awayTeam(data.getTeam2())
                .matchStartTime(data.getMatchStart())
                .isLive(data.getIsLive())
                .matchProgress(data.getProgress())
                .marketType(data.getGeneralMarketType())
                .outCome(data.getGeneralOutcomeName())
                .profitPercentage(data.getProfitPercentage() != null
                        ? data.getProfitPercentage().setScale(4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .roiPercentage(data.getRoi() != null
                        ? data.getRoi().setScale(4, RoundingMode.HALF_UP)
                        : null)
                .status(ArbStatus.ACTIVE)
                .lastCheckedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .confidenceScore(calculateConfidenceScore(
                        data.getProfitPercentage() != null ? data.getProfitPercentage() : BigDecimal.ZERO,
                        data.getCreated() != null
                                ? Duration.between(data.getCreated(), LocalDateTime.now()).getSeconds()
                                : 0L))
                .outcomes(new ArrayList<>())
                .build();

        try {
//            arb.setRawData(objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            log.debug("Could not serialize raw data: {}", e.getMessage());
        }

        for (OutcomeData outcomeData : data.getOutcomes()) {
            ArbOutcome outcome = ArbOutcome.builder()
                    .bookmakerId(outcomeData.getBookmakerId())
                    .bookmakerName(outcomeData.getBookmakerName())
                    .outComeName(outcomeData.getOutComeName())
                    .marketType(outcomeData.getMarketType())
                    .odds(outcomeData.getOdds())
                    .previousOdds(outcomeData.getPreviousOdds())
                    .subEventId(outcomeData.getSubEventId())
                    .originalId(outcomeData.getOriginalId())
                    .sport(outcomeData.getSport())
                    .awayTeam(outcomeData.getTeam1())
                    .homeTeam(outcomeData.getTeam2())
                    .leagueName(outcomeData.getLeague())
                    .stake(outcomeData.getStake())
                    .progress(outcomeData.getProgress())
                    .reordered(outcomeData.getReordered())
                    .initiator(outcomeData.getInitiator())
                    .bookMakerUrl(outcomeData.getBookmakerUrl())
                    .build();
            arb.addOutcome(outcome);
        }

        return arb;
    }

    /**
     * Save opportunities using thread-safe ArbitrageService
     * Uses concurrent processing with proper error handling
     */
    /**
     * Save opportunities sequentially using thread-safe ArbitrageService
     * Simpler and more reliable than concurrent saves
     */
    private int saveArbitrageOpportunitiesSequential(List<ArbitrageOpportunity> opportunities) {
        log.debug("{} {} Processing {} opportunities for database save...",
                EMOJI_SAVE, EMOJI_INFO, opportunities.size());

        if (opportunities.isEmpty()) {
            log.debug("{} {} No opportunities to save", EMOJI_INFO, EMOJI_SAVE);
            return 0;
        }

        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (ArbitrageOpportunity opportunity : opportunities) {
            try {
                ArbitrageService.SaveResult result = arbitrageService.saveOrUpdateArbitrage(opportunity);

                switch (result) {
                    case SAVED -> savedCount++;
                    case UPDATED -> updatedCount++;
                    case SKIPPED -> skippedCount++;
                }

            } catch (Exception e) {
                log.warn("{} {} Failed to save opportunity {}: {}",
                        EMOJI_WARNING, EMOJI_SAVE,
                        opportunity.getExternalId(), e.getMessage());
                skippedCount++;
            }
        }

        log.info("{} {} Database save completed: {} new, {} updated, {} skipped",
                EMOJI_SUCCESS, EMOJI_SAVE, savedCount, updatedCount, skippedCount);

        return savedCount + updatedCount;
    }

    // === CONFIDENCE SCORE CALCULATION ===

    private BigDecimal calculateConfidenceScore(BigDecimal profit, Long ageSeconds) {
        BigDecimal score = BigDecimal.valueOf(100);

        // Profit factor
        if (profit.compareTo(BigDecimal.valueOf(2)) < 0) {
            score = score.multiply(BigDecimal.valueOf(0.7));
        }

        // Age factor
        if (ageSeconds > 300) {
            score = score.multiply(BigDecimal.valueOf(0.5));
        } else if (ageSeconds > 60) {
            score = score.multiply(BigDecimal.valueOf(0.8));
        }

        return score.setScale(2, RoundingMode.HALF_UP);
    }

    // === STATISTICS ===

    private void logStatistics() {
        try {
            ArbitrageService.ArbStatistics stats = arbitrageService.getStatistics();

            log.info("{} {} Current Statistics:", EMOJI_STATS, EMOJI_INFO);
            log.info("{} Total Active: {}", EMOJI_STATS, stats.totalActive());
            log.info("{} Fresh Active: {} ({}%)", EMOJI_STATS,
                    stats.freshActive(),
                    String.format("%.1f", stats.freshPercentage()));
            log.info("{} Stale Active: {} ({}%)", EMOJI_STATS,
                    stats.staleActive(),
                    String.format("%.1f", stats.stalePercentage()));
            log.info("{} Average Profit: {}%", EMOJI_STATS,
                    stats.averageProfit() != null
                            ? stats.averageProfit().setScale(2, RoundingMode.HALF_UP)
                            : "N/A");
        } catch (Exception e) {
            log.debug("Could not retrieve statistics: {}", e.getMessage());
        }
    }

    public boolean isPollingActive() {
        return isRunning;
    }

    public boolean isCurrentlyProcessing() {
        return isProcessing;
    }


}