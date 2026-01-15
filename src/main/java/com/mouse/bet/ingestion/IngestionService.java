package com.mouse.bet.ingestion;

import com.mouse.bet.client.BreakingBetClient;
import com.mouse.bet.dto.*;
import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.mapper.MSportBetMapper;
import com.mouse.bet.mapper.OneWinMapper;
import com.mouse.bet.mapper.OppositeOutcomeMapper;
import com.mouse.bet.mapper.SportyBetMapper;
import com.mouse.bet.mapper.model.MarketOutcome;
import com.mouse.bet.monitoring.ArbitrageDataValidator;
import com.mouse.bet.repository.ArbOutcomeRepository;
import com.mouse.bet.repository.ArbitrageRepository;
import com.mouse.bet.transformation.BookMakerMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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


    List<BookMaker> PREFERRED_BOOKMAKERS = Arrays.asList(
            BookMaker.SPORTYBET,  // First priority
            BookMaker.MSPORT // Second priority
    );


    private final BreakingBetClient breakingBetClient;
    private final ArbitrageRepository arbitrageRepository;
    private final ArbOutcomeRepository arbOutcomeRepository;
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
            int saved = saveArbitrageOpportunitiesConcurrent(opportunities);

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
        return saveArbitrageOpportunitiesConcurrent(opportunities);
    }

    @Transactional
    public int ingestPrematchArbsManual() throws IOException {
        log.info("{} {} Manual prematch arbs ingestion triggered", EMOJI_INGESTION, EMOJI_INFO);
        BreakingBetResponse response = breakingBetClient.fetchPrematchArbsAsObject();
        List<ParsedArbitrageData> parsedData = enrichEventsToArbitrages(response, false);
        List<ArbitrageOpportunity> opportunities = transformToEntitiesConcurrent(parsedData);
        return saveArbitrageOpportunitiesConcurrent(opportunities);
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
     */
    private ParsedArbitrageData convertEventToArbitrage(Event event,
                                                        ArbItem item,
                                                        Map<String, Odd> oddsMap,
                                                        boolean isLive) {
        log.trace("{} {} Converting event {} to arbitrage...", EMOJI_TRANSFORM, EMOJI_INFO, event.getId());

        // Filter: Only 2-way arbs
        if (event.getSubEvents() == null || event.getSubEvents().size() != 2) {
            log.debug("{} {} Ignoring event {} - has {} sub-events (only accepting 2-way)",
                    EMOJI_INFO, EMOJI_TRANSFORM, event.getId(),
                    event.getSubEvents() != null ? event.getSubEvents().size() : 0);
            return null;
        }

        log.trace("{} {} Event {} has 2 sub-events, proceeding with conversion",
                EMOJI_SUCCESS, EMOJI_TRANSFORM, event.getId());

        // Get profit info from item (may be null if no item for this event)
        BigDecimal profitPercentage = item != null ? item.getValue() : BigDecimal.ZERO;
        BigDecimal roi = item != null ? item.getRoi() : null;
        String arbId = item != null ? item.getId() : event.getId();
        String created = item != null ? item.getCreated() : null;

        log.trace("{} {} Event {} profit info: arbId={}, profit={}%, roi={}",
                EMOJI_INFO, EMOJI_TRANSFORM, event.getId(), arbId, profitPercentage, roi);

        String marketType = "";
        String outCome = "";
        Map<String, String> crumbs = null;
        String oid = "";


        // Sort sub-events by bookmaker priority
        List<SubEvent> sortedSubEvents = getSortedSubEvents(event);

        // Convert each SubEvent to Outcome
        log.trace("{} {} Processing {} sub-events for event {}...",
                EMOJI_TRANSFORM, EMOJI_INFO, sortedSubEvents.size(), event.getId());

        List<OutcomeData> outcomes = new ArrayList<>();
        int subEventIndex = 0;

        for (SubEvent subEvent : sortedSubEvents) {
            subEventIndex++;
            log.info("{} {} Processing sub-event {}/{} (ID: {})...",
                    EMOJI_TRANSFORM, EMOJI_INFO, subEventIndex, sortedSubEvents.size(), subEvent.getId());

            // Find odds for this sub-event
            Odd odd = oddsMap.get(subEvent.getId());
            BookMaker bookMaker = BookMakerMapper.getBookmakerName(subEvent.getBookmakerId());

            log.info("{} {} Sub-event {} bookmaker: {} (ID: {})",
                    EMOJI_INFO, EMOJI_TRANSFORM, subEvent.getId(), bookMaker, subEvent.getBookmakerId());

            BigDecimal oddsValue = BigDecimal.valueOf(2.00); // Default
            BigDecimal previousOdds = null;
            Boolean initiator = false;
            LocalDateTime updated = null;
            String index = "";


            MarketOutcome marketOutcome = null;

            if (odd != null) {
                log.trace("{} {} Found odds for sub-event {}: value={}, prev={}",
                        EMOJI_SUCCESS, EMOJI_TRANSFORM, subEvent.getId(), odd.getValue(), odd.getPrev());

                if (odd.getValue() != null && odd.getValue().compareTo(BigDecimal.ZERO) > 0) {
                    oddsValue = odd.getValue();
                    log.trace("{} {} Using odds value: {}", EMOJI_SUCCESS, EMOJI_TRANSFORM, oddsValue);
                } else {
                    log.debug("{} {} Odds masked for sub_event_id: {} (bookmaker: {})",
                            EMOJI_WARNING, EMOJI_TRANSFORM, subEvent.getId(), subEvent.getBookmakerId());
                }

                previousOdds = odd.getPrev();
                initiator = odd.getInitiator();
                index = odd.getIndex();

                if (!bookMaker.equals(BookMaker._1WIN)) {
//                    log.info("skip oneWin crumb by using the previous crumb");
                    crumbs = odd.getCrumbs();

                }

                log.info("{} {} {} Extracting crumbs for sub-event {} (bookmaker: {})...",
                        EMOJI_CRUMBS, EMOJI_MARKET, EMOJI_INFO, subEvent.getId(), bookMaker);

                if (crumbs != null && !crumbs.isEmpty()) {
                    log.debug("{} {} Crumbs found: {} {}", EMOJI_SUCCESS, EMOJI_CRUMBS, crumbs, bookMaker);

                    oid = crumbs.get("oid");
                    log.trace("{} {} Extracted oid from crumbs: {}", EMOJI_CRUMBS, EMOJI_INFO, oid);


                    log.info("{} {} Attempting to get market outcome for bookmaker: {}",
                            EMOJI_MARKET, EMOJI_INFO, bookMaker);

                    try {
                        marketOutcome = getMarketOutcome(bookMaker, crumbs);

                        if (marketOutcome != null) {
                            marketType = marketOutcome.getName();
                            if (bookMaker.equals(BookMaker._1WIN)) {
                                outCome = marketOutcome.getOutcome(OppositeOutcomeMapper.getOppositeKey(oid));

                            }else {
                                outCome = marketOutcome.getOutcome(oid);
                            }


                            log.info("{} {} {} Market outcome resolved: marketType='{}', outcome='{}'",
                                    EMOJI_SUCCESS, EMOJI_MARKET, EMOJI_CRUMBS, marketType, outCome);
                        } else {
                            log.warn("{} {} {} Market outcome returned null for bookmaker: {}",
                                    EMOJI_WARNING, EMOJI_MARKET, EMOJI_CRUMBS, bookMaker);
                        }
                    } catch (Exception e) {
                        log.error("{} {} {} Failed to get market outcome for bookmaker {}: {}",
                                EMOJI_ERROR, EMOJI_MARKET, EMOJI_CRUMBS, bookMaker, e.getMessage(), e);
                    }
                } else {
                    log.warn("{} {} No crumbs found for sub-event {} (bookmaker: {})",
                            EMOJI_WARNING, EMOJI_CRUMBS, subEvent.getId(), bookMaker);
                }

                if (odd.getUpdated() != null) {
                    updated = parseTimestamp(odd.getUpdated(), "yyyy-MM-dd HH:mm:ss");
                    log.trace("{} {} Parsed updated timestamp: {}", EMOJI_INFO, EMOJI_TRANSFORM, updated);
                }
            } else {
                log.warn("{} {} No odds found for sub_event_id: {}", EMOJI_WARNING, EMOJI_TRANSFORM, subEvent.getId());
            }

            OutcomeData outcome = OutcomeData.builder()
                    .subEventId(subEvent.getId())
                    .odds(oddsValue)
                    .previousOdds(previousOdds)
                    .initiator(initiator)
                    .bookmakerId(subEvent.getBookmakerId())
                    .bookmakerName(BookMakerMapper.getBookmakerName(subEvent.getBookmakerId()))
                    .sport(subEvent.getSport())
                    .league(subEvent.getLeague())
                    .marketType(marketType)
                    .team1(subEvent.getTeam1())
                    .team2(subEvent.getTeam2())
                    .progress(subEvent.getProgress())
                    .originalId(subEvent.getOriginalId())
                    .reordered(subEvent.getReordered())
                    .outComeName(bookMaker == BookMaker._1WIN ?
                            oneWinOutcomeStyle(outCome, subEvent.getTeam1(), subEvent.getTeam2()) : outCome)
                    .updated(updated)
                    .build();

            outcomes.add(outcome);
            log.trace("{} {} Outcome created: bookmaker={}, odds={}, marketType='{}', outcomeName='{}'",
                    EMOJI_SUCCESS, EMOJI_TRANSFORM, outcome.getBookmakerName(), outcome.getOdds(),
                    outcome.getMarketType(), outcome.getOutComeName());
        }

        // Parse timestamps
        LocalDateTime createdTime = parseTimestamp(created, "yyyy-MM-dd HH:mm:ss");
        LocalDateTime matchStart = parseTimestamp(event.getStart(), "yyyy-MM-dd HH:mm");

        log.trace("{} {} Parsed timestamps: created={}, matchStart={}",
                EMOJI_INFO, EMOJI_TRANSFORM, createdTime, matchStart);

        // Build ParsedArbitrageData from Event (source of truth)
        ParsedArbitrageData parsed = ParsedArbitrageData.builder()
                .arbId(arbId)
                .eventId(event.getId())
                .profitPercentage(profitPercentage)
                .roi(roi)
                .generalMarketType(marketType)
                .generalOutcomeName(outCome)
                .groupsIds(item != null ? item.getGroupsIds() : null)
                .sportId(event.getSportId())
                .sportName(BookMakerMapper.getSportName(event.getSportId()))
                .league(event.getLeague())
                .team1(event.getTeam1())
                .team2(event.getTeam2())
                .matchStart(matchStart)
                .created(createdTime)
                .isLive(isLive)
                .progress(outcomes.get(0).getProgress())
                .outcomes(outcomes)
                .build();

        log.debug("{} {} {} Converted event {} to arb: {} ({}) vs {} ({}) | {}% profit | marketType='{}' | outcome='{}'",
                EMOJI_SUCCESS, EMOJI_TRANSFORM, EMOJI_MARKET, event.getId(),
                outcomes.get(0).getBookmakerName(), outcomes.get(0).getOdds(),
                outcomes.get(1).getBookmakerName(), outcomes.get(1).getOdds(),
                profitPercentage, marketType, outCome);

        return parsed;
    }

    private String oneWinOutcomeStyle(String outCome, String homeTeam, String awayTeam) {
        if (outCome == null) {
            return null;
        }

        String trimmed = outCome.trim();

        // Exact match (fast path)
        if (homeTeam != null && trimmed.equalsIgnoreCase("home")) {
            return homeTeam;
        }
        if (awayTeam != null && trimmed.equalsIgnoreCase("away")) {
            return awayTeam;
        }

        // Partial replacement (case-insensitive)
        String result = outCome;

        if (homeTeam != null) {
            result = replaceIgnoreCase(result, "home", homeTeam);
        }

        if (awayTeam != null) {
            result = replaceIgnoreCase(result, "away", awayTeam);
        }

        return result;
    }

    private String replaceIgnoreCase(String source, String target, String replacement) {
        StringBuilder sb = new StringBuilder(source.length());
        String lowerSource = source.toLowerCase();
        String lowerTarget = target.toLowerCase();

        int fromIndex = 0;
        int index;

        while ((index = lowerSource.indexOf(lowerTarget, fromIndex)) != -1) {
            sb.append(source, fromIndex, index);
            sb.append(replacement);
            fromIndex = index + target.length();
        }

        sb.append(source.substring(fromIndex));
        return sb.toString();
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

                if (mid == null || spec == null) {
                    log.error("{} {} {} Missing required crumbs for oneWin: mid={}, spec={}",
                            EMOJI_ERROR, EMOJI_MARKET, EMOJI_CRUMBS, mid, spec);
                    throw new IllegalArgumentException("Missing required crumbs for SPORTYBET: mid, spec");
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

    private LocalDateTime parseTimestamp(String timestamp, String pattern) {
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
                .confidenceScore(calculateConfidenceScore(
                        data.getProfitPercentage() != null ? data.getProfitPercentage() : BigDecimal.ZERO,
                        data.getCreated() != null
                                ? Duration.between(data.getCreated(), LocalDateTime.now()).getSeconds()
                                : 0L))
                .outcomes(new ArrayList<>())
                .build();

        try {
            arb.setRawData(objectMapper.writeValueAsString(data));
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
                    .progress(outcomeData.getProgress())
                    .reordered(outcomeData.getReordered())
                    .initiator(outcomeData.getInitiator())
                    .build();
            arb.addOutcome(outcome);
        }

        return arb;
    }

    private int saveArbitrageOpportunitiesConcurrent(List<ArbitrageOpportunity> opportunities) {
        log.debug("{} {} Processing {} opportunities for database save...",
                EMOJI_SAVE, EMOJI_INFO, opportunities.size());

        List<CompletableFuture<SaveResult>> futures = opportunities.stream()
                .map(opportunity -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return saveOrUpdateSingleOpportunity(opportunity);
                    } catch (Exception e) {
                        log.warn("{} {} Failed to save opportunity: {}",
                                EMOJI_WARNING, EMOJI_SAVE, e.getMessage());
                        return SaveResult.SKIPPED;
                    }
                }, processingExecutor))
                .toList();

        List<SaveResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long savedCount = results.stream().filter(r -> r == SaveResult.SAVED).count();
        long updatedCount = results.stream().filter(r -> r == SaveResult.UPDATED).count();
        long skippedCount = results.stream().filter(r -> r == SaveResult.SKIPPED).count();

        log.info("{} {} {} Database save completed: {} new, {} updated, {} skipped",
                EMOJI_SUCCESS, EMOJI_CONCURRENT, EMOJI_SAVE, savedCount, updatedCount, skippedCount);
        return (int) (savedCount + updatedCount);
    }

    private SaveResult saveOrUpdateSingleOpportunity(ArbitrageOpportunity opportunity) {
        if (opportunity.getExternalId() != null) {
            Optional<ArbitrageOpportunity> existing = arbitrageRepository.findByExternalId(opportunity.getExternalId());
            if (existing.isPresent()) {
                ArbitrageOpportunity existingArb = existing.get();

                if (hasSignificantChanges(existingArb, opportunity)) {
                    // Significant change: Delete old arb completely and save as new
                    log.debug("{} Significant changes detected for arb {} (age: {}), expiring old and creating new",
                            EMOJI_WARNING, opportunity.getExternalId(), existingArb.getAgeFormatted());

                    // Mark old arb as expired (outcomes will be cascade deleted via orphanRemoval)
                    existingArb.setStatus(ArbStatus.EXPIRED);
                    existingArb.setExpiredAt(LocalDateTime.now());
                    existingArb.calculateAge(); // Final age calculation
                    arbitrageRepository.save(existingArb);

                    // Delete the expired arb and its outcomes
                    arbitrageRepository.delete(existingArb);

                    // Recalculate confidence score for new opportunity
                    recalculateConfidenceScore(opportunity);

                    // Age will be calculated in @PrePersist (will be 0 for new arb)
                    arbitrageRepository.save(opportunity);
                    return SaveResult.SAVED;
                } else {
                    // No significant change: Keep existing arb and outcomes, just update metadata
                    log.debug("{} No significant changes for arb {} (age: {}), updating metadata only",
                            EMOJI_INFO, opportunity.getExternalId(), existingArb.getAgeFormatted());

                    existingArb.setLastCheckedAt(LocalDateTime.now());

                    // Update confidence score based on current age
                    recalculateConfidenceScore(existingArb);

                    // Age will be recalculated in @PreUpdate
                    arbitrageRepository.save(existingArb);
                    return SaveResult.UPDATED;
                }
            }
        }

        // New arb: Calculate initial confidence score
        recalculateConfidenceScore(opportunity);
        // Age will be calculated in @PrePersist (will be 0)
        arbitrageRepository.save(opportunity);
        return SaveResult.SAVED;
    }

    /**
     * Recalculate confidence score based on current profit and age
     */
    private void recalculateConfidenceScore(ArbitrageOpportunity arb) {
        // Use current age (real-time)
        long ageSeconds = arb.getCurrentAge();

        BigDecimal profit = arb.getProfitPercentage() != null ? arb.getProfitPercentage() : BigDecimal.ZERO;
        BigDecimal newScore = calculateConfidenceScore(profit, ageSeconds);

        arb.setConfidenceScore(newScore);

        log.debug("{} Updated confidence score for arb {}: {} (age: {}, profit: {}%)",
                EMOJI_INFO, arb.getExternalId(), newScore, arb.getAgeFormatted(), profit);
    }

    private enum SaveResult {
        SAVED, UPDATED, SKIPPED
    }

    private boolean hasSignificantChanges(ArbitrageOpportunity existing, ArbitrageOpportunity newData) {
        Set<Integer> existingBookmakers = existing.getOutcomes().stream()
                .map(ArbOutcome::getBookmakerId)
                .collect(Collectors.toSet());
        Set<Integer> newBookmakers = newData.getOutcomes().stream()
                .map(ArbOutcome::getBookmakerId)
                .collect(Collectors.toSet());

        if (!existingBookmakers.equals(newBookmakers)) return true;

        BigDecimal threshold = new BigDecimal("0.05");
        for (ArbOutcome existingOutcome : existing.getOutcomes()) {
            ArbOutcome matchingNew = newData.getOutcomes().stream()
                    .filter(o -> o.getBookmakerId().equals(existingOutcome.getBookmakerId()))
                    .findFirst()
                    .orElse(null);
            if (matchingNew != null) {
                BigDecimal diff = existingOutcome.getOdds().subtract(matchingNew.getOdds()).abs();
                if (diff.compareTo(threshold) > 0) return true;
            }
        }
        return false;
    }

    private BigDecimal calculateConfidenceScore(BigDecimal profit, Long ageSeconds) {
        BigDecimal score = BigDecimal.valueOf(100);
        if (profit.compareTo(BigDecimal.valueOf(2)) < 0) {
            score = score.multiply(BigDecimal.valueOf(0.7));
        }
        if (ageSeconds > 300) {
            score = score.multiply(BigDecimal.valueOf(0.5));
        } else if (ageSeconds > 60) {
            score = score.multiply(BigDecimal.valueOf(0.8));
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private void logStatistics() {
        try {
            long activeCount = arbitrageRepository.countActiveArbs();
            BigDecimal avgProfit = arbitrageRepository.getAverageProfitPercentage();
            log.info("{} {} Current Statistics:", EMOJI_STATS, EMOJI_INFO);
            log.info("{} Active Arbs: {}", EMOJI_STATS, activeCount);
            log.info("{} Average Profit: {}%", EMOJI_STATS,
                    avgProfit != null ? avgProfit.setScale(2, RoundingMode.HALF_UP) : "N/A");
        } catch (Exception e) {
            log.debug("Could not retrieve statistics: {}", e.getMessage());
        }
    }

    public boolean isPollingActive() {
        return isRunning;
    }


}