package com.mouse.bet.transformation;


import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.enums.Sport;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to map Breaking-Bet integer IDs to our internal enums.
 * This acts as a bridge until we discover more bookmakers/sports.
 */
public class BookMakerMapper {

    private static final Map<Integer, BookMaker> BOOKMAKER_BY_ID = new HashMap<>();

    static {
        BOOKMAKER_BY_ID.put(21, BookMaker._1XBET);
        BOOKMAKER_BY_ID.put(110, BookMaker.BET365);
        BOOKMAKER_BY_ID.put(3, BookMaker.WILLIAM_HILL);
        BOOKMAKER_BY_ID.put(6, BookMaker.UNIBET);
        BOOKMAKER_BY_ID.put(10, BookMaker.PINNACLE);
        BOOKMAKER_BY_ID.put(14, BookMaker.BETFAIR);
        BOOKMAKER_BY_ID.put(23, BookMaker.MARATHON_BET);
        BOOKMAKER_BY_ID.put(31, BookMaker._888SPORT);
        BOOKMAKER_BY_ID.put(91, BookMaker.BWIN);
        BOOKMAKER_BY_ID.put(36, BookMaker.BETWAY);
        BOOKMAKER_BY_ID.put(39, BookMaker.LADBROKES);
        BOOKMAKER_BY_ID.put(48, BookMaker.POINTSBET);
        BOOKMAKER_BY_ID.put(49, BookMaker.BETVICTOR);
        BOOKMAKER_BY_ID.put(53, BookMaker.CORAL);
        BOOKMAKER_BY_ID.put(82, BookMaker._22BET);
        BOOKMAKER_BY_ID.put(79, BookMaker._1WIN);
        BOOKMAKER_BY_ID.put(84, BookMaker.MELBET);
        BOOKMAKER_BY_ID.put(85, BookMaker.PARIMATCH);
        BOOKMAKER_BY_ID.put(89, BookMaker.LEOVEGAS);
        BOOKMAKER_BY_ID.put(92, BookMaker.BETANO);
        BOOKMAKER_BY_ID.put(93, BookMaker.BETFRED);
        BOOKMAKER_BY_ID.put(94, BookMaker.MATCHBOOK);
         BOOKMAKER_BY_ID.put(80, BookMaker.MSPORT);
         BOOKMAKER_BY_ID.put(43, BookMaker.SPORTYBET);
        BOOKMAKER_BY_ID.put(33, BookMaker.BET9JA);
    }

    private static final Map<Integer, Sport> SPORT_BY_ID = new HashMap<>();

    static {
        SPORT_BY_ID.put(1, Sport.FOOTBALL);
        SPORT_BY_ID.put(5, Sport.BASKETBALL);
        SPORT_BY_ID.put(3, Sport.BASEBALL);
        SPORT_BY_ID.put(0, Sport.ICE_HOCKEY);
        SPORT_BY_ID.put(4, Sport.VOLLEYBALL);
        SPORT_BY_ID.put(16, Sport.HANDBALL);
        SPORT_BY_ID.put(11, Sport.TABLE_TENNIS);
        SPORT_BY_ID.put(6, Sport.TENNIS);
        SPORT_BY_ID.put(18, Sport.AMERICAN_FOOTBALL);
        SPORT_BY_ID.put(91, Sport.E_SPORTS);
    }

    /**
     * Returns the BookMaker enum for a Breaking-Bet ID, or null if unknown.
     */
    public static BookMaker getBookmaker(Integer id) {
        return BOOKMAKER_BY_ID.get(id);
    }

    /**
     * Returns display name with fallback (keeps your original behavior)
     */
    public static BookMaker getBookmakerName(Integer id) {
        BookMaker bm = BOOKMAKER_BY_ID.get(id);
        return bm != null ? bm: BookMaker.UNKNOWN;
    }

    /**
     * Returns the Sport enum for a Breaking-Bet ID, or null if unknown.
     */
    public static Sport getSport(Integer id) {
        return SPORT_BY_ID.get(id);
    }

    /**
     * Returns display name with fallback
     */
    public static String getSportName(Integer id) {
        Sport sport = SPORT_BY_ID.get(id);
        return sport != null ? sport.getDisplayName() : "Sport_" + id;
    }

    /**
     * Returns a defensive copy of all known bookmaker mappings
     */
    public static Map<Integer, BookMaker> getAllBookmakers() {
        return new HashMap<>(BOOKMAKER_BY_ID);
    }

    /**
     * Returns a defensive copy of all known sport mappings
     */
    public static Map<Integer, Sport> getAllSports() {
        return new HashMap<>(SPORT_BY_ID);
    }
}