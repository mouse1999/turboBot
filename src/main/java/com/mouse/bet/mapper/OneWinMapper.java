package com.mouse.bet.mapper;

import com.mouse.bet.mapper.model.SimilarBookieMapper;

import java.util.HashMap;
import java.util.Map;

public class OneWinMapper extends SimilarBookieMapper {
    public OneWinMapper() {
        super("oneWin");
    }

    @Override
    public String buildKey(String marketId, String specifier) {
        if (specifier == null || specifier.isEmpty()) {
            return marketId;
        }
        return marketId + "-" + specifier;
    }

    @Override
    public void initializeMarkets() {
        // ───────────────────────────────────────────────
        // FOOTBALL MARKETS
        // ───────────────────────────────────────────────

        // 1X2 (Match Winner)
        Map<String, String> match1x2 = new HashMap<>();
        match1x2.put("1", "Home");
        match1x2.put("2", "Draw");
        match1x2.put("3", "Away");
        addMarket("1", null, "1X2", match1x2);

        // Double Chance
        Map<String, String> doubleChance = new HashMap<>();
        doubleChance.put("9", "Home or Draw");
        doubleChance.put("10", "Home or Away");
        doubleChance.put("11", "Draw or Away");
        addMarket("10", null, "Double Chance", doubleChance);

        // Draw No Bet
        Map<String, String> drawNoBet = new HashMap<>();
        drawNoBet.put("4", "Home");
        drawNoBet.put("5", "Away");
        addMarket("11", null, "Draw No Bet", drawNoBet);

        // Both Teams to Score (GG/NG)
        Map<String, String> btts = new HashMap<>();
        btts.put("74", "Yes");
        btts.put("76", "No");
        addMarket("29", null, "GG/NG", btts);

        // Odd/Even
        Map<String, String> oddEven = new HashMap<>();
        oddEven.put("70", "Odd");
        oddEven.put("72", "Even");
        addMarket("26", null, "Odd/Even", oddEven);

        // Goal Bounds - Home
        Map<String, String> goalBoundsHome = new HashMap<>();
        goalBoundsHome.put("0", "0");
        goalBoundsHome.put("1", "0-1");
        goalBoundsHome.put("2", "0-2");
        goalBoundsHome.put("11", "1");
        goalBoundsHome.put("12", "1-2");
        goalBoundsHome.put("13", "1-3+");
        goalBoundsHome.put("22", "2");
        goalBoundsHome.put("23", "2-3+");
        goalBoundsHome.put("33", "3+");
        addMarket("450002", null, "Goal Bounds - Home", goalBoundsHome);

        // 2nd Goal
        Map<String, String> secondGoal = new HashMap<>();
        secondGoal.put("6", "Home");
        secondGoal.put("7", "None");
        secondGoal.put("8", "Away");
        addMarket("8", "goalnr=2", "2nd Goal", secondGoal);

        // Rest of the Match (current score 0:1)
        Map<String, String> restOfMatch = new HashMap<>();
        restOfMatch.put("1", "Home");
        restOfMatch.put("2", "Draw");
        restOfMatch.put("3", "Away");
        addMarket("7", "score=0:1", "Rest of the Match (current score 0:1)", restOfMatch);

        // Football Over/Under and Handicaps
        generateFootballOverUnder();
        generateTeamOverUnder("19", "");
        generateTeamOverUnder("20", ""); //todo: include the names in ingestion
        generateFootballHandicaps();
        generateFootballAsianHandicaps();

        // Additional Football Markets
        generate1X2Variants();
        generateFirstGoal();
        generateAwayHandicaps();
        generateRestOfMatchVariants();
        generateGGNGVariants();
        generateGoalsInRowMarkets();
        generateLeadByGoalsMarkets();
        generateGoalBoundsMarkets();
        generateExcludedGoalsMarkets();
        generateCorrectScoreMarkets();
        generateHalfTimeFullTime();
        generateFirstHalfMarkets();
        generateSecondHalfMarkets();
        generateExactGoalsMarkets();
        generateCleanSheetMarkets();
        generateHighestScoringHalf();
        generateComboMarkets();

        // ───────────────────────────────────────────────
        // TABLE TENNIS MARKETS
        // ───────────────────────────────────────────────
        generateTableTennisMarkets();

        // ───────────────────────────────────────────────
        // BASKETBALL MARKETS
        // ───────────────────────────────────────────────

        // Basketball - WIN MARKET
        Map<String, String> winner = new HashMap<>();
        winner.put("4", "Home");
        winner.put("5", "Away");
        addMarket("219", null, "Winner (incl. overtime)", winner);

        // Basketball totals, handicaps, etc.
        generateFullGameTotals();
        generateFirstHalfTotals();
        generateSecondHalfTotals();
        generateAllQuarterTotals();
        generateFullGameHandicaps();
        generateAsianHandicaps();
        generateFirstHalfHandicaps();
        generateFirstHalfAsianHandicaps();
        generateSecondHalfHandicaps();
        generateAllQuarterHandicaps();
        generateQuarterDrawNoBet();

        // Additional Basketball Markets
        generateTeamTotalsIncludingOvertime("227", "Mobis Phoebus");
        generateTeamTotalsIncludingOvertime("228", "Samsung Thunders");
        generateAllQuarter1X2();
        generateAllQuarterWinningMargin();
        generateAllQuarterOddEven();
        generateAllQuarterRaceToPoints();
        generateFirstHalf1X2();
        generateFirstHalfDrawNoBet();
        generateFirstHalfOddEven();
        generateSecondHalf1X2();
        generateSecondHalfDrawNoBet();
        generateSecondHalfOddEven();
        generateRaceToPointsIncludingOvertime();

        // Odd/Even (incl. overtime)
        Map<String, String> oddEvenOT = new HashMap<>();
        oddEvenOT.put("70", "Odd");
        oddEvenOT.put("72", "Even");
        addMarket("229", null, "Odd/even (incl. overtime)", oddEvenOT);

        // Will There be Overtime
        Map<String, String> overtime = new HashMap<>();
        overtime.put("74", "Yes");
        overtime.put("76", "No");
        addMarket("220", null, "Will There be Overtime", overtime);

        // Winning Margin
        Map<String, String> winningMargin = new HashMap<>();
        winningMargin.put("sr:winning_margin:6+:120", "Home by 6+");
        winningMargin.put("sr:winning_margin:6+:121", "Away by 6+");
        winningMargin.put("sr:winning_margin:6+:122", "Other");
        addMarket("15", "variant=sr:winning_margin:6+", "Winning Margin", winningMargin);
    }

