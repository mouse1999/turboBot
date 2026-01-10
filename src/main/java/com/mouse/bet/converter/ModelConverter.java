package com.mouse.bet.converter;

import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.orchestrator.model.BetLeg;

/**
 * Converter between persistence entity (ArbOutcome) and orchestrator model (BetLeg/BettingTask)
 */
public class ModelConverter {

    /**
     * Converts an ArbOutcome entity into a BetLeg record that implements BettingTask.
     *
     * @param arbOutcome the arbitrage outcome entity to convert
     * @return BetLeg record or null if input is null or critical fields are missing
     */
    public static BetLeg convertFromArbOutcome(ArbOutcome arbOutcome) {
        if (arbOutcome == null) {
            return null;
        }

        // Safety check for required fields
        if (arbOutcome.getBookmakerId() == null || arbOutcome.getBookmakerName() == null) {
            return null;
        }

        // Get parent arbitrage external ID (taskId)
        String taskId = (arbOutcome.getArbitrage() != null)
                ? arbOutcome.getArbitrage().getExternalId()
                : null;

        // Extract bookmaker enum (should match what you have in DB)
        BookMaker bookmakerEnum = arbOutcome.getBookmakerName();

        // Use BigDecimal → double conversion safely
        double expectedOdds = arbOutcome.getOdds() != null
                ? arbOutcome.getOdds().doubleValue()
                : 0.0;

        // Reasonable fallback for min/max odds (can be made configurable)
        double minOdds = expectedOdds * 0.95;  // -10%
        double maxOdds = expectedOdds * 1.05;  // +10%

        double stakeAmount = arbOutcome.getStake() != null
                ? arbOutcome.getStake().doubleValue()
                : 0.0;



        return BetLeg.builder()
                .bookmaker(bookmakerEnum)
                .bookmakerId(arbOutcome.getBookmakerId())
                .marketType(arbOutcome.getMarketType())
                .outcome(arbOutcome.getOutcomeName() != null ? arbOutcome.getOutcomeName() : "Unknown")
                .expectedOdds(expectedOdds)
                .minOdds(minOdds)
                .maxOdds(maxOdds)
                .stakeAmount(stakeAmount)
                .leagueName(arbOutcome.getLeagueName() != null ? arbOutcome.getLeagueName() : "Unknown")
                .homeTeam(arbOutcome.getHomeTeam())
                .awayTeam(arbOutcome.getAwayTeam())
                .taskId(taskId)       // From parent ArbitrageOpportunity.externalId
                .build();
    }




}