package com.mouse.bet.interfaces;

import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.enums.Sport;
import lombok.Data;


public interface BettingTask {

    /* ===================== BOOKMAKER ===================== */

    BookMaker bookmaker();      // e.g. "Bet365", "1xBet"
    Integer bookmakerId();   // internal / DB ID
    String marketType();
    String outcome();
    String bookmakerUrl();

    double expectedOdds();
    double minOdds();
    double maxOdds();

    double stakeAmount();
    String leagueName();

    String homeTeam();
    String awayTeam();
    String taskId();

    String sport();
}
