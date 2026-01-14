package com.mouse.bet.mapper;

import com.mouse.bet.mapper.model.SimilarBookieMapper;

import java.util.HashMap;
import java.util.Map;

public class MSportBetMapper extends SimilarBookieMapper {
    public MSportBetMapper() {
        super("msport");
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
        generateFootballMarkets();

        // ───────────────────────────────────────────────
        // TABLE TENNIS MARKETS
        // ───────────────────────────────────────────────
        generateTableTennisMarkets();

        // ───────────────────────────────────────────────
        // BASKETBALL MARKETS
        // ───────────────────────────────────────────────
        generateBasketballMarkets();
    }

    // ──────────────────────────────────────────────────────────────
    // FOOTBALL GENERATORS
    // ──────────────────────────────────────────────────────────────

    private void generateFootballMarkets() {
        // 1X2 (Match Winner)
        Map<String, String> match1x2 = new HashMap<>();
        match1x2.put("1", "Home");
        match1x2.put("2", "Draw");
        match1x2.put("3", "Away");
        addMarket("1", null, "1x2", match1x2);

        // Double Chance
        Map<String, String> doubleChance = new HashMap<>();
        doubleChance.put("9", "1 X");
        doubleChance.put("10", "1 2");
        doubleChance.put("11", "X 2");
        addMarket("10", null, "Double Chance", doubleChance);

        // Draw No Bet
        Map<String, String> drawNoBet = new HashMap<>();
        drawNoBet.put("4", "Home");
        drawNoBet.put("5", "Away");
        addMarket("11", null, "DNB", drawNoBet);

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

        // Away Clean Sheet
        Map<String, String> awayCleanSheet = new HashMap<>();
        awayCleanSheet.put("74", "Yes");
        awayCleanSheet.put("76", "No");
        addMarket("32", null, "Away clean sheet", awayCleanSheet);

        // Correct Score
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
        addMarket("45", null, "Correct score", correctScore);

        // Exact Goals
        Map<String, String> exactGoals = new HashMap<>();
        exactGoals.put("sr:exact_goals:5+:1336", "0");
        exactGoals.put("sr:exact_goals:5+:1337", "1");
        exactGoals.put("sr:exact_goals:5+:1338", "2");
        exactGoals.put("sr:exact_goals:5+:1339", "3");
        exactGoals.put("sr:exact_goals:5+:1340", "4");
        exactGoals.put("sr:exact_goals:5+:1341", "5+");
        addMarket("21", "variant=sr:exact_goals:5+", "Exact goals", exactGoals);

        // Home Exact Goals
        Map<String, String> homeExactGoals = new HashMap<>();
        homeExactGoals.put("sr:exact_goals:3+:88", "0");
        homeExactGoals.put("sr:exact_goals:3+:89", "1");
        homeExactGoals.put("sr:exact_goals:3+:90", "2");
        homeExactGoals.put("sr:exact_goals:3+:91", "3+");
        addMarket("23", "variant=sr:exact_goals:3+", "Home Exact goals", homeExactGoals);

        // 1X2 & GG/NG
        Map<String, String> result1x2GG = new HashMap<>();
        result1x2GG.put("78", "Home& Yes");
        result1x2GG.put("80", "Home & No");
        result1x2GG.put("82", "Draw & Yes");
        result1x2GG.put("84", "Draw & No");
        result1x2GG.put("86", "Away & Yes");
        result1x2GG.put("88", "Away & No");
        addMarket("35", null, "1x2 & GG/NG", result1x2GG);

        // Double Chance & GG/NG
        Map<String, String> dcGG = new HashMap<>();
        dcGG.put("1718", "Home/Draw & Yes");
        dcGG.put("1719", "Home/Draw & No");
        dcGG.put("1720", "Home/Away & Yes");
        dcGG.put("1721", "Home/Away & No");
        dcGG.put("1722", "Draw/Away & Yes");
        dcGG.put("1723", "Draw/Away & No");
        addMarket("546", null, "Double chance & GG/NG", dcGG);

        // Nth Goal markets (1-10)
        for (int goal = 1; goal <= 10; goal++) {
            Map<String, String> nthGoal = new HashMap<>();
            nthGoal.put("6", "Home");
            nthGoal.put("7", "None");
            nthGoal.put("8", "Away");
            addMarket("8", "goalnr=" + goal, goal + getOrdinalSuffix(goal) + " Goal", nthGoal);
        }

        // Rest of Match markets (various scores)
        generateRestOfMatchMarkets();

        // Over/Under markets
        generateFootballOverUnder();

        // Home/Away Over/Under
        generateFootballHomeAwayOverUnder();

        // Handicap markets
        generateFootballHandicaps();

        // Asian Handicap markets
        generateFootballAsianHandicaps();

        // Double Chance & O/U combinations
        generateFootballDoubleChanceOverUnder();
    }