    private void generateFootballOverUnder() {
        double[] totals = {0.5, 1.5, 2.5, 3.5, 4.5, 5.5};

        for (double total : totals) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("18", "total=" + line, "Over/Under", map);
        }
    }

    private void generate1X2Variants() {
        // 1X2 - 1UP
        Map<String, String> map1UP = new HashMap<>();
        map1UP.put("1", "Home");
        map1UP.put("2", "Draw");
        map1UP.put("3", "Away");
        addMarket("60200", null, "1X2 - 1UP", map1UP);

        // 1X2 - 2UP
        Map<String, String> map2UP = new HashMap<>();
        map2UP.put("1", "Home");
        map2UP.put("2", "Draw");
        map2UP.put("3", "Away");
        addMarket("60100", null, "1X2 - 2UP", map2UP);
    }

    private void generateFirstGoal() {
        Map<String, String> map = new HashMap<>();
        map.put("6", "Home");
        map.put("7", "None");
        map.put("8", "Away");
        addMarket("8", "goalnr=1", "1st Goal", map);
    }

    private void generateAwayHandicaps() {
        // Handicaps 0:1, 0:2, 0:3
        int[] handicaps = {1, 2, 3};

        for (int hcp : handicaps) {
            Map<String, String> map = new HashMap<>();
            map.put("1711", "Home (0:" + hcp + ")");
            map.put("1712", "Draw (0:" + hcp + ")");
            map.put("1713", "Away (0:" + hcp + ")");
            addMarket("14", "hcp=0:" + hcp, "Handicap 0:" + hcp, map);
        }
    }

    private void generateRestOfMatchVariants() {
        // Rest of Match score 0:0
        Map<String, String> map = new HashMap<>();
        map.put("1", "Home");
        map.put("2", "Draw");
        map.put("3", "Away");
        addMarket("7", "score=0:0", "Rest of the Match (current score 0:0)", map);

        // Rest of Match Total Goals
        double[] totals = {1.5, 2.5, 3.5};
        for (double total : totals) {
            String line = formatDecimal(total);
            Map<String, String> mapTotal = new HashMap<>();
            mapTotal.put("30", "Over " + line);
            mapTotal.put("31", "Under " + line);
            addMarket("900028", "total=" + line + "|score=0:0",
                    "Rest of Match Total Goals (current score 0:0)", mapTotal);
        }

        // Rest of Match Asian Handicap
        double[] handicaps = {-1.5, -0.5, 0.5, 1.5};
        for (double hcp : handicaps) {
            Map<String, String> mapHcp = new HashMap<>();
            if (hcp < 0) {
                mapHcp.put("1", "Home " + formatDecimal(hcp));
                mapHcp.put("3", "Away +" + formatDecimal(Math.abs(hcp)));
            } else {
                mapHcp.put("1", "Home +" + formatDecimal(hcp));
                mapHcp.put("3", "Away " + formatDecimal(-hcp));
            }
            addMarket("900035", "hcp=" + formatDecimal(hcp) + "|score=0:0",
                    "Rest of Match Asian Handicap (current score 0:0)", mapHcp);
        }
    }

    private void generateGGNGVariants() {
        // GG/NG 2+
        Map<String, String> map = new HashMap<>();
        map.put("74", "Yes");
        map.put("76", "No");
        addMarket("60000", null, "GG/NG 2+", map);
    }

    private void generateGoalsInRowMarkets() {
        // Any Team to Score 2+ in a Row
        Map<String, String> any2 = new HashMap<>();
        any2.put("74", "Yes");
        any2.put("76", "No");
        addMarket("60010", null, "Any Team To Score 2 or More Goals in a Row", any2);

        // Any Team to Score 3+ in a Row
        Map<String, String> any3 = new HashMap<>();
        any3.put("74", "Yes");
        any3.put("76", "No");
        addMarket("60020", null, "Any Team To Score 3 or More Goals in a Row", any3);

        // Home Team to Score 2+ in a Row
        Map<String, String> home2 = new HashMap<>();
        home2.put("74", "Yes");
        home2.put("76", "No");
        addMarket("60011", null, "Home Team To Score 2 or More Goals in a Row", home2);

        // Home Team to Score 3+ in a Row
        Map<String, String> home3 = new HashMap<>();
        home3.put("74", "Yes");
        home3.put("76", "No");
        addMarket("60021", null, "Home Team To Score 3 or More Goals in a Row", home3);

        // Away Team to Score 2+ in a Row
        Map<String, String> away2 = new HashMap<>();
        away2.put("74", "Yes");
        away2.put("76", "No");
        addMarket("60012", null, "Away Team To Score 2 or More Goals in a Row", away2);

        // Away Team to Score 3+ in a Row
        Map<String, String> away3 = new HashMap<>();
        away3.put("74", "Yes");
        away3.put("76", "No");
        addMarket("60022", null, "Away Team To Score 3 or More Goals in a Row", away3);
    }

    private void generateLeadByGoalsMarkets() {
        // Any Team to lead by 1/2/3 goals
        for (int goals = 1; goals <= 3; goals++) {
            Map<String, String> map = new HashMap<>();
            map.put("74", "Yes");
            map.put("76", "No");
            addMarket("6030" + (goals - 1), null,
                    "Any Team to lead by " + goals + " Goal" + (goals > 1 ? "s" : "") + " at any time", map);
        }

        // Home Team to lead by 1/2/3 goals
        for (int goals = 1; goals <= 3; goals++) {
            Map<String, String> map = new HashMap<>();
            map.put("74", "Yes");
            map.put("76", "No");
            addMarket("6030" + (goals + 2), null,
                    "Home Team to lead by " + goals + " Goal" + (goals > 1 ? "s" : "") + " at any time", map);
        }

        // Away Team to lead by 1/2/3 goals
        for (int goals = 1; goals <= 3; goals++) {
            Map<String, String> map = new HashMap<>();
            map.put("74", "Yes");
            map.put("76", "No");
            addMarket("6030" + (goals + 5), null,
                    "Away Team to lead by " + goals + " Goal" + (goals > 1 ? "s" : "") + " at any time", map);
        }
    }

    private void generateGoalBoundsMarkets() {
        // Goal Bounds - Full Match
        Map<String, String> goalBounds = new HashMap<>();
        goalBounds.put("0", "0");
        goalBounds.put("1", "0-1");
        goalBounds.put("2", "0-2");
        goalBounds.put("3", "0-3");
        goalBounds.put("4", "0-4");
        goalBounds.put("11", "1");
        goalBounds.put("12", "1-2");
        goalBounds.put("13", "1-3");
        goalBounds.put("14", "1-4");
        goalBounds.put("15", "1-5+");
        goalBounds.put("22", "2");
        goalBounds.put("23", "2-3");
        goalBounds.put("24", "2-4");
        goalBounds.put("25", "2-5+");
        goalBounds.put("33", "3");
        goalBounds.put("34", "3-4");
        goalBounds.put("35", "3-5+");
        goalBounds.put("44", "4");
        goalBounds.put("45", "4-5+");
        goalBounds.put("55", "5+");
        addMarket("450001", null, "Goal Bounds", goalBounds);

        // Goal Bounds - Away
        Map<String, String> goalBoundsAway = new HashMap<>();
        goalBoundsAway.put("0", "0");
        goalBoundsAway.put("1", "0-1");
        goalBoundsAway.put("2", "0-2");
        goalBoundsAway.put("11", "1");
        goalBoundsAway.put("12", "1-2");
        goalBoundsAway.put("13", "1-3+");
        goalBoundsAway.put("22", "2");
        goalBoundsAway.put("23", "2-3+");
        goalBoundsAway.put("33", "3+");
        addMarket("450003", null, "Goal Bounds - Away", goalBoundsAway);

        // Goal Bounds - 1st Half
        Map<String, String> goalBounds1H = new HashMap<>();
        goalBounds1H.put("0", "0");
        goalBounds1H.put("1", "0-1");
        goalBounds1H.put("2", "0-2");
        goalBounds1H.put("11", "1");
        goalBounds1H.put("12", "1-2");
        goalBounds1H.put("13", "1-3+");
        goalBounds1H.put("22", "2");
        goalBounds1H.put("23", "2-3+");
        goalBounds1H.put("33", "3+");
        addMarket("810001", null, "Goal Bounds - 1st Half", goalBounds1H);
    }

    private void generateExcludedGoalsMarkets() {
        // Excluded Number of Goals - Full Match
        Map<String, String> excluded = new HashMap<>();
        excluded.put("0", "0");
        excluded.put("1", "1");
        excluded.put("2", "2");
        excluded.put("3", "3");
        excluded.put("4", "4");
        excluded.put("5", "5+");
        addMarket("450004", null, "Excluded Number of Goals", excluded);

        // Excluded Number of Goals - Home
        Map<String, String> excludedHome = new HashMap<>();
        excludedHome.put("0", "0");
        excludedHome.put("1", "1");
        excludedHome.put("2", "2");
        excludedHome.put("3", "3+");
        addMarket("450005", null, "Excluded Number of Goals - Home", excludedHome);

        // Excluded Number of Goals - Away
        Map<String, String> excludedAway = new HashMap<>();
        excludedAway.put("0", "0");
        excludedAway.put("1", "1");
        excludedAway.put("2", "2");
        excludedAway.put("3", "3+");
        addMarket("450006", null, "Excluded Number of Goals - Away", excludedAway);

        // Excluded Number of Goals - First Half
        Map<String, String> excluded1H = new HashMap<>();
        excluded1H.put("0", "0");
        excluded1H.put("1", "1");
        excluded1H.put("2", "2");
        excluded1H.put("3", "3+");
        addMarket("810002", null, "Excluded Number of Goals - First Half", excluded1H);
    }

    private void generateCorrectScoreMarkets() {
        // Correct Score - Full Match
        Map<String, String> correctScore = new HashMap<>();
        correctScore.put("274", "0:0");
        correctScore.put("276", "1:0");
        correctScore.put("278", "2:0");
        correctScore.put("280", "3:0");
        correctScore.put("282", "4:0");
        correctScore.put("284", "0:1");
        correctScore.put("286", "1:1");
        correctScore.put("288", "2:1");
        correctScore.put("290", "3:1");
        correctScore.put("292", "4:1");
        correctScore.put("294", "0:2");
        correctScore.put("296", "1:2");
        correctScore.put("298", "2:2");
        correctScore.put("300", "3:2");
        correctScore.put("302", "4:2");
        correctScore.put("304", "0:3");
        correctScore.put("306", "1:3");
        correctScore.put("308", "2:3");
        correctScore.put("310", "3:3");
        correctScore.put("312", "4:3");
        correctScore.put("314", "0:4");
        correctScore.put("316", "1:4");
        correctScore.put("318", "2:4");
        correctScore.put("320", "3:4");
        correctScore.put("322", "4:4");
        correctScore.put("324", "Other");
        addMarket("45", null, "Correct Score", correctScore);

        // Correct Score [0:0] - Live betting
        Map<String, String> correctScoreLive = new HashMap<>();
        correctScoreLive.put("110", "0:0");
        correctScoreLive.put("114", "1:0");
        correctScoreLive.put("116", "2:0");
        correctScoreLive.put("118", "3:0");
        correctScoreLive.put("120", "4:0");
        correctScoreLive.put("122", "5:0");
        correctScoreLive.put("124", "6:0");
        correctScoreLive.put("126", "0:1");
        correctScoreLive.put("128", "1:1");
        correctScoreLive.put("130", "2:1");
        correctScoreLive.put("132", "3:1");
        correctScoreLive.put("134", "4:1");
        correctScoreLive.put("136", "5:1");
        correctScoreLive.put("138", "0:2");
        correctScoreLive.put("140", "1:2");
        correctScoreLive.put("142", "2:2");
        correctScoreLive.put("144", "3:2");
        correctScoreLive.put("146", "4:2");
        correctScoreLive.put("148", "0:3");
        correctScoreLive.put("150", "1:3");
        correctScoreLive.put("152", "2:3");
        correctScoreLive.put("154", "3:3");
        correctScoreLive.put("156", "0:4");
        correctScoreLive.put("158", "1:4");
        correctScoreLive.put("160", "2:4");
        correctScoreLive.put("162", "0:5");
        correctScoreLive.put("164", "1:5");
        correctScoreLive.put("166", "0:6");
        addMarket("41", "score=0:0", "Correct Score [0:0]", correctScoreLive);
    }

    private void generateHalfTimeFullTime() {
        Map<String, String> map = new HashMap<>();
        map.put("418", "Home/Home");
        map.put("420", "Home/Draw");
        map.put("422", "Home/Away");
        map.put("424", "Draw/Home");
        map.put("426", "Draw/Draw");
        map.put("428", "Draw/Away");
        map.put("430", "Away/Home");
        map.put("432", "Away/Draw");
        map.put("434", "Away/Away");
        addMarket("47", null, "Half Time/Full Time", map);
    }

    private void generateFirstHalfMarkets() {
        // 1st Half 1X2
        Map<String, String> map1X2 = new HashMap<>();
        map1X2.put("1", "Home");
        map1X2.put("2", "Draw");
        map1X2.put("3", "Away");
        addMarket("60", null, "1st Half - 1X2", map1X2);

        // 1st Half Over/Under
        double[] totals = {0.5, 1.5, 2.5};
        for (double total : totals) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("68", "total=" + line, "1st Half - Over/Under", map);
        }

        // 1st Half Double Chance
        Map<String, String> mapDC = new HashMap<>();
        mapDC.put("9", "Home or Draw");
        mapDC.put("10", "Home or Away");
        mapDC.put("11", "Draw or Away");
        addMarket("63", null, "1st Half - Double Chance", mapDC);

        // 1st Half Handicap
        int[] handicaps = {1};
        for (int hcp : handicaps) {
            Map<String, String> map = new HashMap<>();
            map.put("1711", "Home (0:" + hcp + ")");
            map.put("1712", "Draw (0:" + hcp + ")");
            map.put("1713", "Away (0:" + hcp + ")");
            addMarket("65", "hcp=0:" + hcp, "1st Half - Handicap", map);
        }

        // 1st Half Asian Handicap
        double[] asianHcps = {-0.5, 0.5};
        for (double hcp : asianHcps) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            if (hcp < 0) {
                map.put("1714", "Home (-" + line + ")");
                map.put("1715", "Away (+" + line + ")");
            } else {
                map.put("1714", "Home (+" + line + ")");
                map.put("1715", "Away (-" + line + ")");
            }
            addMarket("66", "hcp=" + formatDecimal(hcp), "1st Half - Asian Handicap", map);
        }

        // 1st Half GG/NG
        Map<String, String> mapGG = new HashMap<>();
        mapGG.put("74", "Yes");
        mapGG.put("76", "No");
        addMarket("75", null, "1st Half - GG/NG", mapGG);

        // 1st Half Correct Score
        Map<String, String> mapCS = new HashMap<>();
        mapCS.put("462", "0:0");
        mapCS.put("464", "1:1");
        mapCS.put("466", "2:2");
        mapCS.put("468", "1:0");
        mapCS.put("470", "2:0");
        mapCS.put("472", "2:1");
        mapCS.put("474", "0:1");
        mapCS.put("476", "0:2");
        mapCS.put("478", "1:2");
        mapCS.put("480", "Other");
        addMarket("81", null, "1st Half - Correct Score", mapCS);
    }

    private void generateSecondHalfMarkets() {
        // 2nd Half 1X2
        Map<String, String> map = new HashMap<>();
        map.put("1", "Home");
        map.put("2", "Draw");
        map.put("3", "Away");
        addMarket("83", null, "2nd Half - 1X2", map);

        // 2nd Half GG/NG
        Map<String, String> mapGG = new HashMap<>();
        mapGG.put("74", "Yes");
        mapGG.put("76", "No");
        addMarket("95", null, "2nd Half - GG/NG", mapGG);
    }

    private void generateExactGoalsMarkets() {
        // Exact Goals
        Map<String, String> exactGoals = new HashMap<>();
        exactGoals.put("sr:exact_goals:5+:1336", "0");
        exactGoals.put("sr:exact_goals:5+:1337", "1");
        exactGoals.put("sr:exact_goals:5+:1338", "2");
        exactGoals.put("sr:exact_goals:5+:1339", "3");
        exactGoals.put("sr:exact_goals:5+:1340", "4");
        exactGoals.put("sr:exact_goals:5+:1341", "5+");
        addMarket("21", "variant=sr:exact_goals:5+", "Exact Goals", exactGoals);

        // Home Team Goals
        Map<String, String> homeGoals = new HashMap<>();
        homeGoals.put("sr:exact_goals:3+:88", "0");
        homeGoals.put("sr:exact_goals:3+:89", "1");
        homeGoals.put("sr:exact_goals:3+:90", "2");
        homeGoals.put("sr:exact_goals:3+:91", "3+");
        addMarket("23", "variant=sr:exact_goals:3+", "Home Team Goals", homeGoals);

        // Away Team Goals
        Map<String, String> awayGoals = new HashMap<>();
        awayGoals.put("sr:exact_goals:3+:88", "0");
        awayGoals.put("sr:exact_goals:3+:89", "1");
        awayGoals.put("sr:exact_goals:3+:90", "2");
        awayGoals.put("sr:exact_goals:3+:91", "3+");
        addMarket("24", "variant=sr:exact_goals:3+", "Away Team Goals", awayGoals);
    }

    private void generateCleanSheetMarkets() {
        // Home Team Clean Sheet
        Map<String, String> homeCS = new HashMap<>();
        homeCS.put("74", "Yes");
        homeCS.put("76", "No");
        addMarket("31", null, "Home Team Clean Sheet", homeCS);

        // Away Team Clean Sheet
        Map<String, String> awayCS = new HashMap<>();
        awayCS.put("74", "Yes");
        awayCS.put("76", "No");
        addMarket("32", null, "Away Team Clean Sheet", awayCS);
    }

    private void generateHighestScoringHalf() {
        Map<String, String> map = new HashMap<>();
        map.put("436", "1st half");
        map.put("438", "2nd half");
        map.put("440", "Equal");
        addMarket("52", null, "Highest Scoring Half", map);
    }

    private void generateComboMarkets() {
        // 1X2 & Over/Under
        double[] totals = {1.5, 2.5, 3.5};
        for (double total : totals) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("794", "Home & Under " + line);
            map.put("796", "Home & Over " + line);
            map.put("798", "Draw & Under " + line);
            map.put("800", "Draw & Over " + line);
            map.put("802", "Away & Under " + line);
            map.put("804", "Away & Over " + line);
            addMarket("37", "total=" + line, "1X2 & Over/Under", map);
        }

        // 1X2 & GG/NG
        Map<String, String> map1X2GG = new HashMap<>();
        map1X2GG.put("78", "Home & yes");
        map1X2GG.put("80", "Home & no");
        map1X2GG.put("82", "Draw & yes");
        map1X2GG.put("84", "Draw & no");
        map1X2GG.put("86", "Away & yes");
        map1X2GG.put("88", "Away & no");
        addMarket("35", null, "1X2 & GG/NG", map1X2GG);

        // Double Chance & Over/Under
        double[] dcTotals = {1.5, 2.5, 3.5, 4.5};
        for (double total : dcTotals) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("1724", "Home/Draw & Under " + line);
            map.put("1725", "Home/Away & Under " + line);
            map.put("1726", "Draw/Away & Under " + line);
            map.put("1727", "Home/Draw & Over " + line);
            map.put("1728", "Home/Away & Over " + line);
            map.put("1729", "Draw/Away & Over " + line);
            addMarket("547", "total=" + line, "Double Chance & Over/Under", map);
        }

        // Double Chance & GG/NG
        Map<String, String> mapDCGG = new HashMap<>();
        mapDCGG.put("1718", "Home/Draw & Yes");
        mapDCGG.put("1719", "Home/Draw & No");
        mapDCGG.put("1720", "Home/Away & Yes");
        mapDCGG.put("1721", "Home/Away & No");
        mapDCGG.put("1722", "Draw/Away & Yes");
        mapDCGG.put("1723", "Draw/Away & No");
        addMarket("546", null, "Double Chance & GG/NG", mapDCGG);
    }

    private void generateTeamOverUnder(String marketId, String teamName) {
        double[] totals = {0.5, 1.5, 2.5, 3.5};

        for (double total : totals) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket(marketId, "total=" + line, teamName + " Over/Under", map);
        }
    }

    private void generateFootballHandicaps() {
        int[] handicaps = {1, 2, 3, 4, 5};

        for (int hcp : handicaps) {
            Map<String, String> map = new HashMap<>();
            map.put("1711", "Home (" + hcp + ":0)");
            map.put("1712", "Draw (" + hcp + ":0)");
            map.put("1713", "Away (" + hcp + ":0)");
            addMarket("14", "hcp=" + hcp + ":0", "Handicap " + hcp + ":0", map);
        }
    }

    private void generateFootballAsianHandicaps() {
        // Negative handicaps: -5.5 to -0.5
        double start = -5.5;
        double end = -0.5;
        double step = 0.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("16", "hcp=" + formatDecimal(hcp), "Asian Handicap", map);
        }

        // Positive handicaps: +0.5 to +5.5
        start = 0.5;
        end = 5.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("16", "hcp=" + line, "Asian Handicap", map);
        }
    }

