package com.mouse.bet.interfaces;

public interface BettingTask {
    String getMarketType();
    String getOutcome();
    double getExpectedOdds();
    double getMinOdds();
    double getMaxOdds();
    double getStakeAmount();
    String getHomeTeam();
    String getAwayTeam();
    String getTaskId();
}