    private void generateRestOfMatchMarkets() {
        // Common score scenarios
        String[][] scores = {
                {"0:1"}, {"0:2"}, {"0:3"},
                {"1:0"}, {"1:1"}, {"1:2"}, {"1:3"},
                {"2:0"}, {"2:1"}, {"2:2"}, {"2:3"},
                {"3:0"}, {"3:1"}, {"3:2"}, {"3:3"}
        };

        for (String[] score : scores) {
            Map<String, String> map = new HashMap<>();
            map.put("1", "Home");
            map.put("2", "Draw");
            map.put("3", "Away");
            addMarket("7", "score=" + score[0], "Which team wins the rest of the match  [" + score[0] + "]", map);
        }
    }

    private void generateFootballOverUnder() {
        // Over/Under: 0.5 to 6.5
        double start = 0.5;
        double end = 6.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("18", "total=" + line, "Over/Under", map);
        }
    }

    private void generateFootballHomeAwayOverUnder() {
        // Home Team O/U: 0.5 to 4.5
        double start = 0.5;
        double end = 4.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("19", "total=" + line, "Home O/U", map);
        }

        // Away Team O/U: 0.5 to 4.5
        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("20", "total=" + line, "Away O/U", map);
        }
    }

    private void generateFootballHandicaps() {
        // Regular Handicaps: 1:0 to 5:0
        int[] handicaps = {1, 2, 3, 4, 5};

        for (int hcp : handicaps) {
            Map<String, String> map = new HashMap<>();
            map.put("1711", "Home (" + hcp + ":0)");
            map.put("1712", "Draw (" + hcp + ":0)");
            map.put("1713", "Away (" + hcp + ":0)");
            addMarket("14", "hcp=" + hcp + ":0", "Handicap", map);
        }
    }

    private void generateFootballAsianHandicaps() {
        // Asian Handicaps: -5.5 to +5.5
        double start = -5.5;
        double end = -0.5;
        double step = 0.5;

        // Negative handicaps
        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("16", "hcp=" + formatDecimal(hcp), "Asian Handicap", map);
        }

        // Positive handicaps
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

    private void generateFootballDoubleChanceOverUnder() {
        // Double Chance & O/U for common totals: 1.5, 2.5, 3.5, 4.5
        double[] totals = {1.5, 2.5, 3.5, 4.5};

        for (double total : totals) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("1724", "Home/Draw & Under " + line);
            map.put("1725", "Home/Away & Under " + line);
            map.put("1726", "Draw/Away & Under " + line);
            map.put("1727", "Home/Draw & Over " + line);
            map.put("1728", "Home/Away & Over " + line);
            map.put("1729", "Draw/Away & Over " + line);
            addMarket("547", "total=" + line, "Double chance & O/U " + line, map);
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

        // Correct Score (Best of 5)
        Map<String, String> correctScore = new HashMap<>();
        correctScore.put("sr:correct_score:bestof:5:8", "3:0");
        correctScore.put("sr:correct_score:bestof:5:9", "3:1");
        correctScore.put("sr:correct_score:bestof:5:10", "3:2");
        correctScore.put("sr:correct_score:bestof:5:11", "2:3");
        correctScore.put("sr:correct_score:bestof:5:12", "1:3");
        correctScore.put("sr:correct_score:bestof:5:13", "0:3");
        addMarket("199", "variant=sr:correct_score:bestof:5", "Correct score", correctScore);

        // Match Total Points O/U
        generateMatchTotalPoints();

        // Home Team Total Points O/U
        generateHomeTeamTotalPoints();

        // Away Team Total Points O/U
        generateAwayTeamTotalPoints();

        // Match Point Handicaps
        generateMatchPointHandicaps();

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

        // Exact Games (Best of 5)
        Map<String, String> exactGames = new HashMap<>();
        exactGames.put("sr:exact_games:bestof:5:39", "3");
        exactGames.put("sr:exact_games:bestof:5:40", "4");
        exactGames.put("sr:exact_games:bestof:5:41", "5");
        addMarket("241", "variant=sr:exact_games:bestof:5", "Exact games", exactGames);

        // Generate for games 1-7 (standard best of 7)
        for (int game = 1; game <= 7; game++) {
            generateGameWinner(game);
            generateGameTotalPoints(game);
            generateGamePointHandicaps(game);
            generateGameRaceToPoints(game);
            generateGameNthPoint(game);
            generateGameOddEven(game);
            generateGameWinningMargin(game);
            generateGamePlayerTotals(game);
        }
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
            addMarket("238", "total=" + line, "Points O/U", map);
        }
    }

    private void generateHomeTeamTotalPoints() {
        // Home team total points: 20.5 to 60.5
        double start = 20.5;
        double end = 60.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("19", "total=" + line, "Home O/U", map);
        }
    }

    private void generateAwayTeamTotalPoints() {
        // Away team total points: 20.5 to 60.5
        double start = 20.5;
        double end = 60.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("20", "total=" + line, "Away O/U", map);
        }
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

    private void generateGameWinner(int game) {
        String marketName = game + getOrdinalSuffix(game) + " game - winner";
        Map<String, String> map = new HashMap<>();
        map.put("4", "Home");
        map.put("5", "Away");
        addMarket("245", "gamenr=" + game, marketName, map);
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
        // Nth point markets for points 5, 7, 8, 10, 12, 15, 20
        int[] nthPoints = {5, 7, 8, 10, 12, 15, 20};

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

    private void generateGameWinningMargin(int game) {
        String marketName = game + getOrdinalSuffix(game) + " game - winning margin";
        Map<String, String> map = new HashMap<>();
        map.put("2132", "Home by 2");
        map.put("2133", "Home by 3-4");
        map.put("2134", "Home by 5+");
        map.put("2135", "Away by 2");
        map.put("2136", "Away by 3-4");
        map.put("2137", "Away by 5+");
        addMarket("1353", "gamenr=" + game, marketName, map);
    }

    private void generateGamePlayerTotals(int game) {
        // Home player total points: 5.5 to 15.5
        double start = 5.5;
        double end = 15.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            String param = "total=" + line + "|gamenr=" + game;
            String marketName = game + getOrdinalSuffix(game) + " game - Home player total";
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("1330", param, marketName, map);
        }

        // Away player total points: 5.5 to 15.5
        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            String param = "total=" + line + "|gamenr=" + game;
            String marketName = game + getOrdinalSuffix(game) + " game - Away player total";
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("1331", param, marketName, map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // BASKETBALL GENERATORS
    // ──────────────────────────────────────────────────────────────

    private void generateBasketballMarkets() {
        // Match Winner (incl. overtime)
        Map<String, String> winner = new HashMap<>();
        winner.put("4", "Home");
        winner.put("5", "Away");
        addMarket("219", null, "Winner (incl. overtime)", winner);

        // 1X2 (Full Time)
        Map<String, String> match1x2 = new HashMap<>();
        match1x2.put("1", "Home");
        match1x2.put("2", "Draw");
        match1x2.put("3", "Away");
        addMarket("1", null, "1x2", match1x2);

        // Draw No Bet
        Map<String, String> drawNoBet = new HashMap<>();
        drawNoBet.put("4", "Home");
        drawNoBet.put("5", "Away");
        addMarket("11", null, "DNB", drawNoBet);

        // Will there be overtime
        Map<String, String> overtime = new HashMap<>();
        overtime.put("74", "Yes");
        overtime.put("76", "No");
        addMarket("220", null, "Will there be overtime", overtime);

        // Odd/Even (incl. overtime)
        Map<String, String> oddEven = new HashMap<>();
        oddEven.put("70", "Odd");
        oddEven.put("72", "Even");
        addMarket("229", null, "Odd/Even (incl. overtime)", oddEven);

        // Winning Margin (incl. overtime)
        Map<String, String> winningMargin = new HashMap<>();
        winningMargin.put("sr:winning_margin_no_draw:11+:137", "Home by 11+");
        winningMargin.put("sr:winning_margin_no_draw:11+:138", "Home by 6-10");
        winningMargin.put("sr:winning_margin_no_draw:11+:139", "Home by 1-5");
        winningMargin.put("sr:winning_margin_no_draw:11+:140", "Away by 1-5");
        winningMargin.put("sr:winning_margin_no_draw:11+:141", "Away by 6-10");
        winningMargin.put("sr:winning_margin_no_draw:11+:142", "Away by 11+");
        addMarket("290", "variant=sr:winning_margin_no_draw:11+", "Winning margin (incl. overtime)", winningMargin);

        // Basketball totals, handicaps, etc.
        generateBasketballFullGameTotals();
        generateBasketballFullGameHandicaps();
        generateBasketballHomeAwayTotals();
        generateBasketballWinnerAndTotal();
        generateBasketballNthPoint();

        // 2nd Half markets
        generateBasketballSecondHalfMarkets();

        // Quarter markets (1-4)
        for (int quarter = 1; quarter <= 4; quarter++) {
            generateBasketballQuarterMarkets(quarter);
        }
    }

    private void generateBasketballFullGameTotals() {
        // Full game total (incl. OT): 150.5 to 280.5
        double start = 150.5;
        double end = 280.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("225", "total=" + line, "O/U (incl. OT)", map);
        }
    }

    private void generateBasketballFullGameHandicaps() {
        // Full game handicaps (incl. OT): -30.5 to +30.5
        double start = -30.5;
        double end = -0.5;
        double step = 0.5;

        // Negative handicaps
        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("223", "hcp=" + formatDecimal(hcp), "Handicap (incl. overtime)", map);
        }

        // Positive handicaps
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

    private void generateBasketballHomeAwayTotals() {
        // Home team total (incl. OT): 60.5 to 150.5
        double start = 60.5;
        double end = 150.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("227", "total=" + line, "Home O/U (incl. OT)", map);
        }

        // Away team total (incl. OT): 60.5 to 150.5
        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("228", "total=" + line, "Away O/U (incl. OT)", map);
        }
    }

    private void generateBasketballWinnerAndTotal() {
        // Winner & O/U combinations: 120.5 to 200.5
        double start = 120.5;
        double end = 200.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("973", "Home & Over " + line);
            map.put("974", "Away & Over " + line);
            map.put("975", "Home & Under " + line);
            map.put("976", "Away & Under " + line);
            addMarket("292", "total=" + line, "Winner & O/U (incl. overtime)", map);
        }
    }

    private void generateBasketballNthPoint() {
        // Nth point markets: 100, 105, 110, ..., 200 (incl. overtime)
        for (int point = 100; point <= 200; point += 5) {
            Map<String, String> map = new HashMap<>();
            map.put("4", "Home");
            map.put("5", "Away");
            addMarket("291", "pointnr=" + point, point + getOrdinalSuffix(point) + " point (incl. overtime)", map);
        }
    }

    private void generateBasketballSecondHalfMarkets() {
        // 2nd Half - 1x2 (incl. overtime)
        Map<String, String> half1x2 = new HashMap<>();
        half1x2.put("1", "Home");
        half1x2.put("2", "Draw");
        half1x2.put("3", "Away");
        addMarket("293", null, "2nd half - 1x2 (incl. overtime)", half1x2);

        // 2nd Half - Draw No Bet
        Map<String, String> halfDrawNoBet = new HashMap<>();
        halfDrawNoBet.put("4", "Home");
        halfDrawNoBet.put("5", "Away");
        addMarket("294", null, "2nd half - draw no bet (incl. overtime)", halfDrawNoBet);

        // 2nd Half - Odd/Even
        Map<String, String> halfOddEven = new HashMap<>();
        halfOddEven.put("70", "Odd");
        halfOddEven.put("72", "Even");
        addMarket("295", null, "2nd half - Odd/Even (incl. overtime)", halfOddEven);

        // 2nd Half - Totals: 60.5 to 140.5
        double start = 60.5;
        double end = 140.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("232", "total=" + line, "2nd half - O/U (incl. overtime)", map);
        }

        // 2nd Half - Handicaps: -15.5 to +15.5
        start = -15.5;
        end = -0.5;
        step = 0.5;

        // Negative handicaps
        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("231", "hcp=" + formatDecimal(hcp), "2nd half - Handicap (incl. overtime)", map);
        }

        // Positive handicaps
        start = 0.5;
        end = 15.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("231", "hcp=" + line, "2nd half - Handicap (incl. overtime)", map);
        }
    }

    private void generateBasketballQuarterMarkets(int quarter) {
        // Quarter 1x2
        Map<String, String> quarter1x2 = new HashMap<>();
        quarter1x2.put("1", "Home");
        quarter1x2.put("2", "Draw");
        quarter1x2.put("3", "Away");
        addMarket("235", "quarternr=" + quarter, quarter + getOrdinalSuffix(quarter) + " quarter - 1x2", quarter1x2);

        // Quarter Draw No Bet
        Map<String, String> quarterDrawNoBet = new HashMap<>();
        quarterDrawNoBet.put("4", "Home");
        quarterDrawNoBet.put("5", "Away");
        addMarket("302", "quarternr=" + quarter, quarter + getOrdinalSuffix(quarter) + " quarter - draw no bet", quarterDrawNoBet);

        // Quarter Odd/Even
        Map<String, String> quarterOddEven = new HashMap<>();
        quarterOddEven.put("70", "Odd");
        quarterOddEven.put("72", "Even");
        addMarket("304", "quarternr=" + quarter, quarter + getOrdinalSuffix(quarter) + " quarter - Odd/Even", quarterOddEven);

        // Quarter Winning Margin
        Map<String, String> quarterWinningMargin = new HashMap<>();
        quarterWinningMargin.put("1002", "Home by 3+");
        quarterWinningMargin.put("1003", "Away by 3+");
        quarterWinningMargin.put("1004", "Other");
        addMarket("301", "quarternr=" + quarter, quarter + getOrdinalSuffix(quarter) + " quarter - winning margin", quarterWinningMargin);

        // Quarter Totals: 30.5 to 70.5
        double start = 30.5;
        double end = 70.5;
        double step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            String param = "total=" + line + "|quarternr=" + quarter;
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("236", param, quarter + getOrdinalSuffix(quarter) + " quarter - O/U", map);
        }

        // Quarter Handicaps: -10.5 to +10.5
        start = -10.5;
        end = -0.5;
        step = 0.5;

        // Negative handicaps
        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(Math.abs(hcp));
            String param = "hcp=" + formatDecimal(hcp) + "|quarternr=" + quarter;
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (-" + line + ")");
            map.put("1715", "Away (+" + line + ")");
            addMarket("303", param, quarter + getOrdinalSuffix(quarter) + " quarter - Handicap", map);
        }

        // Positive handicaps
        start = 0.5;
        end = 10.5;

        for (double hcp = start; hcp <= end; hcp += step) {
            String line = formatDecimal(hcp);
            String param = "hcp=" + line + "|quarternr=" + quarter;
            Map<String, String> map = new HashMap<>();
            map.put("1714", "Home (+" + line + ")");
            map.put("1715", "Away (-" + line + ")");
            addMarket("303", param, quarter + getOrdinalSuffix(quarter) + " quarter - Handicap", map);
        }

        // Quarter Home Team Total: 10.5 to 40.5
        start = 10.5;
        end = 40.5;
        step = 0.5;

        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            String param = "total=" + line + "|quarternr=" + quarter;
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("756", param, quarter + getOrdinalSuffix(quarter) + " quarter - Home O/U", map);
        }

        // Quarter Away Team Total: 10.5 to 40.5
        for (double total = start; total <= end; total += step) {
            String line = formatDecimal(total);
            String param = "total=" + line + "|quarternr=" + quarter;
            Map<String, String> map = new HashMap<>();
            map.put("12", "Over " + line);
            map.put("13", "Under " + line);
            addMarket("757", param, quarter + getOrdinalSuffix(quarter) + " quarter - Away O/U", map);
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