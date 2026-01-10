package com.mouse.bet.orchestrator.model;

import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.interfaces.BettingTask;
import lombok.Builder;

@Builder
public record BetLeg(BookMaker bookmaker, Integer bookmakerId, String marketType, String outcome,
                     double expectedOdds, double minOdds, double maxOdds, double stakeAmount,
                     String leagueName, String homeTeam, String awayTeam,
                     String taskId) implements BettingTask {
//taskId is the arbitrageopportunity externalId

}