// ──────────────────────────────────────────────────────────────
// TABLE TENNIS GENERATORS
// ──────────────────────────────────────────────────────────────

    private void generateTableTennisMarkets() {
        // Match Winner
        Map<String, String> winner = new HashMap<>();
        winner.put("4", "Home");
        winner.put("5", "Away");
        addMarket("186", null, "Winner", winner);

        // Match Total Points
        generateMatchTotalPoints();

        // Correct Score (Best of 5)
        Map<String, String> correctScore = new HashMap<>();
        correctScore.put("sr:correct_score:bestof:5:8", "3:0");
        correctScore.put("sr:correct_score:bestof:5:9", "3:1");
        correctScore.put("sr:correct_score:bestof:5:10", "3:2");
        correctScore.put("sr:correct_score:bestof:5:11", "2:3");
        correctScore.put("sr:correct_score:bestof:5:12", "1:3");
        correctScore.put("sr:correct_score:bestof:5:13", "0:3");
        addMarket("199", "variant=sr:correct_score:bestof:5", "Correct Score", correctScore);

        // Game Handicap
        generateGameHandicaps();

        // Exact Games (Best of 5)
        Map<String, String> exactGames = new HashMap<>();
        exactGames.put("sr:exact_games:bestof:5:39", "3");
        exactGames.put("sr:exact_games:bestof:5:40", "4");
        exactGames.put("sr:exact_games:bestof:5:41", "5");
        addMarket("241", "variant=sr:exact_games:bestof:5", "Exact games", exactGames);

        // How many games will be decided by extra points (Best of 5)
        Map<String, String> extraPoints = new HashMap<>();
        extraPoints.put("sr:decided_by_extra_points:bestof:5:53", "0");
        extraPoints.put("sr:decided_by_extra_points:bestof:5:54", "1");
        extraPoints.put("sr:decided_by_extra_points:bestof:5:55", "2");
        extraPoints.put("sr:decided_by_extra_points:bestof:5:56", "3");
        extraPoints.put("sr:decided_by_extra_points:bestof:5:57", "4");
        extraPoints.put("sr:decided_by_extra_points:bestof:5:58", "5");
        addMarket("239", "variant=sr:decided_by_extra_points:bestof:5",
                "How many games will be decided by extra points", extraPoints);

        // Generate for games 1-7 (standard best of 7)
        for (int game = 1; game <= 7; game++) {
            generateGameWinner(game);
            generateGameTotalPoints(game);
            generateGamePointHandicaps(game);
            generateGameRaceToPoints(game);
            generateGameNthPoint(game);
            generateGameOddEven(game);
        }

        // Match Point Handicaps
        generateMatchPointHandicaps();
    }

    private void generateGameWinner(int game) {
        String marketName = game + getOrdinalSuffix(game) + " game - winner";
        Map<String, String> map = new HashMap<>();
        map.put("4", "Home");
        map.put("5", "Away");
        addMarket("245", "gamenr=" + game, marketName, map);
    }

    private void generateMatchTotalPoints() {
        // Match total points: 50.5 to 120.5
        double start = 50.5;
        double end = 120.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("238", "total=" + line, "Total points", map);
        }
    }

    private void generateGameTotalPoints(int game) {
        String marketName = game + getOrdinalSuffix(game) + " game - total points";

        // Game total points: 15.5 to 30.5
        double start = 15.5;
        double end = 30.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            String param = "total=" + line + "|gamenr=" + game;
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("247", param, marketName, map);
        }
    }

    private void generateGameHandicaps() {
        // Game handicaps: -2.5 to +2.5 (0.5 increments)
        double start = -2.5;
        double end = -0.5;
        double step = 0.5;

        // Negative handicaps
        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("187", "hcp=" + formatDecimal(hcp), "Game handicap", map);
        }

        // Positive handicaps
        start = 0.5;
        end = 2.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("187", "hcp=" + line, "Game handicap", map);
        }
    }

    private void generateGamePointHandicaps(int game) {
        String marketName = game + getOrdinalSuffix(game) + " game - point handicap";

        // Negative handicaps: -10.5 to -0.5
        double start = -10.5;
        double end = -0.5;
        double step = 0.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            String param = "hcp=" + formatDecimal(hcp) + "|gamenr=" + game;
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("246", param, marketName, map);
        }

        // Positive handicaps: +0.5 to +10.5
        start = 0.5;
        end = 10.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            String param = "hcp=" + line + "|gamenr=" + game;
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("246", param, marketName, map);
        }
    }

    private void generateGameRaceToPoints(int game) {
        // Race to 5, 8, 10, 12, 15 points for each game
        int[] racePoints = {5, 8, 10, 12, 15};

        for (int points : racePoints) {
            String marketName = game + getOrdinalSuffix(game) + " game - race to " + points + " points";
            String param = "gamenr=" + game + "|pointnr=" + points;
            Map<String, String> map = new HashMap<>();
            map.put("4", "Home");
            map.put("5", "Away");
            addMarket("250", param, marketName, map);
        }
    }

    private void generateGameNthPoint(int game) {
        // Nth point markets for points 5, 8, 10, 12, 15, 20
        int[] nthPoints = {5, 8, 10, 12, 15, 20};

        for (int point : nthPoints) {
            String marketName = game + getOrdinalSuffix(game) + " game - " + point + getOrdinalSuffix(point) + " point";
            String param = "gamenr=" + game + "|pointnr=" + point;
            Map<String, String> map = new HashMap<>();
            map.put("4", "Home");
            map.put("5", "Away");
            addMarket("520", param, marketName, map);
        }
    }

    private void generateGameOddEven(int game) {
        String marketName = game + getOrdinalSuffix(game) + " game - odd/even";
        Map<String, String> map = new HashMap<>();
        map.put("70", "Odd");
        map.put("72", "Even");
        addMarket("248", "gamenr=" + game, marketName, map);
    }

    private void generateMatchPointHandicaps() {
        // Match-level point handicaps: -20.5 to +20.5
        double start = -20.5;
        double end = -0.5;
        double step = 0.5;

        // Negative handicaps
        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("237", "hcp=" + formatDecimal(hcp), "Point handicap", map);
        }

        // Positive handicaps
        start = 0.5;
        end = 20.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("237", "hcp=" + line, "Point handicap", map);
        }
    }

