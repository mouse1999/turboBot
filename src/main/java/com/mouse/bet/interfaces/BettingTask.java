package com.mouse.bet.interfaces;

public interface BettingTask {

    /* ===================== BOOKMAKER ===================== */

    String getBookmaker();      // e.g. "Bet365", "1xBet"
    Integer getBookmakerId();   // internal / DB ID
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
