package com.mouse.bet.mapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility Mapper class for mapping opposite outcome keys in 2-way markets.
 * For markets with exactly 2 outcomes (e.g., Home/Away, Over/Under, Yes/No),
 * this class provides the opposite key mapping.
 */
public class OppositeOutcomeMapper {

    private static final Map<String, String> OPPOSITE_KEYS = new HashMap<>();

    static {
        // Winner/Draw No Bet markets (Home <-> Away)
        addOpposite("4", "5");

        // 1X2 markets (Home <-> Away, Draw is neutral)
        addOpposite("1", "3");

        // Double Chance markets
        addOpposite("9", "11");  // Home or Draw <-> Draw or Away
        // Note: "10" (Home or Away) has no direct opposite

        // Over/Under markets
        addOpposite("12", "13");

        // Odd/Even markets
        addOpposite("70", "72");

        // Yes/No markets (BTTS, Clean Sheet, etc.)
        addOpposite("74", "76");

        // First/Second/Third Goal markets (Home <-> Away, None is neutral)
        addOpposite("6", "8");

        // Asian Handicap markets (Home <-> Away)
        addOpposite("1714", "1715");

        // European Handicap markets (Home <-> Away, Draw is neutral)
        addOpposite("1711", "1713");

        // Highest Scoring Half
        addOpposite("436", "438");  // 1st half <-> 2nd half

        // Half Time/Full Time outcomes
        addOpposite("418", "434");  // Home/Home <-> Away/Away
        addOpposite("420", "432");  // Home/Draw <-> Away/Draw
        addOpposite("422", "430");  // Home/Away <-> Away/Home

        // 1X2 & Over/Under combo
        addOpposite("794", "802");  // Home & Under <-> Away & Under
        addOpposite("796", "804");  // Home & Over <-> Away & Over

        // 1X2 & GG/NG combo
        addOpposite("78", "86");    // Home & yes <-> Away & yes
        addOpposite("80", "88");    // Home & no <-> Away & no

        // Double Chance & Over/Under combo
        addOpposite("1724", "1726");  // Home/Draw & Under <-> Draw/Away & Under
        addOpposite("1727", "1729");  // Home/Draw & Over <-> Draw/Away & Over

        // Double Chance & GG/NG combo
        addOpposite("1718", "1722");  // Home/Draw & Yes <-> Draw/Away & Yes
        addOpposite("1719", "1723");  // Home/Draw & No <-> Draw/Away & No

        // Excluded/Goal Bounds outcomes
//        addOpposite("0", "55");   // 0 goals <-> 5+ goals (full match)
//        addOpposite("1", "45");   // 0-1 <-> 4-5+
//        addOpposite("11", "35");  // 1 <-> 3-5+
//        addOpposite("22", "34");  // 2 <-> 3-4

//        // Correct Score outcomes (selected opposites)
//        addOpposite("274", "322");  // 0:0 <-> 4:4
//        addOpposite("276", "314");  // 1:0 <-> 0:4
//        addOpposite("278", "304");  // 2:0 <-> 0:3
//        addOpposite("280", "294");  // 3:0 <-> 0:2
//        addOpposite("282", "284");  // 4:0 <-> 0:1
//        addOpposite("286", "322");  // 1:1 <-> 4:4
//        addOpposite("288", "318");  // 2:1 <-> 2:4
//        addOpposite("290", "308");  // 3:1 <-> 2:3
//        addOpposite("292", "296");  // 4:1 <-> 1:2
//        addOpposite("298", "310");  // 2:2 <-> 3:3
//        addOpposite("300", "306");  // 3:2 <-> 1:3
//
//        // Winning Margin
//        addOpposite("sr:winning_margin:6+:120", "sr:winning_margin:6+:121");  // Home by 6+ <-> Away by 6+
//        addOpposite("1002", "1003");  // Home by 3+ <-> Away by 3+
//
//        // Table Tennis - Correct Score (Best of 5)
//        addOpposite("sr:correct_score:bestof:5:8", "sr:correct_score:bestof:5:13");    // 3:0 <-> 0:3
//        addOpposite("sr:correct_score:bestof:5:9", "sr:correct_score:bestof:5:12");    // 3:1 <-> 1:3
//        addOpposite("sr:correct_score:bestof:5:10", "sr:correct_score:bestof:5:11");   // 3:2 <-> 2:3
//
//        // Exact Goals/Games outcomes
//        addOpposite("sr:exact_goals:5+:1336", "sr:exact_goals:5+:1341");  // 0 <-> 5+
//        addOpposite("sr:exact_goals:5+:1337", "sr:exact_goals:5+:1340");  // 1 <-> 4
//        addOpposite("sr:exact_goals:5+:1338", "sr:exact_goals:5+:1339");  // 2 <-> 3
//
//        addOpposite("sr:exact_goals:3+:88", "sr:exact_goals:3+:91");      // 0 <-> 3+
//        addOpposite("sr:exact_goals:3+:89", "sr:exact_goals:3+:90");      // 1 <-> 2
//
//        addOpposite("sr:exact_games:bestof:5:39", "sr:exact_games:bestof:5:41");  // 3 games <-> 5 games
//
//        // Decided by Extra Points
//        addOpposite("sr:decided_by_extra_points:bestof:5:53", "sr:decided_by_extra_points:bestof:5:58");  // 0 <-> 5
//        addOpposite("sr:decided_by_extra_points:bestof:5:54", "sr:decided_by_extra_points:bestof:5:57");  // 1 <-> 4
//        addOpposite("sr:decided_by_extra_points:bestof:5:55", "sr:decided_by_extra_points:bestof:5:56");  // 2 <-> 3
//
//        // First Half Correct Score
//        addOpposite("462", "466");  // 0:0 <-> 2:2
//        addOpposite("468", "476");  // 1:0 <-> 0:2
//        addOpposite("470", "474");  // 2:0 <-> 0:1
//        addOpposite("472", "478");  // 2:1 <-> 1:2

        // Goal Bounds variations
//        addOpposite("0", "33");     // 0 <-> 3+ (for away/home specific)
//        addOpposite("1", "23");     // 0-1 <-> 2-3+
//        addOpposite("11", "13");    // 1 <-> 1-3+
    }

    /**
     * Helper method to add bidirectional opposite mapping
     */
    private static void addOpposite(String key1, String key2) {
        OPPOSITE_KEYS.put(key1, key2);
        OPPOSITE_KEYS.put(key2, key1);
    }

    /**
     * Get the opposite outcome key for a given key.
     *
     * @param key the outcome key
     * @return the opposite outcome key, or null if no opposite exists
     */
    public static String getOppositeKey(String key) {
        return OPPOSITE_KEYS.get(key);
    }

    /**
     * Check if a key has an opposite outcome.
     *
     * @param key the outcome key
     * @return true if an opposite exists, false otherwise
     */
    public static boolean hasOpposite(String key) {
        return OPPOSITE_KEYS.containsKey(key);
    }

    /**
     * Check if two keys are opposites of each other.
     *
     * @param key1 first outcome key
     * @param key2 second outcome key
     * @return true if they are opposites, false otherwise
     */
    public static boolean areOpposites(String key1, String key2) {
        return key2.equals(OPPOSITE_KEYS.get(key1));
    }
}