// ──────────────────────────────────────────────────────────────
// BASKETBALL TOTALS GENERATORS
// ──────────────────────────────────────────────────────────────

    private void generateFullGameTotals() {
        double start = 150.5;
        double end = 280.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("225", "total=" + line, "Over/Under (incl. overtime)", map);
        }
    }

    private void generateFirstHalfTotals() {
        double start = 60.5;
        double end = 140.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("68", "total=" + line, "1st Half - Over/Under", map);
        }
    }

    private void generateSecondHalfTotals() {
        double start = 60.5;
        double end = 140.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("232", "total=" + line, "2nd half - total (incl. overtime)", map);
        }
    }

    private void generateAllQuarterTotals() {
        for (int quarter = 1; quarter <= 4; quarter++) {
            double start = 30.5;
            double end = 70.5;
            double step = 0.5;

            String marketName = quarter + getOrdinalSuffix(quarter) + " quarter - Over/Under";

            for (double total = start; total <= end; total += step) {
                String line = formatDecimal(total);
                String param = "total=" + line + "|quarternr=" + quarter;
                Map<String, String> map = new HashMap<>();
                map.put("12", "Over " + line);
                map.put("13", "Under " + line);
                addMarket("236", param, marketName, map);
            }
        }
    }

// ──────────────────────────────────────────────────────────────
// BASKETBALL HANDICAP GENERATORS
// ──────────────────────────────────────────────────────────────

    private void generateFullGameHandicaps() {
        // Negative handicaps: -30.5 to -0.5
        double start = -30.5;
        double end = -0.5;
        double step = 0.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("223", "hcp=" + formatDecimal(hcp), "Handicap (incl. overtime)", map);
        }

        // Positive handicaps: +0.5 to +30.5
        start = 0.5;
        end = 30.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("223", "hcp=" + line, "Handicap (incl. overtime)", map);
        }
    }

    private void generateAsianHandicaps() {
        // Negative handicaps: -30.5 to -0.5
        double start = -30.5;
        double end = -0.5;
        double step = 0.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("16", "hcp=" + formatDecimal(hcp), "Asian Handicap", map);
        }

        // Positive handicaps: +0.5 to +30.5
        start = 0.5;
        end = 30.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("16", "hcp=" + line, "Asian Handicap", map);
        }
    }

    private void generateFirstHalfHandicaps() {
        // Negative handicaps: -15.5 to -0.5
        double start = -15.5;
        double end = -0.5;
        double step = 0.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("230", "hcp=" + formatDecimal(hcp), "1st half - handicap", map);
        }

        // Positive handicaps: +0.5 to +15.5
        start = 0.5;
        end = 15.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("230", "hcp=" + line, "1st half - handicap", map);
        }
    }

    private void generateFirstHalfAsianHandicaps() {
        // Negative handicaps: -15.5 to -0.5
        double start = -15.5;
        double end = -0.5;
        double step = 0.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("66", "hcp=" + formatDecimal(hcp), "1st Half - Asian Handicap", map);
        }

        // Positive handicaps: +0.5 to +15.5
        start = 0.5;
        end = 15.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("66", "hcp=" + line, "1st Half - Asian Handicap", map);
        }
    }

    private void generateSecondHalfHandicaps() {
        // Negative handicaps: -15.5 to -0.5
        double start = -15.5;
        double end = -0.5;
        double step = 0.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("231", "hcp=" + formatDecimal(hcp), "2nd half - handicap (incl. overtime)", map);
        }

        // Positive handicaps: +0.5 to +15.5
        start = 0.5;
        end = 15.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("231", "hcp=" + line, "2nd half - handicap (incl. overtime)", map);
        }
    }

    private void generateAllQuarterHandicaps() {
        for (int quarter = 1; quarter <= 4; quarter++) {
            String marketName = quarter + getOrdinalSuffix(quarter) + " quarter - handicap";

            // Negative handicaps: -10.5 to -0.5
            double start = -10.5;
            double end = -0.5;
            double step = 0.5;

            for (double hcp = start; hcp <= end; hcp += step) {
                String line = formatDecimal(Math.abs(hcp));
                String param = "hcp=" + formatDecimal(hcp) + "|quarternr=" + quarter;
                Map<String, String> map = new HashMap<>();
                map.put("1714", "Home (-" + line + ")");
                map.put("1715", "Away (+" + line + ")");
                addMarket("303", param, marketName, map);
            }

            // Positive handicaps: +0.5 to +10.5
            start = 0.5;
            end = 10.5;

            for (double hcp = start; hcp <= end; hcp += step) {
                String line = formatDecimal(hcp);
                String param = "hcp=" + line + "|quarternr=" + quarter;
                Map<String, String> map = new HashMap<>();
                map.put("1714", "Home (+" + line + ")");
                map.put("1715", "Away (-" + line + ")");
                addMarket("303", param, marketName, map);
            }
        }
    }

