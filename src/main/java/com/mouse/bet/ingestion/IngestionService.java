package com.mouse.bet.ingestion;

import com.mouse.bet.client.BreakingBetClient;
import com.mouse.bet.config.WindowConfig;
import com.mouse.bet.dto.*;
import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.mapper.*;
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
//import org.springframework.beans.factory.annotation.Value;

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



    private final WindowConfig windowConfig;

    List<BookMaker> PREFERRED_BOOKMAKERS = Arrays.asList(
            BookMaker.SPORTYBET,  // First priority
            BookMaker.MSPORT      // Second priority
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
    private Bet9jaMapper bet9jaMapper;
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

        sportyBetMapper = new SportyBetMapper();
        mSportBetMapper = new MSportBetMapper();
        oneWinMapper = new OneWinMapper();
        bet9jaMapper = new Bet9jaMapper();
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
        if (!isRunning) return;

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
            int saved = saveArbitrageOpportunitiesSequential(opportunities);

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

        Map<String, Odd> oddsMap = buildOddsMap(response.getItems());
        Map<String, ArbItem> itemMap = buildItemMap(response.getItems());

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

    private Map<String, ArbItem> buildItemMap(List<ArbItem> items) {
        if (items == null) return Collections.emptyMap();

        return items.stream()
                .collect(Collectors.toMap(ArbItem::getEventId, item -> item, (a, b) -> a));
    }

    private ParsedArbitrageData convertEventToArbitrage(Event event,
                                                        ArbItem item,
                                                        Map<String, Odd> oddsMap,
                                                        boolean isLive) {
        log.trace("{} {} Converting event {} to arbitrage...", EMOJI_TRANSFORM, EMOJI_INFO, event.getId());

        if (event.getSubEvents() == null || event.getSubEvents().size() != 2) {
            log.debug("{} {} Ignoring event {} - has {} sub-events (only accepting 2-way)",
                    EMOJI_INFO, EMOJI_TRANSFORM, event.getId(),
                    event.getSubEvents() != null ? event.getSubEvents().size() : 0);
            return null;
        }

        ArbitrageProfitInfo profitInfo = ArbitrageProfitInfo.from(item, event);

        List<SubEvent> sortedSubEvents = getSortedSubEvents(event);

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

        return buildArbitrageData(event, profitInfo, marketHolder, outcomes, isLive);
    }

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

    private static class CrumbsHolder {
        private Map<String, String> savedCrumbs;
        private String savedKey;           // Always "oid" since Bet9ja never saves
        private BookMaker sourceBookmaker;

        void saveCrumbs(BookMaker bm, Map<String, String> crumbs) {
            if (this.savedCrumbs != null) return;

            this.sourceBookmaker = bm;
            this.savedCrumbs = new HashMap<>(crumbs);
            this.savedKey = "oid";

            log.info("{} {} Saved crumbs from {} using key='oid' ({} entries)",
                    EMOJI_SUCCESS, EMOJI_CRUMBS, bm, crumbs.size());
        }

        boolean hasCrumbs() {
            return savedCrumbs != null && savedKey != null;
        }

        String getSavedKey() {
            return savedKey;
        }

        Map<String, String> getSavedCrumbs() {
            return savedCrumbs;
        }

        BookMaker getSourceBookmaker() {
            return sourceBookmaker;
        }
    }

    private static class MarketInfoHolder {
        private String marketType = "";
        private String lastOutcome = "";

        void updateMarket(String marketType, String outcome) {
            if (marketType != null) this.marketType = marketType;
            if (outcome != null) this.lastOutcome = outcome;
        }
    }

    private OutcomeData processSubEvent(SubEvent subEvent,
                                        Map<String, Odd> oddsMap,
                                        Event event,
                                        CrumbsHolder crumbsHolder,
                                        MarketInfoHolder marketHolder,
                                        boolean isLive,
                                        ArbitrageProfitInfo arbitrageProfitInfo) {
        Odd odd = oddsMap.get(subEvent.getId());
        BookMaker bookMaker = BookMakerMapper.getBookmakerName(subEvent.getBookmakerId());

        log.info("{} {} Sub-event {} bookmaker: {} (ID: {})",
                EMOJI_INFO, EMOJI_TRANSFORM, subEvent.getId(), bookMaker, subEvent.getBookmakerId());

        if (odd == null) {
            log.warn("{} {} No odds found for sub_event_id: {}",
                    EMOJI_WARNING, EMOJI_TRANSFORM, subEvent.getId());
            return null;
        }

        OddsInfo oddsInfo = extractOddsInfo(odd);

        // Only save crumbs from non-1WIN and non-Bet9ja bookmakers
        if (bookMaker != BookMaker._1WIN && bookMaker != BookMaker.BET9JA) {
            Map<String, String> crumbs = odd.getCrumbs();
            if (crumbs != null && !crumbs.isEmpty() && crumbsHolder.savedCrumbs == null) {
                crumbsHolder.saveCrumbs(bookMaker, crumbs);
            }
        }

        MarketInfo marketInfo = bookMaker == BookMaker._1WIN
                ? extractOneWinMarketInfo(odd, crumbsHolder, bookMaker)
                : extractStandardMarketInfo(odd, crumbsHolder, bookMaker);

        marketHolder.updateMarket(marketInfo.marketType, marketInfo.outcome);

        String finalOutcome = bookMaker == BookMaker._1WIN
                ? oneWinOutcomeStyle(marketInfo.outcome, subEvent.getTeam2(), subEvent.getTeam1())
                : marketInfo.outcome;

        Map<String, String> subEventCrumbs = subEvent.getCrumbs();
        String bookmakerUrl = buildBookmakerUrlWithMatchType(bookMaker, subEventCrumbs, subEvent.getId(), isLive);

        if (bookmakerUrl != null) {
            log.info("📎 Built URL for {}: {}", bookMaker, bookmakerUrl);
        } else {
            log.debug("No URL built for bookmaker: {}", bookMaker);
        }

        OutcomeData outcome = buildOutcomeData(subEvent, bookMaker, oddsInfo, marketInfo, finalOutcome, bookmakerUrl, arbitrageProfitInfo);

        log.info("✅ Built outcome - bookMaker: {}, outcome: '{}', odds: {}, url: {}",
                bookMaker, finalOutcome, oddsInfo.value, bookmakerUrl != null ? "✓" : "✗");

        return outcome;
    }

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
        if (input == null) return null;
        if (input.isEmpty()) return input;
        String trimmed = input.trim();
        if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private OddsInfo extractOddsInfo(Odd odd) {
        log.trace("{} {} Found odds: value={}, prev={}",
                EMOJI_SUCCESS, EMOJI_TRANSFORM, odd.getValue(), odd.getPrev());
        return OddsInfo.from(odd);
    }

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

    private MarketInfo extractStandardMarketInfo(Odd odd,
                                                 CrumbsHolder crumbsHolder,
                                                 BookMaker bookMaker) {
        Map<String, String> crumbs = odd.getCrumbs();

        if (crumbs == null || crumbs.isEmpty()) {
            log.warn("{} {} No crumbs found for bookmaker: {}",
                    EMOJI_WARNING, EMOJI_CRUMBS, bookMaker);
            return MarketInfo.empty();
        }

        String keyToUse;
        Map<String, String> crumbsToUse;
        boolean isBet9jaSpecialProcessing = (bookMaker == BookMaker.BET9JA);

        if (isBet9jaSpecialProcessing) {
            // Bet9ja ALWAYS uses its own crumbs
            crumbsToUse = crumbs;
            keyToUse = "id2";
        } else if (crumbsHolder.hasCrumbs()) {
            // Other normal bookmakers use saved crumbs (only from non-Bet9ja sources)
            crumbsToUse = crumbsHolder.getSavedCrumbs();
            keyToUse = crumbsHolder.getSavedKey();
        } else {
            // First non-Bet9ja non-1WIN bookmaker → save now with oid
            crumbsToUse = crumbs;
            keyToUse = "oid";
            crumbsHolder.saveCrumbs(bookMaker, crumbs);
        }

        String rawId = crumbsToUse.get(keyToUse);

        if (rawId == null) {
            log.warn("{} {} Missing key '{}' in crumbs for {} (available keys: {})",
                    EMOJI_WARNING, EMOJI_CRUMBS, keyToUse, bookMaker, crumbsToUse.keySet());
            return MarketInfo.empty();
        }

        // For Bet9ja: take the part AFTER the LAST underscore
        String selectedId;
        if (isBet9jaSpecialProcessing) {
            int lastUnderscore = rawId.lastIndexOf('_');
            if (lastUnderscore == -1 || lastUnderscore == rawId.length() - 1) {
                log.warn("{} {} Bet9ja id2 format invalid (no '_' or ends with '_'): '{}'",
                        EMOJI_WARNING, EMOJI_CRUMBS, rawId);
                return MarketInfo.empty();
            }
            selectedId = rawId.substring(lastUnderscore + 1).trim();
        } else {
            // Normal bookmakers use the value as-is
            selectedId = rawId;
        }

        log.info("{} {} Using key='{}' = '{}' → selectedId='{}' for bookmaker: {} (source: {})",
                EMOJI_CRUMBS, EMOJI_INFO,
                keyToUse, rawId, selectedId, bookMaker,
                isBet9jaSpecialProcessing ? "own crumbs (post-underscore)" :
                        (crumbsHolder.hasCrumbs() ? crumbsHolder.getSourceBookmaker() : "self (first non-Bet9ja)"));

        return Optional.ofNullable(getMarketOutcome(bookMaker, crumbsToUse))
                .map(outcome -> {
                    String marketType = outcome.getName().trim();
                    String outcomeName = outcome.getOutcome(selectedId).trim();

                    log.info("{} {} Market resolved: marketType='{}', selectedId='{}', outcome='{}'",
                            EMOJI_SUCCESS, EMOJI_MARKET, marketType, selectedId, outcomeName);

                    return MarketInfo.of(marketType, outcomeName);
                })
                .orElseGet(() -> {
                    log.warn("{} {} Market outcome returned null for bookmaker: {}",
                            EMOJI_WARNING, EMOJI_MARKET, bookMaker);
                    return MarketInfo.empty();
                });
    }


    private MarketInfo extractOneWinMarketInfo(Odd odd,
                                               CrumbsHolder crumbsHolder,
                                               BookMaker bookMaker) {
        if (!crumbsHolder.hasCrumbs()) {
            log.warn("{} {} No saved crumbs available for 1WIN bookmaker (Bet9ja cannot provide reference)",
                    EMOJI_WARNING, EMOJI_CRUMBS);
            return MarketInfo.empty();
        }

        Map<String, String> crumbs = crumbsHolder.getSavedCrumbs();
        String savedKey = crumbsHolder.getSavedKey();

        log.info("{} {} Using SAVED crumbs (from {}) with key='{}' for 1WIN",
                EMOJI_SUCCESS, EMOJI_CRUMBS, crumbsHolder.getSourceBookmaker(), savedKey);

        return Optional.ofNullable(getMarketOutcome(bookMaker, crumbs))
                .map(outcome -> {
                    String originalValue = crumbs.get(savedKey);
                    String oppositeKey = OppositeOutcomeMapper.getOppositeKey(originalValue);
                    String marketType = outcome.getName().trim();
                    String outcomeName = outcome.getOutcome(oppositeKey).trim();

                    log.info("{} {} Market outcome for 1WIN: marketType='{}', originalKey='{}', oppositeKey='{}', outcome='{}'",
                            EMOJI_SUCCESS, EMOJI_MARKET, marketType,
                            savedKey, oppositeKey, outcomeName);

                    return MarketInfo.of(marketType, outcomeName);
                })
                .orElseGet(() -> {
                    log.warn("{} {} Market outcome returned null for 1WIN",
                            EMOJI_WARNING, EMOJI_MARKET);
                    return MarketInfo.empty();
                });
    }

    private OutcomeData buildOutcomeData(SubEvent subEvent,
                                         BookMaker bookMaker,
                                         OddsInfo oddsInfo,
                                         MarketInfo marketInfo,
                                         String finalOutcome,
                                         String bookmakerUrl,
                                         ArbitrageProfitInfo profitInfo) {
        String marketType = bookMaker == BookMaker.SPORTYBET
                ? (Sport.fromDisplayName(subEvent.getSport()) == Sport.BASKETBALL
                ? marketInfo.marketType + " " + oddsInfo.index
                : marketInfo.marketType)
                : marketInfo.marketType;

        BigDecimal stakeAmount = ArbCalculator.calculateStakeFromProfit(profitInfo.profitPercentage, oddsInfo.value, windowConfig.getTotalStake());

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

    private String oneWinOutcomeStyle(String outCome, String homeTeam, String awayTeam) {
        log.debug("🎯 oneWinOutcomeStyle - Input: outCome='{}', homeTeam='{}', awayTeam='{}'",
                outCome, homeTeam, awayTeam);

        if (outCome == null) {
            log.warn("⚠️ oneWinOutcomeStyle - outCome is NULL, returning null");
            return null;
        }

        String trimmed = outCome.trim();

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

            if (index1 == -1) index1 = Integer.MAX_VALUE;
            if (index2 == -1) index2 = Integer.MAX_VALUE;

            return Integer.compare(index1, index2);
        });
        return sortedSubEvents;
    }

    public static String buildBookmakerUrlWithMatchType(BookMaker bookMaker,
                                                        Map<String, String> crumbs,
                                                        String subEventId,
                                                        boolean isLive) {
        if (crumbs == null || crumbs.isEmpty()) {
            log.debug("Cannot build URL - crumbs are null or empty for bookmaker: {}", bookMaker);
            return null;
        }

        Map<String, String> enrichedCrumbs = new HashMap<>(crumbs);
        enrichedCrumbs.put("match_type", isLive ? "live" : "result");

        return buildBookmakerUrl(bookMaker, enrichedCrumbs, subEventId);
    }

    public static String buildBookmakerUrl(BookMaker bookMaker, Map<String, String> crumbs, String subEventId) {
        if (crumbs == null || crumbs.isEmpty()) return null;

        try {
            switch (bookMaker) {
                case MSPORT:   return buildMSportUrl(crumbs);
                case SPORTYBET: return buildSportyBetUrl(crumbs);
                case _1WIN:    return buildOneWinUrl(crumbs);
                case BET9JA:   return buildBet9jaUrl(crumbs);
                default:
                    log.debug("URL building not implemented for bookmaker: {}", bookMaker);
                    return null;
            }
        } catch (Exception e) {
            log.error("Failed to build URL for bookmaker {}: {}", bookMaker, e.getMessage(), e);
            return null;
        }
    }

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
        String sportFormatted = sport.replace("_", " ");
        String sportEncoded = urlEncode(sportFormatted);

        return String.format("https://www.msport.com/ng/web/sports/%s/%s/%s/%s_vs_%s/sr:match:%s",
                sportEncoded, matchType, league, team1, team2, eventId);
    }

    private static String urlEncode(String value) {
        if (value == null) return null;
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

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

        return String.format("https://www.sportybet.com/ng/sport/%s/%s/%s/%s/%s_vs_%s/sr:match:%s",
                sport, matchType, country, league, team1, team2, eventId);
    }

    private static String buildOneWinUrl(Map<String, String> crumbs) {
        String eventId = crumbs.get("event_id");
        String team1 = crumbs.get("team1");
        String team2 = crumbs.get("team2");

        if (eventId == null || team1 == null || team2 == null) {
            log.warn("Missing required crumbs for 1WIN URL. Available: {}", crumbs.keySet());
            return null;
        }

        return String.format("https://1win.pro/betting/match/sport/%s-vs-%s-%s",
                team1, team2, eventId);
    }

    private static String buildBet9jaUrl(Map<String, String> crumbs) {
        String eventId = crumbs.get("event_id");

        if (eventId == null || eventId.trim().isEmpty()) {
            log.warn("Cannot build Bet9ja URL – missing or empty 'event_id'. Crumbs keys: {}",
                    crumbs != null ? crumbs.keySet() : "null");
            return null;
        }

        String url = "https://sports.bet9ja.com/liveEvent/" + eventId.trim();

        log.debug("Built Bet9ja modern URL: {}", url);
        return url;
    }

    private MarketOutcome getMarketOutcome(BookMaker bookMaker, Map<String, String> crumbs) {
        log.debug("{} {} Getting market outcome for bookmaker: {}", EMOJI_MARKET, EMOJI_CRUMBS, bookMaker);

        switch (bookMaker) {
            case _1WIN:
                String mid1 = crumbs.get("mid");
                String spec1 = crumbs.get("spec");
                if (mid1 == null) throw new IllegalArgumentException("Missing mid for 1WIN");
                return oneWinMapper.searchMarket(mid1, spec1);

            case MSPORT:
                String midM = crumbs.get("mid");
                String specM = crumbs.get("spec");
                if (midM == null) throw new IllegalArgumentException("Missing mid for MSPORT");
                return mSportBetMapper.searchMarket(midM, specM);

            case BET9JA:
                String id = crumbs.get("id2");

                if (id == null) throw new IllegalArgumentException("Missing id for BET9JA");

                String mid = getMarketId(id);
                return bet9jaMapper.searchMarket(mid, null);

            case SPORTYBET:
                String midS = crumbs.get("mid");
                String specS = crumbs.get("spec");
                if (midS == null) throw new IllegalArgumentException("Missing mid for SPORTYBET");
                return sportyBetMapper.searchMarket(midS, specS);

            default:
                log.error("No market outcome mapping for bookmaker: {}", bookMaker);
                throw new UnsupportedOperationException("No mapping for " + bookMaker);
        }
    }

    private String getMarketId(String key) {
        return key.substring(0, key.lastIndexOf('_'));
    }

    private static LocalDateTime parseTimestamp(String timestamp, String pattern) {
        if (timestamp == null) return null;
        try {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            log.debug("Could not parse timestamp: {}", timestamp);
            return null;
        }
    }

    private List<ArbitrageOpportunity> transformToEntitiesConcurrent(List<ParsedArbitrageData> parsedDataList) {
        return parsedDataList.stream()
                .map(this::transformSingleToEntity)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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