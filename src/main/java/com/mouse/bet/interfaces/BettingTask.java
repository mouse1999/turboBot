package com.mouse.bet.interfaces;

import com.mouse.bet.enums.BookMaker;

public interface BettingTask {

    /* ===================== BOOKMAKER ===================== */

    BookMaker bookmaker();      // e.g. "Bet365", "1xBet"
    Integer bookmakerId();   // internal / DB ID
    String marketType();
    String outcome();

    double expectedOdds();
    double minOdds();
    double maxOdds();

    double stakeAmount();
    String leagueName();

    String homeTeam();
    String awayTeam();
    String taskId();
}