// ──────────────────────────────────────────────────────────────
// BASKETBALL DRAW NO BET
// ──────────────────────────────────────────────────────────────

    private void generateQuarterDrawNoBet() {
        for (int quarter = 1; quarter <= 4; quarter++) {
            String marketName = quarter + getOrdinalSuffix(quarter) + " quarter - draw no bet";
            Map<String, String> map = new HashMap<>();
            map.put("4", "Home");
            map.put("5", "Away");
            addMarket("302", "quarternr=" + quarter, marketName, map);
        }
    }

// ──────────────────────────────────────────────────────────────
// ADDITIONAL BASKETBALL MARKETS
// ──────────────────────────────────────────────────────────────

    private void generateTeamTotalsIncludingOvertime(String marketId, String teamName) {
        // Team totals: 50.5 to 100.5
        double start = 50.5;
        double end = 100.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket(marketId, "total=" + line, teamName + " Over/Under (incl. overtime)", map);
        }
    }

    private void generateAllQuarter1X2() {
        for (int quarter = 1; quarter <= 4; quarter++) {
            String marketName = quarter + getOrdinalSuffix(quarter) + " quarter - 1x2";
            Map<String, String> map = new HashMap<>();
            map.put("1", "Home");
            map.put("2", "Draw");
            map.put("3", "Away");
            addMarket("235", "quarternr=" + quarter, marketName, map);
        }
    }

    private void generateAllQuarterWinningMargin() {
        for (int quarter = 1; quarter <= 4; quarter++) {
            String marketName = quarter + getOrdinalSuffix(quarter) + " quarter - winning margin";
            Map<String, String> map = new HashMap<>();
            map.put("1002", "Home by 3+");
            map.put("1003", "Away by 3+");
            map.put("1004", "Other");
            addMarket("301", "quarternr=" + quarter, marketName, map);
        }
    }

    private void generateAllQuarterOddEven() {
        for (int quarter = 1; quarter <= 4; quarter++) {
            String marketName = quarter + getOrdinalSuffix(quarter) + " quarter - odd/even";
            Map<String, String> map = new HashMap<>();
            map.put("70", "Odd");
            map.put("72", "Even");
            addMarket("304", "quarternr=" + quarter, marketName, map);
        }
    }

    private void generateAllQuarterRaceToPoints() {
        // Race to points: 5, 10, 15, 20
        int[] racePoints = {5, 10, 15, 20};

        for (int quarter = 1; quarter <= 4; quarter++) {
            for (int points : racePoints) {
                String marketName = quarter + getOrdinalSuffix(quarter) + " quarter - race to " + points + " points";
                String param = "quarternr=" + quarter + "|pointnr=" + points;

                // Market 305 - Two outcomes (Home/Away)
                Map<String, String> map305 = new HashMap<>();
                map305.put("4", "Home");
                map305.put("5", "Away");
                addMarket("305", param, marketName, map305);

                // Market 1057 - Three outcomes (Home/None/Away)
                Map<String, String> map1057 = new HashMap<>();
                map1057.put("6", "Home");
                map1057.put("7", "None");
                map1057.put("8", "Away");
                addMarket("1057", param, marketName, map1057);
            }
        }
    }

    private void generateFirstHalf1X2() {
        Map<String, String> map = new HashMap<>();
        map.put("1", "Home");
        map.put("2", "Draw");
        map.put("3", "Away");
        addMarket("60", null, "1st Half - 1X2", map);
    }

    private void generateFirstHalfDrawNoBet() {
        Map<String, String> map = new HashMap<>();
        map.put("4", "Home");
        map.put("5", "Away");
        addMarket("64", null, "1st Half - Draw No Bet", map);
    }

    private void generateFirstHalfOddEven() {
        Map<String, String> map = new HashMap<>();
        map.put("70", "Odd");
        map.put("72", "Even");
        addMarket("74", null, "1st Half - Odd/Even", map);
    }

    private void generateSecondHalf1X2() {
        Map<String, String> map = new HashMap<>();
        map.put("1", "Home");
        map.put("2", "Draw");
        map.put("3", "Away");
        addMarket("293", null, "2nd half - 1x2 (incl. overtime)", map);
    }

    private void generateSecondHalfDrawNoBet() {
        Map<String, String> map = new HashMap<>();
        map.put("4", "Home");
        map.put("5", "Away");
        addMarket("294", null, "2nd half - draw no bet (incl. overtime)", map);
    }

    private void generateSecondHalfOddEven() {
        Map<String, String> map = new HashMap<>();
        map.put("70", "Odd");
        map.put("72", "Even");
        addMarket("295", null, "2nd half - odd/even (incl. overtime)", map);
    }

    private void generateRaceToPointsIncludingOvertime() {
        // Race to points: 20, 30, 40
        int[] racePoints = {20, 30, 40};

        for (int points : racePoints) {
            Map<String, String> map = new HashMap<>();
            map.put("4", "Home");
            map.put("5", "Away");
            addMarket("230", "pointnr=" + points, "Race to " + points + " points (incl. overtime)", map);
        }
    }

// ──────────────────────────────────────────────────────────────
// UTILITY METHODS
// ──────────────────────────────────────────────────────────────

    private String formatDecimal(double value) {
        return String.format("%.1f", value);
    }

    private String getOrdinalSuffix(int number) {
        if (number >= 11 && number <= 13) {
            return "th";
        }
        int lastDigit = number % 10;
        if (lastDigit == 1) return "st";
        if (lastDigit == 2) return "nd";
        if (lastDigit == 3) return "rd";
        return "th";
    }




}
