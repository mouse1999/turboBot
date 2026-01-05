package com.mouse.bet.ingestion;

import com.mouse.bet.client.BreakingBetClient;
import com.mouse.bet.dto.*;
import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.monitoring.ArbitrageDataValidator;
import com.mouse.bet.repository.ArbOutcomeRepository;
import com.mouse.bet.repository.ArbitrageRepository;
import com.mouse.bet.util.BookMakerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for ingesting arbitrage data from Breaking-Bet API
 * Uses normalized entity design with separate outcome entities
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

    private final BreakingBetClient breakingBetClient;
    private final ArbitrageRepository arbitrageRepository;
    private final ArbOutcomeRepository arbOutcomeRepository;
    private final ArbitrageDataValidator validator;
    private final ObjectMapper objectMapper;

    /**
     * Scheduled task to ingest live arbitrage opportunities
     */
    @Scheduled(cron = "${arb.fetch.schedule.live:*/30 * * * * *}")
    @Transactional
    public void ingestLiveArbs() {
        log.info("{} {} Starting live arbs ingestion...", EMOJI_INGESTION, EMOJI_INFO);

        try {
            long startTime = System.currentTimeMillis();

            BreakingBetResponse response = breakingBetClient.fetchLiveArbsAsObject();
            List<ParsedArbitrageData> parsedData = enrichArbsWithEventData(response, true);
            List<ArbitrageOpportunity> opportunities = transformToEntities(parsedData);
            int saved = saveArbitrageOpportunities(opportunities);

            long duration = System.currentTimeMillis() - startTime;

            log.info("{} {} Live arbs ingestion completed: {} opportunities saved in {}ms",
                    EMOJI_SUCCESS, EMOJI_INGESTION, saved, duration);

            logStatistics();

        } catch (IOException e) {
            log.error("{} {} Failed to fetch live arbs: {}",
                    EMOJI_ERROR, EMOJI_INGESTION, e.getMessage(), e);
        } catch (Exception e) {
            log.error("{} {} Unexpected error during live arbs ingestion: {}",
                    EMOJI_ERROR, EMOJI_INGESTION, e.getMessage(), e);
        }
    }

    /**
     * Scheduled task to ingest prematch arbitrage opportunities
     */
    @Scheduled(cron = "${arb.fetch.schedule.prematch:0 * * * * *}")
    @Transactional
    public void ingestPrematchArbs() {
        log.info("{} {} Starting prematch arbs ingestion...", EMOJI_INGESTION, EMOJI_INFO);

        try {
            long startTime = System.currentTimeMillis();

            BreakingBetResponse response = breakingBetClient.fetchPrematchArbsAsObject();
            List<ParsedArbitrageData> parsedData = enrichArbsWithEventData(response, false);
            List<ArbitrageOpportunity> opportunities = transformToEntities(parsedData);
            int saved = saveArbitrageOpportunities(opportunities);

            long duration = System.currentTimeMillis() - startTime;

            log.info("{} {} Prematch arbs ingestion completed: {} opportunities saved in {}ms",
                    EMOJI_SUCCESS, EMOJI_INGESTION, saved, duration);

            logStatistics();

        } catch (IOException e) {
            log.error("{} {} Failed to fetch prematch arbs: {}",
                    EMOJI_ERROR, EMOJI_INGESTION, e.getMessage(), e);
        } catch (Exception e) {
            log.error("{} {} Unexpected error during prematch arbs ingestion: {}",
                    EMOJI_ERROR, EMOJI_INGESTION, e.getMessage(), e);
        }
    }

    /**
     * Manual ingestion methods
     */
    @Transactional
    public int ingestLiveArbsManual() throws IOException {
        log.info("{} {} Manual live arbs ingestion triggered", EMOJI_INGESTION, EMOJI_INFO);

        BreakingBetResponse response = breakingBetClient.fetchLiveArbsAsObject();
        List<ParsedArbitrageData> parsedData = enrichArbsWithEventData(response, true);
        List<ArbitrageOpportunity> opportunities = transformToEntities(parsedData);
        return saveArbitrageOpportunities(opportunities);
    }

    @Transactional
    public int ingestPrematchArbsManual() throws IOException {
        log.info("{} {} Manual prematch arbs ingestion triggered", EMOJI_INGESTION, EMOJI_INFO);

        BreakingBetResponse response = breakingBetClient.fetchPrematchArbsAsObject();
        List<ParsedArbitrageData> parsedData = enrichArbsWithEventData(response, false);
        List<ArbitrageOpportunity> opportunities = transformToEntities(parsedData);
        return saveArbitrageOpportunities(opportunities);
    }

    /**
     * Enrich arb items with event data
     */
    private List<ParsedArbitrageData> enrichArbsWithEventData(BreakingBetResponse response, boolean isLive) {
        log.info("{} {} Enriching {} arb items with event data...",
                EMOJI_TRANSFORM, EMOJI_INFO,
                response.getItems() != null ? response.getItems().size() : 0);

        // Validate response
        ArbitrageDataValidator.ValidationResult validationResult = validator.validateResponse(response);
        if (!validationResult.isValid()) {
            log.error("{} {} Response validation failed: {}",
                    EMOJI_ERROR, EMOJI_TRANSFORM, validationResult.getSummary());
            log.error("Validation details:\n{}", validationResult);
        } else if (validationResult.hasWarnings()) {
            log.warn("{} {} Response has warnings: {}",
                    EMOJI_WARNING, EMOJI_TRANSFORM, validationResult.getSummary());
        }

        List<ParsedArbitrageData> enrichedData = new ArrayList<>();

        if (response.getItems() == null || response.getEvents() == null) {
            log.warn("{} {} No items or events in response", EMOJI_WARNING, EMOJI_TRANSFORM);
            return enrichedData;
        }

        // Create lookup maps
        Map<String, Event> eventMap = response.getEvents().stream()
                .collect(Collectors.toMap(Event::getId, event -> event, (e1, e2) -> e1));

        Map<String, SubEvent> subEventMap = new HashMap<>();
        for (Event event : response.getEvents()) {
            if (event.getSubEvents() != null) {
                for (SubEvent subEvent : event.getSubEvents()) {
                    subEventMap.put(subEvent.getId(), subEvent);
                }
            }
        }

        // Process each arb item
        for (ArbItem item : response.getItems()) {
            try {
                ParsedArbitrageData parsed = enrichSingleArb(item, eventMap, subEventMap, isLive);
                if (parsed != null) {
                    enrichedData.add(parsed);
                }
            } catch (Exception e) {
                log.warn("{} {} Failed to enrich arb {}: {}",
                        EMOJI_WARNING, EMOJI_TRANSFORM, item.getId(), e.getMessage());
            }
        }

        log.info("{} {} Successfully enriched {} arbitrage opportunities",
                EMOJI_SUCCESS, EMOJI_TRANSFORM, enrichedData.size());

        return enrichedData;
    }

    /**
     * Enrich single arb - ONLY 2-way arbitrages
     */
    private ParsedArbitrageData enrichSingleArb(
            ArbItem item,
            Map<String, Event> eventMap,
            Map<String, SubEvent> subEventMap,
            boolean isLive) {

        Event event = eventMap.get(item.getEventId());
        if (event == null) {
            log.warn("{} {} Event not found for arb: {}",
                    EMOJI_WARNING, EMOJI_TRANSFORM, item.getId());
            return null;
        }

        if (item.getOdds() == null || item.getOdds().isEmpty()) {
            log.warn("{} {} No odds found for arb: {}",
                    EMOJI_WARNING, EMOJI_TRANSFORM, item.getId());
            return null;
        }

        // ONLY ACCEPT 2-WAY ARBITRAGES
        if (item.getOdds().size() != 2) {
            log.debug("{} {} Ignoring arb {} - has {} outcomes (only accepting 2-way)",
                    EMOJI_INFO, EMOJI_TRANSFORM, item.getId(), item.getOdds().size());
            return null;
        }

        ParsedArbitrageData.ParsedArbitrageDataBuilder builder = ParsedArbitrageData.builder()
                .arbId(item.getId())
                .eventId(item.getEventId())
                .profitPercentage(item.getValue())
                .roi(item.getRoi())
                .groupsIds(item.getGroupsIds())
                .sportId(event.getSportId())
                .sportName(BookMakerMapper.getSportName(event.getSportId()))
                .league(event.getLeague())
                .team1(event.getTeam1())
                .team2(event.getTeam2())
                .isLive(isLive);

        // Parse timestamps
        if (item.getCreated() != null) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                builder.created(LocalDateTime.parse(item.getCreated(), formatter));
            } catch (DateTimeParseException e) {
                log.debug("Could not parse created timestamp: {}", item.getCreated());
            }
        }

        if (event.getStart() != null) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                builder.matchStart(LocalDateTime.parse(event.getStart(), formatter));
            } catch (DateTimeParseException e) {
                log.debug("Could not parse match start time: {}", event.getStart());
            }
        }

        if (event.getSubEvents() != null && !event.getSubEvents().isEmpty()) {
            builder.progress(event.getSubEvents().get(0).getProgress());
        }

        // Process exactly 2 outcomes
        List<OutcomeData> outcomes = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            Odd odd = item.getOdds().get(i);
            SubEvent subEvent = subEventMap.get(odd.getSubEventId());

            if (subEvent == null) {
                log.error("{} {} Sub-event not found for sub_event_id: {}",
                        EMOJI_ERROR, EMOJI_TRANSFORM, odd.getSubEventId());
                return null;
            }

            BigDecimal oddsValue = odd.getValue();
            if (oddsValue == null || oddsValue.compareTo(BigDecimal.ZERO) == 0) {
                log.debug("{} Odds value is 0 for sub_event_id: {} (subscription required)",
                        EMOJI_INFO, odd.getSubEventId());
                oddsValue = BigDecimal.valueOf(2.00); // Placeholder
            }

            OutcomeData outcome = OutcomeData.builder()
                    .subEventId(odd.getSubEventId())
                    .odds(oddsValue)
                    .previousOdds(odd.getPrev())
                    .initiator(odd.getInitiator())
                    .bookmakerId(subEvent.getBookmakerId())
                    .bookmakerName(BookMakerMapper.getBookmakerName(subEvent.getBookmakerId()))
                    .sport(subEvent.getSport())
                    .progress(subEvent.getProgress())
                    .originalId(subEvent.getOriginalId())
                    .reordered(subEvent.getReordered())
                    .outcomeName("Side " + (i + 1))
                    .build();

            if (odd.getUpdated() != null) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    outcome.setUpdated(LocalDateTime.parse(odd.getUpdated(), formatter));
                } catch (DateTimeParseException e) {
                    log.debug("Could not parse updated timestamp: {}", odd.getUpdated());
                }
            }

            outcomes.add(outcome);
        }

        builder.outcomes(outcomes);

        log.debug("{} Enriched 2-way arb {} with bookmakers: {} vs {}",
                EMOJI_SUCCESS, item.getId(),
                outcomes.get(0).getBookmakerName(),
                outcomes.get(1).getBookmakerName());

        return builder.build();
    }

    /**
     * Transform parsed data to entities with normalized outcomes
     */
    private List<ArbitrageOpportunity> transformToEntities(List<ParsedArbitrageData> parsedDataList) {
        log.info("{} {} Transforming {} parsed arbs to entities...",
                EMOJI_TRANSFORM, EMOJI_INFO, parsedDataList.size());

        List<ArbitrageOpportunity> entities = new ArrayList<>();

        for (ParsedArbitrageData data : parsedDataList) {
            try {
                ArbitrageOpportunity entity = transformSingleToEntity(data);
                if (entity != null) {
                    entities.add(entity);
                }
            } catch (Exception e) {
                log.warn("{} {} Failed to transform arb {}: {}",
                        EMOJI_WARNING, EMOJI_TRANSFORM, data.getArbId(), e.getMessage());
            }
        }

        log.info("{} {} Transformed {} entities",
                EMOJI_SUCCESS, EMOJI_TRANSFORM, entities.size());

        return entities;
    }

    /**
     * Transform to normalized entity with separate outcomes
     */
    private ArbitrageOpportunity transformSingleToEntity(ParsedArbitrageData data) {
        if (!data.isTwoWay()) {
            log.debug("{} {} Ignoring non-2-way arb: {}",
                    EMOJI_INFO, EMOJI_TRANSFORM, data.getArbId());
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
//                .marketType("2-Way Market")
                .profitPercentage(data.getProfitPercentage() != null ?
                        data.getProfitPercentage().setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .roiPercentage(data.getRoi() != null ?
                        data.getRoi().setScale(4, RoundingMode.HALF_UP) : null)
                .status(ArbStatus.ACTIVE)
                .lastCheckedAt(LocalDateTime.now())
                .confidenceScore(calculateConfidenceScore(
                        data.getProfitPercentage() != null ? data.getProfitPercentage() : BigDecimal.ZERO,
                        data.getCreated() != null ?
                                java.time.Duration.between(data.getCreated(), LocalDateTime.now()).getSeconds() : 0L))
                .outcomes(new ArrayList<>())
                .build();

        // Store raw data
        try {
            arb.setRawData(objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            log.debug("Could not serialize raw data: {}", e.getMessage());
        }

        // Add outcomes (position-independent)
        BigDecimal totalStake = BigDecimal.valueOf(100);
        BigDecimal stakePerOutcome = totalStake.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        for (OutcomeData outcomeData : data.getOutcomes()) {
            ArbOutcome outcome = ArbOutcome.builder()
                    .bookmakerId(outcomeData.getBookmakerId())
                    .bookmakerName(outcomeData.getBookmakerName())
                    .outcomeName(outcomeData.getOutcomeName())
                    .odds(outcomeData.getOdds())
                    .previousOdds(outcomeData.getPreviousOdds())
                    .stake(stakePerOutcome)
                    .subEventId(outcomeData.getSubEventId())
                    .originalId(outcomeData.getOriginalId())
                    .sport(outcomeData.getSport())
                    .progress(outcomeData.getProgress())
                    .reordered(outcomeData.getReordered())
                    .initiator(outcomeData.getInitiator())
                    .build();

            arb.addOutcome(outcome);
        }

        log.debug("{} Created entity with {} outcomes: {} vs {} | {}% profit",
                EMOJI_SUCCESS, arb.getOutcomes().size(),
                arb.getOutcomes().get(0).getBookmakerName(),
                arb.getOutcomes().get(1).getBookmakerName(),
                data.getProfitPercentage());

        return arb;
    }

    /**
     * Save with intelligent update strategy for normalized design
     */
    private int saveArbitrageOpportunities(List<ArbitrageOpportunity> opportunities) {
        log.info("{} {} Saving {} opportunities to database...",
                EMOJI_SAVE, EMOJI_INFO, opportunities.size());

        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (ArbitrageOpportunity opportunity : opportunities) {
            try {
                if (opportunity.getExternalId() != null) {
                    Optional<ArbitrageOpportunity> existing =
                            arbitrageRepository.findByExternalId(opportunity.getExternalId());

                    if (existing.isPresent()) {
                        ArbitrageOpportunity existingArb = existing.get();

                        if (hasSignificantChanges(existingArb, opportunity)) {
                            // DELETE all outcomes connected to old arb
                            arbOutcomeRepository.deleteByArbitrageId(existingArb.getId());
                            log.debug("Deleted {} outcomes for expired arb {}",
                                    existingArb.getOutcomes().size(),
                                    existingArb.getExternalId());

                            // Mark old as expired
                            existingArb.setStatus(ArbStatus.EXPIRED);
                            existingArb.setExpiredAt(LocalDateTime.now());
                            arbitrageRepository.save(existingArb);

                            // Save new arb
                            arbitrageRepository.save(opportunity);
                            savedCount++;
                            log.debug("Arb {} changed significantly - saved as new",
                                    opportunity.getExternalId());
                        } else {
                            // Just update last checked
                            existingArb.setLastCheckedAt(LocalDateTime.now());
                            arbitrageRepository.save(existingArb);
                            updatedCount++;
                        }
                    } else {
                        // New arb
                        arbitrageRepository.save(opportunity);
                        savedCount++;
                    }
                } else {
                    arbitrageRepository.save(opportunity);
                    savedCount++;
                }
            } catch (Exception e) {
                log.warn("{} {} Failed to save opportunity: {}",
                        EMOJI_WARNING, EMOJI_SAVE, e.getMessage());
                skippedCount++;
            }
        }

        log.info("{} {} Database save completed: {} new, {} updated, {} skipped",
                EMOJI_SUCCESS, EMOJI_SAVE, savedCount, updatedCount, skippedCount);

        return savedCount + updatedCount;
    }

    /**
     * Check if arb has significant changes (bookmakers or odds changed)
     */
    private boolean hasSignificantChanges(ArbitrageOpportunity existing, ArbitrageOpportunity newData) {
        // Compare bookmaker IDs (position-independent)
        Set<Integer> existingBookmakers = existing.getOutcomes().stream()
                .map(ArbOutcome::getBookmakerId)
                .collect(Collectors.toSet());

        Set<Integer> newBookmakers = newData.getOutcomes().stream()
                .map(ArbOutcome::getBookmakerId)
                .collect(Collectors.toSet());

        if (!existingBookmakers.equals(newBookmakers)) {
            return true; // Different bookmakers
        }

        // Compare odds by matching bookmaker IDs
        BigDecimal threshold = new BigDecimal("0.05");

        for (ArbOutcome existingOutcome : existing.getOutcomes()) {
            ArbOutcome matchingNew = newData.getOutcomes().stream()
                    .filter(o -> o.getBookmakerId().equals(existingOutcome.getBookmakerId()))
                    .findFirst()
                    .orElse(null);

            if (matchingNew != null) {
                BigDecimal oddsDiff = existingOutcome.getOdds()
                        .subtract(matchingNew.getOdds())
                        .abs();

                if (oddsDiff.compareTo(threshold) > 0) {
                    return true; // Significant odds change
                }
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
            log.info("{}   Active Arbs: {}", EMOJI_STATS, activeCount);
            log.info("{}   Average Profit: {}%", EMOJI_STATS,
                    avgProfit != null ? avgProfit.setScale(2, RoundingMode.HALF_UP) : "N/A");
        } catch (Exception e) {
            log.debug("Could not retrieve statistics: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldData() {
        log.info("{} {} Starting cleanup of old arbitrage data...", EMOJI_INFO, EMOJI_SAVE);

        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            arbitrageRepository.deleteByStatusAndCreatedAtBefore(ArbStatus.EXPIRED, cutoff);

            log.info("{} {} Cleanup completed", EMOJI_SUCCESS, EMOJI_SAVE);
        } catch (Exception e) {
            log.error("{} {} Cleanup failed: {}", EMOJI_ERROR, EMOJI_SAVE, e.getMessage());
        }
    }
}