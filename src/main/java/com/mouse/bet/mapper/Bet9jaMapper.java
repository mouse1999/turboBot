package com.mouse.bet.mapper;

import com.mouse.bet.mapper.model.SimilarBookieMapper;

import java.util.HashMap;
import java.util.Map;

public class Bet9jaMapper extends SimilarBookieMapper {
    public Bet9jaMapper() {
        super("Bet9ja");
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
        // BASKETBALL MARKETS
        // ───────────────────────────────────────────────
        // Note: Bet9ja uses market IDs like "LIVEB_12", "LIVEB_OUOT", etc.
        // The outcome IDs are appended (e.g., "LIVEB_12_1", "LIVEB_OUOT@159.5_O")
        // We store market names and outcome names for mapping

        //todo: LIVES_TOTACRNR@2.5_U

        // MONEYLINE (2-Way - Including Overtime)
        // Market ID: LIVEB_12
        Map<String, String> moneyline = new HashMap<>();
        moneyline.put("1", "Home");
        moneyline.put("2", "Away");
        addMarket("LIVEB_12", null, "MONEYLINE", moneyline);

        // 3-WAY (Regular Time)
        // Market ID: LIVEB_1X2N
        Map<String, String> threeWay = new HashMap<>();
        threeWay.put("1", "Home");
        threeWay.put("X", "Draw");
        threeWay.put("2", "Away");
        addMarket("LIVEB_1X2N", null, "3WAY", threeWay);

        // DRAW NO BET (Including Overtime)
        // Market ID: LIVEB_DNB
        Map<String, String> drawNoBet = new HashMap<>();
        drawNoBet.put("1", "Home");
        drawNoBet.put("2", "Away");
        addMarket("LIVEB_DNB", null, "DRAW N0 BET", drawNoBet);

        // DRAW NO BET - 3RD PERIOD
        // Market ID: LIVEB_DNB3PN
        Map<String, String> drawNoBet3P = new HashMap<>();
        drawNoBet3P.put("1", "Home");
        drawNoBet3P.put("2", "Away");
        addMarket("LIVEB_DNB3PN", null, "Draw No Bet - 3rd Period", drawNoBet3P);

        // WILL THERE BE OVERTIME
        // Market ID: LIVEB_OTN
        Map<String, String> overtime = new HashMap<>();
        overtime.put("Yes", "Yes");
        overtime.put("No", "No");
        addMarket("LIVEB_OTN", null, "WILL THERE BE OVERTIME", overtime);

        // ODD/EVEN (Regular Time)
        // Market ID: LIVEB_OEN
        Map<String, String> oddEvenRegular = new HashMap<>();
        oddEvenRegular.put("Odd", "Odd");
        oddEvenRegular.put("Even", "Even");
        addMarket("LIVEB_OEN", null, "ODD/EVEN (REGULAR TIME)", oddEvenRegular);

        // ODD/EVEN (Including Overtime)
        // Market ID: LIVEB_OEOT
        Map<String, String> oddEvenOT = new HashMap<>();
        oddEvenOT.put("OD", "Odd");
        oddEvenOT.put("EV", "Even");
        addMarket("LIVEB_OEOT", null, "Odd/Even (incl. overtime)", oddEvenOT);

        // ODD/EVEN - 3RD PERIOD
        // Market ID: LIVEB_OEP3
        Map<String, String> oddEven3P = new HashMap<>();
        oddEven3P.put("OD", "Odd");
        oddEven3P.put("EV", "Even");
        addMarket("LIVEB_OEP3", null, "Odd/Even - 3rd Period", oddEven3P);

        // Generate all Basketball markets
        generateHandicapIncludingOvertime();
        generateHandicap3rdPeriod();
        generateTotalIncludingOvertime();
        generateTotalRegularTime();
        generateSecondHalfTotal();
        generateAllPeriodMarkets();
        generateTeamTotals();
        generateAdditionalBasketballMarkets();

        // ───────────────────────────────────────────────
        // SOCCER/FOOTBALL MARKETS
        // ───────────────────────────────────────────────
        initializeSoccerMarkets();


        // CORE MATCH MARKETS
        generateTTCoreMarkets();

        // SET-LEVEL MARKETS
        generateTTSetWinner();

        // HANDICAP MARKETS
        generateTTMatchHandicap();
        generateTTPerSetHandicap();            // sets 1-2  (HNDP1N20 / HNDP2N20 style)
        generateTTPeriodHandicap();            // sets 3-7  (HND3PN20 … HND7PN20 style)
        generateTTAlternatePerSetHandicap();   // sets 1-7  (HND1P … HND7P style)
        generateTTMatchGameHandicap();
        generateTTPerSetGameHandicap();        // sets 1-7
        generateTTPointHandicap();

        // TOTAL MARKETS
        generateTTMatchTotal();
        generateTTPerSetTotal();               // sets 1-4  (OU1PN … OU4PN style)
        generateTTAlternatePerSetTotal();      // sets 1-7  (OU1P … OU7P style)
        generateTTTotalPoints();
        generateTTTotalGames();
        generateTTPerSetGameTotal();           // sets 1-5  (G1OUP … G5OUP)

        // ODD / EVEN MARKETS
        generateTTPerSetOddEven();             // sets 1-7  (OE1PN … OE7PN)
        generateTTAlternatePerSetOddEven();    // sets 1-7  (OE1P … OE7P)
        generateTTSet1OddEven();               // G1OE

        // RESULT MARKETS
        generateTTFinalResultBest5();
        generateTTFinalResultBest7();
        generateTTCorrectMatchScore();
        generateTTNumberOfSetsBest5();
        generateTTNumberOfSetsBest7();

        // SETS EXCEEDING SCORE LIMIT
        generateTTSetsExceedingBest5();
        generateTTSetsExceedingBest7();

        // WHO SCORES Nth POINT
        generateTTNthPointScorer();            // sets 1-5
        generateTTNthPointScorer67();          // sets 6-7
    }

    // ──────────────────────────────────────────────────────────────
    // BASKETBALL HANDICAP GENERATORS
    // ──────────────────────────────────────────────────────────────

    private void generateHandicapIncludingOvertime() {
        // Handicap (Including Overtime)
        // Market ID format: LIVEB_12HNDOTN02@{handicap}
        // Outcome suffixes: _1H (Home), _2H (Away)

        for (double hcp = -50.5; hcp <= 50.5; hcp += 0.5) {
            if (hcp == 0) continue;

            String line = formatDecimal(Math.abs(hcp));
            String handicapValue = (hcp < 0 ? "-" : "") + line;
            String marketId = "LIVEB_12HNDOTN02@" + handicapValue;
            Map<String, String> map = new HashMap<>();
            if (hcp < 0) {
                map.put("1H", "Home (-" + line + ")" + " player 1");
                map.put("2H", "Home (-" + line + ")" + " player 2");
            } else {
                map.put("1H", "Home (+" + line + ")" + " player 1");
                map.put("2H", "Home (+" + line + ")" + " player 2");
            }

            addMarket(marketId, null, "Handicap", map);
        }
    }

    private void generateHandicap3rdPeriod() {
        // Handicap - 3rd Period
        // Market ID format: LIVEB_12HND3P@{handicap}
        // Outcome suffixes: _1H (Home), _2H (Away)

        for (double hcp = -20.5; hcp <= 20.5; hcp += 0.5) {
            if (hcp == 0) continue;

            String line = formatDecimal(Math.abs(hcp));
            String handicapValue = (hcp < 0 ? "-" : "") + line;
            String marketId = "LIVEB_12HND3P@" + handicapValue;

            Map<String, String> map = new HashMap<>();
            if (hcp < 0) {
                map.put("1H", "Home (-" + line + ")" + " player 1");
                map.put("2H", "Home (-" + line + ")" + " player 2");
            } else {
                map.put("1H", "Home (+" + line + ")" + " player 1");
                map.put("2H", "Home (+" + line + ")" + " player 2");
            }

            addMarket(marketId, null, "Handicap for third period", map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // BASKETBALL TOTALS GENERATORS
    // ──────────────────────────────────────────────────────────────

    private void generateTotalIncludingOvertime() {
        // Total (Including Overtime)
        // Market ID format: LIVEB_OUOT@{total}
        // Outcome suffixes: _O (Over), _U (Under)

        for (double total = 30.5; total <= 280.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVEB_OUOT@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, "Total for whole match, including overtime", map);
        }
    }

    private void generateTotalRegularTime() {
        // Total (Regular Time)
        // Market ID format: LIVEB_OU@{total}
        // Outcome suffixes: _O (Over), _U (Under)

        for (double total = 20.5; total <= 300.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVEB_OU@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, "Total (Regular Time)", map);
        }
    }

    private void generateSecondHalfTotal() {
        // 2nd Half Total (Including OT)
        // Market ID format: LIVEB_2TOU@{total}
        // Outcome suffixes: _Over, _Under

        for (double total = 30.5; total <= 140.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVEB_2TOU@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("Over", "Over " + line);
            map.put("Under", "Under " + line);
            addMarket(marketId, null, "2nd Half - Total (incl. OT)", map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // ALL PERIOD/QUARTER MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateAllPeriodMarkets() {
        // Generate for quarters 1-4
        for (int quarter = 1; quarter <= 4; quarter++) {
            generatePeriod3Way(quarter);
            generatePeriodDrawNoBet(quarter);
            generatePeriodHandicap(quarter);
            generatePeriodTotal(quarter);
            generatePeriodOddEven(quarter);
            generatePeriodWinningMargin(quarter);
            generatePeriodRaceToPoints(quarter);
        }

        // Generate half markets
        generateFirstHalfMarkets();
        generateSecondHalfMarkets();
    }

    private void generatePeriod3Way(int quarter) {
        // Period 3-Way
        // Market ID format: LIVEB_1X2P{quarter}
        // Outcome suffixes: _1 (Home), _X (Draw), _2 (Away)

        String marketName = getOrdinalName(quarter) + " Period - 3-Way";
        String marketId = "LIVEB_1X2P" + quarter;

        Map<String, String> map = new HashMap<>();
        map.put("1", "Home");
        map.put("X", "Draw");
        map.put("2", "Away");
        addMarket(marketId, null, marketName, map);
    }

    private void generatePeriodDrawNoBet(int quarter) {
        // Period Draw No Bet
        // Market ID format: LIVEB_DNBP{quarter}
        // Outcome suffixes: _1 (Home), _2 (Away)

//        String marketName = getOrdinalName(quarter) + "Draw No Bet" + "";
        // Corrected syntax
        String marketName = String.format("Draw No Bet For %s Period", convertFromOrdinal(getOrdinalName(quarter)));

        //Draw no Bet for third period
        String marketId = "LIVEB_DNBP" + quarter;

        Map<String, String> map = new HashMap<>();
        map.put("1", "Home");
        map.put("2", "Away");
        addMarket(marketId, null, marketName, map);
    }

    private void generatePeriodHandicap(int quarter) {
        // Period Handicap
        // Market ID format: LIVEB_12HNDP{quarter}@{handicap}
        // Outcome suffixes: _1H (Home), _2H (Away)

        // Corrected syntax
        String marketName = String.format("Handicap For %s Period", convertFromOrdinal(getOrdinalName(quarter)));

        for (double hcp = -15.5; hcp <= 15.5; hcp += 0.5) {
            if (hcp == 0) continue;

            String line = formatDecimal(Math.abs(hcp));
            String handicapValue = (hcp < 0 ? "-" : "") + line;
            String marketId = "LIVEB_12HNDP" + quarter + "@" + handicapValue;

            Map<String, String> map = new HashMap<>();
            if (hcp < 0) {
                map.put("1H", "Home (-" + line + ")" + " player 1");
                map.put("2H", "Away (+" + line + ")" + " player 2");
            } else {
                map.put("1H", "Home (+" + line + ")" + " player 1");
                map.put("2H", "Away (-" + line + ")" + " player 2");
            }

            addMarket(marketId, null, marketName, map);
        }
    }

    private void generatePeriodTotal(int quarter) { //todo
        // Period Total
        // Market ID format: LIVEB_OUP{quarter}@{total}
        // Outcome suffixes: _O (Over), _U (Under)

        String marketName = getOrdinalName(quarter) + " Period - Total";

        for (double total = 30.5; total <= 80.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVEB_OUP" + quarter + "@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, marketName, map);
        }
    }

    private void generatePeriodOddEven(int quarter) {
        // Period Odd/Even
        // Market ID format: LIVEB_OEP{quarter}
        // Outcome suffixes: _OD (Odd), _EV (Even)

//        String marketName = getOrdinalName(quarter) + " Period - Odd/Even";

        String marketName = String.format("Odd/Even For %s Period", convertFromOrdinal(getOrdinalName(quarter)));
        String marketId = "LIVEB_OEP" + quarter;

        Map<String, String> map = new HashMap<>();
        map.put("OD", "Odd");
        map.put("EV", "Even");
        addMarket(marketId, null, marketName, map);
    }

    private void generatePeriodWinningMargin(int quarter) {
        // Period Winning Margin
        // Market ID format: LIVEB_WMP{quarter}
        // Outcome suffixes vary by margin range

        String marketName = getOrdinalName(quarter) + " Period - Winning Margin";
        String marketId = "LIVEB_WMP" + quarter;

        Map<String, String> map = new HashMap<>();
        map.put("1H1-3", "Home by 1-3");
        map.put("1H4-6", "Home by 4-6");
        map.put("1H7-9", "Home by 7-9");
        map.put("1H10+", "Home by 10+");
        map.put("2H1-3", "Away by 1-3");
        map.put("2H4-6", "Away by 4-6");
        map.put("2H7-9", "Away by 7-9");
        map.put("2H10+", "Away by 10+");
        map.put("Draw", "Draw");
        addMarket(marketId, null, marketName, map);
    }

    private void generatePeriodRaceToPoints(int quarter) {
        // Period Race to Points
        // Market ID format: LIVEB_RTP{quarter}@{points}
        // Outcome suffixes: _1 (Home), _2 (Away), _None (None for 3-way)

        int[] racePoints = {5, 10, 15, 20, 25, 30};

        for (int points : racePoints) {
            String marketName = getOrdinalName(quarter) + " Period - Race to " + points + " Points";
            String marketId = "LIVEB_RTP" + quarter + "@" + points;

            // 2-way version
            Map<String, String> map2way = new HashMap<>();
            map2way.put("1", "Home");
            map2way.put("2", "Away");
            addMarket(marketId, null, marketName, map2way);

            // 3-way version (with None)
            String marketId3way = marketId + "_3W";
            Map<String, String> map3way = new HashMap<>();
            map3way.put("1", "Home");
            map3way.put("None", "None");
            map3way.put("2", "Away");
            addMarket(marketId3way, null, marketName + " (3-way)", map3way);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // HALF MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateFirstHalfMarkets() {
        // 1st Half - 3-Way
        // Market ID: LIVEB_1X21T
        Map<String, String> map1X2 = new HashMap<>();
        map1X2.put("1", "Home");
        map1X2.put("X", "Draw");
        map1X2.put("2", "Away");
        addMarket("LIVEB_1X21T", null, "1st Half - 3-Way", map1X2);

        // 1st Half - Draw No Bet
        // Market ID: LIVEB_DNB1T
        Map<String, String> mapDNB = new HashMap<>();
        mapDNB.put("1", "Home");
        mapDNB.put("2", "Away");
        addMarket("LIVEB_DNB1T", null, "Draw No Bet first half", mapDNB);

        // 1st Half - Odd/Even
        // Market ID: LIVEB_OE1T
        Map<String, String> mapOE = new HashMap<>();
        mapOE.put("OD", "Odd");
        mapOE.put("EV", "Even");
        addMarket("LIVEB_OE1T", null, "Odd/Even for first half", mapOE);

        // 1st Half - Handicap
        generateFirstHalfHandicap();

        // 1st Half - Total
        generateFirstHalfTotal();
    }

    private void generateFirstHalfHandicap() {
        // 1st Half Handicap
        // Market ID format: LIVEB_12HND1T@{handicap}
        // Outcome suffixes: _1H (Home), _2H (Away)

        for (double hcp = -20.5; hcp <= 20.5; hcp += 0.5) {
            if (hcp == 0) continue;

            String line = formatDecimal(Math.abs(hcp));
            String handicapValue = (hcp < 0 ? "-" : "") + line;
            String marketId = "LIVEB_12HND1T@" + handicapValue;

            Map<String, String> map = new HashMap<>();
            if (hcp < 0) {
                map.put("1H", "Home (-" + line + ")" + " player 1");
                map.put("2H", "Home (-" + line + ")" + " player 2");
            } else {
                map.put("1H", "Home (+" + line + ")" + " player 1");
                map.put("2H", "Home (+" + line + ")" + " player 2");
            }

            addMarket(marketId, null, "Handicap first half", map);
        }
    }

    private void generateFirstHalfTotal() {
        // 1st Half Total
        // Market ID format: LIVEB_OU1T@{total}
        // Outcome suffixes: _O (Over), _U (Under)

        for (double total = 30.5; total <= 140.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVEB_OU1T@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, "Halftime - Total", map);
        }
    }

    private void generateSecondHalfMarkets() { //todo
        // 2nd Half - 3-Way (incl. overtime)
        // Market ID: LIVEB_1X22T
        Map<String, String> map1X2 = new HashMap<>();
        map1X2.put("1", "Home");
        map1X2.put("X", "Draw");
        map1X2.put("2", "Away");
        addMarket("LIVEB_1X22T", null, "2nd Half - 3-Way (incl. overtime)", map1X2);

        // 2nd Half - Draw No Bet (incl. overtime)
        // Market ID: LIVEB_DNB2T
        Map<String, String> mapDNB = new HashMap<>();
        mapDNB.put("1", "Home");
        mapDNB.put("2", "Away");
        addMarket("LIVEB_DNB2T", null, "2nd Half - Draw No Bet (incl. overtime)", mapDNB);

        // 2nd Half - Odd/Even (incl. overtime)
        // Market ID: LIVEB_OE2T
        Map<String, String> mapOE = new HashMap<>();
        mapOE.put("OD", "Odd");
        mapOE.put("EV", "Even");
        addMarket("LIVEB_OE2T", null, "2nd Half - Odd/Even (incl. overtime)", mapOE);

        // 2nd Half - Handicap
        generateSecondHalfHandicap();
    }

    private void generateSecondHalfHandicap() {
        // 2nd Half Handicap (incl. overtime)
        // Market ID format: LIVEB_12HND2T@{handicap}
        // Outcome suffixes: _1H (Home), _2H (Away)

        for (double hcp = -20.5; hcp <= 20.5; hcp += 0.5) {
            if (hcp == 0) continue;

            String line = formatDecimal(Math.abs(hcp));
            String handicapValue = (hcp < 0 ? "-" : "") + line;
            String marketId = "LIVEB_12HND2T@" + handicapValue;

            Map<String, String> map = new HashMap<>();
            if (hcp < 0) {
                map.put("1H", "Home -" + line);
                map.put("2H", "Away +" + line);
            } else {
                map.put("1H", "Home +" + line);
                map.put("2H", "Away -" + line);
            }

            addMarket(marketId, null, "2nd Half - Handicap (incl. overtime)", map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TEAM TOTALS
    // ──────────────────────────────────────────────────────────────

    private void generateTeamTotals() { //todo
        // Home Team Total (Including Overtime)
        // Market ID format: LIVEB_HTOT@{total}
        for (double total = 20.5; total <= 200.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVEB_HTOT@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, "Home Team Total (incl. overtime)", map);
        }

        // Away Team Total (Including Overtime)
        // Market ID format: LIVEB_ATOT@{total}
        for (double total = 20.5; total <= 200.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVEB_ATOT@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, "Away Team Total (incl. overtime)", map);
        }

        // Home Team Total (Regular Time)
        // Market ID format: LIVEB_HT@{total}
        for (double total = 20.5; total <= 200.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVEB_HT@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, "Home Team Total (Regular Time)", map);
        }

        // Away Team Total (Regular Time)
        // Market ID format: LIVEB_AT@{total}
        for (double total = 40.5; total <= 140.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVEB_AT@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, "Away Team Total (Regular Time)", map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // ADDITIONAL BASKETBALL MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateAdditionalBasketballMarkets() {
        // Winning Margin (Full Game)
        generateWinningMargin();

        // Race to Points (Full Game)
        generateRaceToPoints();

        // Highest Scoring Quarter
        generateHighestScoringQuarter();

        // Highest Scoring Half
        generateHighestScoringHalf();

        // Double Result
        generateDoubleResult();
    }

    private void generateWinningMargin() {
        // Winning Margin (incl. overtime)
        // Market ID: LIVEB_WM
        Map<String, String> map = new HashMap<>();
        map.put("1H1-5", "Home by 1-5");
        map.put("1H6-10", "Home by 6-10");
        map.put("1H11-15", "Home by 11-15");
        map.put("1H16-20", "Home by 16-20");
        map.put("1H21+", "Home by 21+");
        map.put("2H1-5", "Away by 1-5");
        map.put("2H6-10", "Away by 6-10");
        map.put("2H11-15", "Away by 11-15");
        map.put("2H16-20", "Away by 16-20");
        map.put("2H21+", "Away by 21+");
        addMarket("LIVEB_WM", null, "Winning Margin (incl. overtime)", map);
    }

    private void generateRaceToPoints() {
        // Full game race to points: 10, 20, 30, 40, 50
        int[] racePoints = {10, 20, 30, 40, 50};

        for (int points : racePoints) {
            String marketId = "LIVEB_RT@" + points;

            // 2-way version
            Map<String, String> map2way = new HashMap<>();
            map2way.put("1", "Home");
            map2way.put("2", "Away");
            addMarket(marketId, null, "Race to " + points + " Points (incl. overtime)", map2way);

            // 3-way version
            String marketId3way = marketId + "_3W";
            Map<String, String> map3way = new HashMap<>();
            map3way.put("1", "Home");
            map3way.put("None", "None");
            map3way.put("2", "Away");
            addMarket(marketId3way, null, "Race to " + points + " Points (3-way)", map3way);
        }
    }

    private void generateHighestScoringQuarter() {
        // Highest Scoring Quarter
        // Market ID: LIVEB_HSQ
        Map<String, String> map = new HashMap<>();
        map.put("Q1", "1st Quarter");
        map.put("Q2", "2nd Quarter");
        map.put("Q3", "3rd Quarter");
        map.put("Q4", "4th Quarter");
        map.put("Equal", "Equal");
        addMarket("LIVEB_HSQ", null, "Highest Scoring Quarter", map);
    }

    private void generateHighestScoringHalf() {
        // Highest Scoring Half
        // Market ID: LIVEB_HSH
        Map<String, String> map = new HashMap<>();
        map.put("1H", "1st Half");
        map.put("2H", "2nd Half");
        map.put("Equal", "Equal");
        addMarket("LIVEB_HSH", null, "Highest Scoring Half", map);
    }

    private void generateDoubleResult() {
        // Double Result (HT/FT)
        // Market ID: LIVEB_DR
        Map<String, String> map = new HashMap<>();
        map.put("1/1", "Home/Home");
        map.put("1/X", "Home/Draw");
        map.put("1/2", "Home/Away");
        map.put("X/1", "Draw/Home");
        map.put("X/X", "Draw/Draw");
        map.put("X/2", "Draw/Away");
        map.put("2/1", "Away/Home");
        map.put("2/X", "Away/Draw");
        map.put("2/2", "Away/Away");
        addMarket("LIVEB_DR", null, "Double Result (HT/FT)", map);
    }

    // ──────────────────────────────────────────────────────────────
    // UTILITY METHODS
    // ──────────────────────────────────────────────────────────────

    private String formatDecimal(double value) {
        return String.format("%.1f", value);
    }

    private String getOrdinalName(int number) {
        String[] ordinals = {"", "1st", "2nd", "3rd", "4th"};
        if (number >= 1 && number <= 4) {
            return ordinals[number];
        }
        return number + "th";
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

    // ══════════════════════════════════════════════════════════════
    // SOCCER/FOOTBALL MARKETS
    // ══════════════════════════════════════════════════════════════

    private void initializeSoccerMarkets() {
        // CORE SOCCER MARKETS
        generateSoccerCoreMarkets();

        // HALFTIME MARKETS
        generateSoccerHalftimeMarkets();

        // GOALS MARKETS
        generateSoccerGoalsMarkets();

        // HANDICAP & TOTALS
        generateSoccerHandicapMarkets();
        generateSoccerTotalMarkets();

        // CORNER MARKETS
        generateSoccerCornerMarkets();

        // CARD/BOOKING MARKETS
        generateSoccerCardMarkets();

        // TIME INTERVAL MARKETS
        generateSoccerTimeIntervalMarkets();

        // PLAYER MARKETS
        generateSoccerPlayerMarkets();
    }

    // ──────────────────────────────────────────────────────────────
    // SOCCER CORE MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateSoccerCoreMarkets() {
        // 1X2 (Match Result)
        Map<String, String> match1x2 = new HashMap<>();
        match1x2.put("1", "Home");
        match1x2.put("X", "Draw");
        match1x2.put("2", "Away");
        addMarket("LIVES_1X2", null, "3way", match1x2);

        // Double Chance
        Map<String, String> dc = new HashMap<>();
        dc.put("1X", "1X");
        dc.put("12", "12");
        dc.put("X2", "X2");
        addMarket("LIVES_DC", null, "Double Chance", dc);

        // Draw No Bet
        Map<String, String> dnb = new HashMap<>();
        dnb.put("1", "Home");
        dnb.put("2", "Away");
        addMarket("LIVES_DNB", null, "Draw no bet", dnb);

        // Both Teams to Score (GGNG)
        Map<String, String> ggng = new HashMap<>();
        ggng.put("Y", "GG");
        ggng.put("N", "NG");
        addMarket("LIVES_GGNG", null, "Goal / No Goal", ggng);

        // Clean Sheet Home
        Map<String, String> cleanHome = new HashMap<>();
        cleanHome.put("Yes", "Yes");
        cleanHome.put("No", "No");
        addMarket("LIVES_CLEANHOME", null, "Clean Sheet Home", cleanHome);

        // Clean Sheet Away
        Map<String, String> cleanAway = new HashMap<>();
        cleanAway.put("Yes", "Yes");
        cleanAway.put("No", "No");
        addMarket("LIVES_CLEANAWAY", null, "Clean Sheet Away", cleanAway);

        // Which Team to Score
        Map<String, String> teamScore = new HashMap<>();
        teamScore.put("1", "Only Home");
        teamScore.put("2", "Only Away");
        teamScore.put("Both", "Both");
        teamScore.put("Neither", "Neither");
        addMarket("LIVES_12SCORE", null, "Which Team to Score", teamScore);
    }

    // ──────────────────────────────────────────────────────────────
    // SOCCER HALFTIME MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateSoccerHalftimeMarkets() {
        // 1X2 Halftime
        Map<String, String> ht1x2 = new HashMap<>();
        ht1x2.put("1", "1");
        ht1x2.put("X", "X");
        ht1x2.put("2", "2");
        addMarket("LIVES_1X21T", null, "Halftime - 3way", ht1x2);

        // Halftime/Fulltime
        Map<String, String> htft = new HashMap<>();
        htft.put("Home/Home", "Home/Home");
        htft.put("Home/Draw", "Home/Draw");
        htft.put("Home/Away", "Home/Away");
        htft.put("Draw/Home", "Draw/Home");
        htft.put("Draw/Draw", "Draw/Draw");
        htft.put("Draw/Away", "Draw/Away");
        htft.put("Away/Home", "Away/Home");
        htft.put("Away/Draw", "Away/Draw");
        htft.put("Away/Away", "Away/Away");
        addMarket("LIVES_HTFT", null, "Halftime/Fulltime", htft);

        // Halftime Double Chance
        Map<String, String> htdc = new HashMap<>();
        htdc.put("1X", "1X");
        htdc.put("12", "12");
        htdc.put("X2", "X2");
        addMarket("LIVES_DCHT", null, "Halftime - Double chance", htdc);

        // 1st Half Draw No Bet
        Map<String, String> htDnb = new HashMap<>();
        htDnb.put("Home", "Home");
        htDnb.put("Away", "Away");
        addMarket("LIVES_DNB1TN", null, "1st Half Draw No Bet", htDnb);

        // 1st Half Both Teams to Score
        Map<String, String> htGgng = new HashMap<>();
        htGgng.put("Y", "Yes");
        htGgng.put("N", "No");
        addMarket("LIVES_GGNG1T", null, "1st Half - Both teams to score", htGgng);
    }

    // ──────────────────────────────────────────────────────────────
    // SOCCER GOALS MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateSoccerGoalsMarkets() {
        // Total Goals (Exact)
        for (int goals = 0; goals <= 6; goals++) {
            Map<String, String> map = new HashMap<>();
            String key = String.valueOf(goals);
            if (goals == 6) {
                map.put("6+", "6+");
                addMarket("LIVES_EXACTGOALN", null, "Total Goals (Exact)", map);
            } else {
                map.put(key, key);
                addMarket("LIVES_GOALS", null, "Total Goals", map);
            }
        }

        // Total Goals Bands
        Map<String, String> goalBands = new HashMap<>();
        goalBands.put("0-1", "0-1");
        goalBands.put("2-3", "2-3");
        goalBands.put("4-6", "4-6");
        goalBands.put("7+", "7+");
        addMarket("LIVES_GOALSBAN", null, "Total Goals (Bands)", goalBands);

        // Home Team Goals
        Map<String, String> homeGoals = new HashMap<>();
        homeGoals.put("0", "0");
        homeGoals.put("1", "1");
        homeGoals.put("2", "2");
        homeGoals.put("3+", "3+");
        addMarket("LIVES_GOALSHOME", null, "Home Team Goals", homeGoals);

        // Away Team Goals
        Map<String, String> awayGoals = new HashMap<>();
        awayGoals.put("0", "0");
        awayGoals.put("1", "1");
        awayGoals.put("2", "2");
        awayGoals.put("3+", "3+");
        addMarket("LIVES_GOALSAWAY", null, "Away Team Goals", awayGoals);

        // Halftime Total Goals
        Map<String, String> htGoals = new HashMap<>();
        for (int i = 0; i <= 5; i++) {
            htGoals.put(String.valueOf(i), String.valueOf(i));
        }
        addMarket("LIVES_HTG", null, "Halftime - Total Goals", htGoals);

        // Highest Scoring Half
        Map<String, String> highHalf = new HashMap<>();
        highHalf.put("1", "1st Half");
        highHalf.put("E", "Equal");
        highHalf.put("2", "2nd Half");
        addMarket("LIVES_HIGHHALF", null, "Highest Scoring Half", highHalf);
    }

    // ──────────────────────────────────────────────────────────────
    // SOCCER HANDICAP MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateSoccerHandicapMarkets() {
        // Asian Handicap
        double[] handicaps = {-1.0,-1.5, -2.0, -2.5, -3.0, -3.5, -4.0, -4.5, 5.0, -5.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5};

        for (double hcp : handicaps) {
            if (hcp == 0.0) continue;

            String hcpStr = formatDecimal(Math.abs(hcp));
            String marketId = "LIVES_12HNDN@" + (hcp < 0 ? "-" : "") + hcpStr;

            Map<String, String> map = new HashMap<>();
            map.put("1H", "Home");
            map.put("2H", "Away");
            addMarket(marketId, null, "Asian Handicap", map);
        }

        // 3-Way Handicap
        for (double hcp : new double[]{-1.0,-1.5, -2.0, -2.5, -3.0, -3.5, -4.0, -4.5, 5.0, -5.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5}) {
            String hcpStr = formatDecimal(Math.abs(hcp));
            String marketId = "LIVES_1X2HNDHN1@" + (hcp < 0 ? "-" : "") + hcpStr;

            Map<String, String> map = new HashMap<>();
            map.put("1H", "Home");
            map.put("XH", "Draw");
            map.put("2H", "Away");
            addMarket(marketId, null, "3-Way Handicap", map);
        }

        // Halftime Handicap
        for (double hcp : new double[]{-1.0,-1.5, -2.0, -2.5, -3.0, -3.5, -4.0, -4.5, 5.0, -5.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5}) {
            String hcpStr = formatDecimal(Math.abs(hcp));
            String marketId = "LIVES_1X2HTN@" + (hcp < 0 ? "-" : "") + hcpStr;

            Map<String, String> map = new HashMap<>();
            map.put("Home", "Home");
            map.put("HandicapTie", "Draw");
            map.put("Away", "Away");
            addMarket(marketId, null, "Halftime - Handicap", map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // SOCCER TOTAL MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateSoccerTotalMarkets() {
        // Over/Under Full Time
        double[] totals = {0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5};

        for (double total : totals) {
            String totalStr = formatDecimal(total);
            String marketId = "LIVES_OU@" + totalStr;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + totalStr);
            map.put("U", "Under " + totalStr);
            addMarket(marketId, null, "Total", map);
        }

        // Over/Under 1st Half
        for (double total : new double[]{1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5}) {
            String totalStr = formatDecimal(total);
            String marketId = "LIVES_OU1T@" + totalStr;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + totalStr);
            map.put("U", "Under " + totalStr);
            addMarket(marketId, null, "Halftime - Total", map);
        }

        // Over/Under 2nd Half
        for (double total : new double[]{1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5}) {
            String totalStr = formatDecimal(total);
            String marketId = "LIVES_OU2T@" + totalStr;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + totalStr);
            map.put("U", "Under " + totalStr);
            addMarket(marketId, null, "2nd Half - Total", map);
        }

        // Home Team Total
        for (double total : new double[]{1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5}) {
            String totalStr = formatDecimal(total);
            String marketId = "LIVES_OUHOME@" + totalStr;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + totalStr);
            map.put("U", "Under " + totalStr);
            addMarket(marketId, null, "Home Team Total", map);
        }

        // Away Team Total
        for (double total : new double[]{1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5}) {
            String totalStr = formatDecimal(total);
            String marketId = "LIVES_OUAWAY@" + totalStr;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + totalStr);
            map.put("U", "Under " + totalStr);
            addMarket(marketId, null, "Away Team Total", map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // SOCCER CORNER MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateSoccerCornerMarkets() { //todo
        // Total Corners
        double[] cornerTotals = {1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5, 9.5, 10.5};

        for (double total : cornerTotals) {
            String totalStr = formatDecimal(total);
            String marketId = "LIVES_OUCORNER@" + totalStr;

            Map<String, String> map = new HashMap<>();
            map.put("Over", "Over " + totalStr);
            map.put("Under", "Under " + totalStr);
            addMarket(marketId, null, "Total Corners", map);
        }

        // Corner Bands
        Map<String, String> cornerBands = new HashMap<>();
        cornerBands.put("0-8", "0-8");
        cornerBands.put("9-11", "9-11");
        cornerBands.put("12+", "12+");
        addMarket("LIVES_CORNER", null, "Corner Bands", cornerBands);

        // Odd/Even Corners
        Map<String, String> oeCorner = new HashMap<>();
        oeCorner.put("Odd", "Odd");
        oeCorner.put("Even", "Even");
        addMarket("LIVES_OECORNERS", null, "Odd/Even Corners", oeCorner);

        // Home Team Corners Total
        for (double total : new double[]{1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5}) {
            String totalStr = formatDecimal(total);
            String marketId = "LIVES_CORNEROUH@" + totalStr;

            Map<String, String> map = new HashMap<>();
            map.put("HOver", "Over " + totalStr);
            map.put("HUnder", "Under " + totalStr);
            addMarket(marketId, null, "Home Corners Total", map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // SOCCER CARD/BOOKING MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateSoccerCardMarkets() { //todo
        // Total Cards
        double[] cardTotals = {1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5};

        for (double total : cardTotals) {
            String totalStr = formatDecimal(total);
            String marketId = "LIVES_TOTACARDSS@" + totalStr;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + totalStr);
            map.put("U", "Under " + totalStr);
            addMarket(marketId, null, "Total Cards", map);
        }

        // Card Bands
        Map<String, String> cardBands = new HashMap<>();
        cardBands.put("0-2", "0-2");
        cardBands.put("3-5", "3-5");
        cardBands.put("6+", "6+");
        addMarket("LIVES_TOTCARDB", null, "Total Cards (Bands)", cardBands);

        // Odd/Even Cards Home
        Map<String, String> oeCardH = new HashMap<>();
        oeCardH.put("Odd", "Odd");
        oeCardH.put("Even", "Even");
        addMarket("LIVES_OECARDH", null, "Odd/Even Cards Home", oeCardH);

        // Odd/Even Cards Away
        Map<String, String> oeCardA = new HashMap<>();
        oeCardA.put("Odd", "Odd");
        oeCardA.put("Even", "Even");
        addMarket("LIVES_OECARDA", null, "Odd/Even Cards Away", oeCardA);
    }

    // ──────────────────────────────────────────────────────────────
    // SOCCER TIME INTERVAL MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateSoccerTimeIntervalMarkets() {
        // Next Goal
        Map<String, String> nextGoal = new HashMap<>();
        nextGoal.put("1", "Home");
        nextGoal.put("X", "No Goal");
        nextGoal.put("2", "Away");
        addMarket("LIVES_NEXTGOAL", null, "Next Goal", nextGoal);

        // X Minutes 1X2 Markets
        int[] intervals = {10, 15, 20, 25, 30, 35, 40, 50, 55, 60, 65, 70, 75};

        for (int mins : intervals) {
            String marketId = "LIVES_" + mins + "MIN1X2";

            Map<String, String> map = new HashMap<>();
            map.put("1", "Home");
            map.put("X", "Draw");
            map.put("2", "Away");
            addMarket(marketId, null, mins + " Minutes - 1X2", map);
        }

        // X Minutes O/U Markets
        for (int mins : intervals) {
            String marketId = "LIVES_" + mins + "MINOU";

            for (double total : new double[]{0.5, 1.5}) {
                String totalStr = formatDecimal(total);
                String marketIdWithTotal = marketId + "@" + totalStr;

                Map<String, String> map = new HashMap<>();
                map.put("O", "Over "+ totalStr);
                map.put("U", "Under " + totalStr);
                addMarket(marketIdWithTotal, null, mins + " Minutes - Over/Under", map);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // SOCCER PLAYER MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateSoccerPlayerMarkets() {
        // Note: Player markets use dynamic player IDs
        // Format: LIVES_[MARKET]@[PLAYER_ID]:[PLAYER_NAME]
        // These are handled dynamically, so we just document the structure

        // Examples (not exhaustive):
        // - LIVES_AG1@[ID]:[NAME] - Anytime Goalscorer
        // - LIVES_NG@[ID]:[NAME]#1 - Next Goalscorer
        // - LIVES_AASSIST@[ID]:[NAME] - Assist
        // - LIVES_TOTSHOTOU@[ID]:[NAME]#[VALUE] - Total Shots Over/Under
        // - LIVES_TOTSHOTTARGETOU@[ID]:[NAME]#[VALUE] - Shots on Target Over/Under
        // - LIVES_TOTATSHOT@[ID]:[NAME]#[VALUE] - At Least X Shots
        // - LIVES_TOTATSHOTTARGET@[ID]:[NAME]#[VALUE] - At Least X Shots on Target
        // - LIVES_PS2@[ID]:[NAME] - Player Sent Off
        // - LIVES_PS3@[ID]:[NAME] - Player Booked
        // - LIVES_ANYREDCARDS@[ID]:[NAME] - Any Red Cards

        // These markets are generated dynamically based on available players
        // The mapper will handle them when encountered in the odds data
    }


    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – CORE MATCH MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateTTCoreMarkets() {
        // Match Winner
        // Market ID: LIVETT_12
        // Outcome suffixes: _1HH (Player 1), _2HH (Player 2)
        Map<String, String> matchWinner = new HashMap<>();
        matchWinner.put("1HH", "1");
        matchWinner.put("2HH", "2");
        addMarket("LIVETT_12", null, "Match Winner", matchWinner);
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – SET WINNER
    // ──────────────────────────────────────────────────────────────

    private void generateTTSetWinner() {
        // Set Winner
        // Market ID format: LIVETT_SW@{setNumber}
        // Outcome suffixes: _1 (Player 1), _2 (Player 2)

        for (int set = 1; set <= 7; set++) {
            String marketId = "LIVETT_SW@" + set;

            Map<String, String> map = new HashMap<>();
            map.put("1", "1");
            map.put("2", "2");
            addMarket(marketId, null, "Set " + "Winner", map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – HANDICAP MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateTTMatchHandicap() {
        // Asian Handicap (Match)
        // Market ID format: LIVETT_12HNDN20@{handicap}
        // Outcome suffixes: _1H (Player 1), _2H (Player 2)

        for (double hcp = -15.5; hcp <= 15.5; hcp += 0.5) {
            if (hcp == 0) continue;

            String line = formatDecimal(Math.abs(hcp));
            String handicapValue = (hcp < 0 ? "-" : "") + line;
            String marketId = "LIVETT_12HNDN20@" + handicapValue;

            Map<String, String> map = new HashMap<>();
            if (hcp < 0) {
                map.put("1H", "Home (-" + line + ")" + " player 1");
                map.put("2H", "Home (-" + line + ")" + " player 2");
            } else {
                map.put("1H", "Home (+" + line + ")" + " player 1");
                map.put("2H", "Home (+" + line + ")" + " player 2");
            }

            addMarket(marketId, null, "Asian Handicap", map);
        }
    }

    private void generateTTPerSetHandicap() {
        // Asian Handicap – 1st / 2nd Period
        // Market ID format: LIVETT_12HNDP{set}N20@{handicap}   (set = 1 or 2)
        // Outcome suffixes: _1H (Player 1), _2H (Player 2)

        for (int set = 1; set <= 2; set++) {
            for (double hcp = -50.5; hcp <= 50.5; hcp += 0.5) {
                if (hcp == 0) continue;

                String line = formatDecimal(Math.abs(hcp));
                String handicapValue = (hcp < 0 ? "-" : "") + line;
                String marketId = "LIVETT_12HNDP" + set + "N20@" + handicapValue;

                Map<String, String> map = new HashMap<>();
                if (hcp < 0) {
                    map.put("1H", "Home (-" + line + ")" + " player 1");
                    map.put("2H", "Home (-" + line + ")" + " player 2");
                } else {
                    map.put("1H", "Home (+" + line + ")" + " player 1");
                    map.put("2H", "Home (+" + line + ")" + " player 2");
                }

                addMarket(marketId, null, getTTSetOrdinal(set) + " Set - Asian Handicap", map);
            }
        }
    }

    private void generateTTPeriodHandicap() {
        // Asian Handicap – 3rd to 7th Period
        // Market ID format: LIVETT_12HND{set}PN20@{handicap}   (set = 3 … 7)
        // Outcome suffixes: _1H (Player 1), _2H (Player 2)

        for (int set = 3; set <= 7; set++) {
            for (double hcp = -40.5; hcp <= 40.5; hcp += 0.5) {
                if (hcp == 0) continue;

                String line = formatDecimal(Math.abs(hcp));
                String handicapValue = (hcp < 0 ? "-" : "") + line;
                String marketId = "LIVETT_12HND" + set + "PN20@" + handicapValue;

                Map<String, String> map = new HashMap<>();
                if (hcp < 0) {
                    map.put("1H", "Home (-" + line + ")" + " player 1");
                    map.put("2H", "Home (-" + line + ")" + " player 2");
                } else {
                    map.put("1H", "Home (+" + line + ")" + " player 1");
                    map.put("2H", "Home (+" + line + ")" + " player 2");
                }

                addMarket(marketId, null, getTTSetOrdinal(set) + " Set - Asian Handicap", map);
            }
        }
    }

    private void generateTTAlternatePerSetHandicap() {
        // Asian Handicap – per period (alternate ID style)
        // Market ID format: LIVETT_12HND{set}P@{handicap}   (set = 1 … 7)
        // Outcome suffixes: _1H (Player 1), _2H (Player 2)

        for (int set = 1; set <= 7; set++) {
            for (double hcp = -50.5; hcp <= 50.5; hcp += 0.5) {
                if (hcp == 0) continue;

                String line = formatDecimal(Math.abs(hcp));
                String handicapValue = (hcp < 0 ? "-" : "") + line;
                String marketId = "LIVETT_12HND" + set + "P@" + handicapValue;

                Map<String, String> map = new HashMap<>();
                if (hcp < 0) {
                    map.put("1H", "Home (-" + line + ")" + " player 1");
                    map.put("2H", "Home (-" + line + ")" + " player 2");
                } else {
                    map.put("1H", "Home (+" + line + ")" + " player 1");
                    map.put("2H", "Home (+" + line + ")" + " player 2");
                }

                addMarket(marketId, null, getTTSetOrdinal(set) + " Set - Asian Handicap", map);
            }
        }
    }

    private void generateTTMatchGameHandicap() {
        // Match Game Handicap
        // Market ID format: LIVETT_12GHND@{handicap}
        // Outcome suffixes: _1 (Player 1), _2 (Player 2)

        for (double hcp = -20.5; hcp <= 20.5; hcp += 0.5) {
            if (hcp == 0) continue;

            String line = formatDecimal(Math.abs(hcp));
            String handicapValue = (hcp < 0 ? "-" : "") + line;
            String marketId = "LIVETT_12GHND@" + handicapValue;

            Map<String, String> map = new HashMap<>();
            if (hcp < 0) {
                map.put("1H", "Home (-" + line + ")" + " player 1");
                map.put("2H", "Home (-" + line + ")" + " player 2");
            } else {
                map.put("1H", "Home (+" + line + ")" + " player 1");
                map.put("2H", "Home (+" + line + ")" + " player 2");
            }

            addMarket(marketId, null, "Match Game Handicap", map);
        }
    }

    private void generateTTPerSetGameHandicap() {
        // Game Handicap – per set
        // Market ID format: LIVETT_12G{set}HND@{handicap}   (set = 1 … 7)
        // Outcome suffixes: _1 (Player 1), _2 (Player 2)

        for (int set = 1; set <= 7; set++) {
            for (double hcp = -40.5; hcp <= 40.5; hcp += 0.5) {
                if (hcp == 0) continue;

                String line = formatDecimal(Math.abs(hcp));
                String handicapValue = (hcp < 0 ? "-" : "") + line;
                String marketId = "LIVETT_12G" + set + "HND@" + handicapValue;

                Map<String, String> map = new HashMap<>();
                if (hcp < 0) {
                    map.put("1H", "Home (-" + line + ")" + " player 1");
                    map.put("2H", "Home (-" + line + ")" + " player 2");
                } else {
                    map.put("1H", "Home (+" + line + ")" + " player 1");
                    map.put("2H", "Home (+" + line + ")" + " player 2");
                }

                addMarket(marketId, null, getTTSetOrdinal(set) + " Set - Game Handicap", map);
            }
        }
    }

    private void generateTTPointHandicap() {
        // Point Handicap (Match)
        // Market ID format: LIVETT_12PHND@{handicap}
        // Outcome suffixes: _1 (Player 1), _2 (Player 2)

        for (double hcp = -70.5; hcp <= 70.5; hcp += 0.5) {
            if (hcp == 0) continue;

            String line = formatDecimal(Math.abs(hcp));
            String handicapValue = (hcp < 0 ? "-" : "") + line;
            String marketId = "LIVETT_12PHND@" + handicapValue;

            Map<String, String> map = new HashMap<>();
            if (hcp < 0) {
                map.put("1H", "Home (-" + line + ")" + " player 1");
                map.put("2H", "Home (-" + line + ")" + " player 2");
            } else {
                map.put("1H", "Home (+" + line + ")" + " player 1");
                map.put("2H", "Home (+" + line + ")" + " player 2");
            }

            addMarket(marketId, null, "Point Handicap", map);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – TOTAL MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateTTMatchTotal() {
        // Total Points (Match)
        // Market ID format: LIVETT_OU@{total}
        // Outcome suffixes: _Over, _Under

        for (double total = 30.5; total <= 200.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVETT_OU@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("Over", "Over " + line);
            map.put("Under", "Under " + line);
            addMarket(marketId, null, "Total", map);
        }
    }

    private void generateTTPerSetTotal() {
        // Total Points – per period (sets 1-4)
        // Market ID format: LIVETT_OU{set}PN@{total}   (set = 1 … 4)
        // Outcome suffixes: _O (Over), _U (Under)

        for (int set = 1; set <= 4; set++) {
            for (double total = 15.5; total <= 40.5; total += 0.5) {
                String line = formatDecimal(total);
                String marketId = "LIVETT_OU" + set + "PN@" + line;

                Map<String, String> map = new HashMap<>();
                map.put("O", "Over " + line);
                map.put("U", "Under " + line);
                addMarket(marketId, null, "TOTAL FOR " + convertFromOrdinal(getTTSetOrdinal(set)) + " PERIOD", map);
            }
        }
    }

    private String convertFromOrdinal(String ttSetOrdinal) {
        return switch (ttSetOrdinal) {
            case "1" -> "FIRST";
            case "2" -> "SECOND";
            case "3" -> "THIRD";
            case "4" -> "FOURTH";
            case "5" -> "FIFTH";
            default -> ttSetOrdinal;
        };
    }

    private void generateTTAlternatePerSetTotal() {
        // Total Points – per period (alternate ID style, sets 1-7)
        // Market ID format: LIVETT_OU{set}P@{total}   (set = 1 … 7)
        // Outcome suffixes: _Over, _Under

        for (int set = 1; set <= 7; set++) {
            for (double total = 15.5; total <= 40.5; total += 0.5) {
                String line = formatDecimal(total);
                String marketId = "LIVETT_OU" + set + "P@" + line;

                Map<String, String> map = new HashMap<>();
                map.put("Over", "Over " + line);
                map.put("Under", "Under " + line);
                addMarket(marketId, null, "TOTAL FOR " + convertFromOrdinal(getTTSetOrdinal(set)) + " PERIOD", map);
            }
        }
    }

    private void generateTTTotalPoints() {
        // Total Points Over/Under (match-level alternate)
        // Market ID format: LIVETT_OUP@{total}
        // Outcome suffixes: _O (Over), _U (Under)

        for (double total = 10.5; total <= 190.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVETT_OUP@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, "TOTAL", map);
        }
    }

    private void generateTTTotalGames() {
        // Total Games Over/Under
        // Market ID format: LIVETT_OUG@{total}
        // Outcome suffixes: _O (Over), _U (Under)

        for (double total = 2.5; total <= 10.5; total += 0.5) {
            String line = formatDecimal(total);
            String marketId = "LIVETT_OUG@" + line;

            Map<String, String> map = new HashMap<>();
            map.put("O", "Over " + line);
            map.put("U", "Under " + line);
            addMarket(marketId, null, "Total Games Over/Under", map);
        }
    }

    private void generateTTPerSetGameTotal() {
        // Game Total Points Over/Under – per set
        // Market ID format: LIVETT_G{set}OUP@{total}   (set = 1 … 5)
        // Outcome suffixes: _O (Over), _U (Under)

        for (int set = 1; set <= 5; set++) {
            for (double total = 15.5; total <= 40.5; total += 0.5) {
                String line = formatDecimal(total);
                String marketId = "LIVETT_G" + set + "OUP@" + line;

                Map<String, String> map = new HashMap<>();
                map.put("O", "Over " + line);
                map.put("U", "Under " + line);
                addMarket(marketId, null, getTTSetOrdinal(set) + " Set - Game Total Points", map);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – ODD / EVEN MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateTTPerSetOddEven() {
        // Odd/Even – per period (sets 1-7)
        // Market ID format: LIVETT_OE{set}PN   (set = 1 … 7)
        // Outcome suffixes: _OD (Odd), _EV (Even)

        for (int set = 1; set <= 7; set++) {
            String marketId = "LIVETT_OE" + set + "PN";

            Map<String, String> map = new HashMap<>();
            map.put("OD", "Odd");
            map.put("EV", "Even");
            addMarket(marketId, null, "ODD/EVEN FOR " + convertFromOrdinal(getTTSetOrdinal(set)) + " PERIOD", map);
        }
    }

    private void generateTTAlternatePerSetOddEven() {
        // Odd/Even – per period (alternate ID style, sets 1-7)
        // Market ID format: LIVETT_OE{set}P   (set = 1 … 7)
        // Outcome suffixes: _Odd, _Even

        for (int set = 1; set <= 7; set++) {
            String marketId = "LIVETT_OE" + set + "P";

            Map<String, String> map = new HashMap<>();
            map.put("Odd", "Odd");
            map.put("Even", "Even");
            addMarket(marketId, null, "ODD/EVEN FOR " + convertFromOrdinal(getTTSetOrdinal(set)) + " PERIOD", map);
        }
    }

    private void generateTTSet1OddEven() {
        // Game 1 Total Points Odd/Even
        // Market ID: LIVETT_G1OE
        // Outcome suffixes: _O (Odd), _E (Even)
        Map<String, String> map = new HashMap<>();
        map.put("O", "Odd");
        map.put("E", "Even");
        addMarket("LIVETT_G1OE", null, "Set 1 - Total Points Odd/Even", map);
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – RESULT MARKETS
    // ──────────────────────────────────────────────────────────────

    private void generateTTFinalResultBest5() {
        // Final Result (in sets – best of 5)
        // Market ID: LIVETT_12B5
        // Outcome suffixes: _3-0, _3-1, _3-2, _0-3, _1-3, _2-3
        Map<String, String> map = new HashMap<>();
        map.put("3-0", "3-0");
        map.put("3-1", "3-1");
        map.put("3-2", "3-2");
        map.put("0-3", "0-3");
        map.put("1-3", "1-3");
        map.put("2-3", "2-3");
        addMarket("LIVETT_12B5", null, "Final Result (Best of 5)", map);
    }

    private void generateTTFinalResultBest7() {
        // Final Result (in sets – best of 7)
        // Market ID: LIVETT_12BO7
        // Outcome suffixes: _4-0 … _3-4
        Map<String, String> map = new HashMap<>();
        map.put("4-0", "4-0");
        map.put("4-1", "4-1");
        map.put("4-2", "4-2");
        map.put("4-3", "4-3");
        map.put("0-4", "0-4");
        map.put("1-4", "1-4");
        map.put("2-4", "2-4");
        map.put("3-4", "3-4");
        addMarket("LIVETT_12BO7", null, "Final Result (Best of 7)", map);
    }

    private void generateTTCorrectMatchScore() {
        // Correct Match Score
        // Market ID: LIVETT_CS
        // Covers both best-of-5 and best-of-7 score lines
        Map<String, String> map = new HashMap<>();
        map.put("3:0", "3:0");   map.put("0:3", "0:3");
        map.put("3:1", "3:1");   map.put("1:3", "1:3");
        map.put("3:2", "3:2");   map.put("2:3", "2:3");
        map.put("4:0", "4:0");   map.put("0:4", "0:4");
        map.put("4:1", "4:1");   map.put("1:4", "1:4");
        map.put("4:2", "4:2");   map.put("2:4", "2:4");
        map.put("4:3", "4:3");   map.put("3:4", "3:4");
        addMarket("LIVETT_CS", null, "Correct Match Score", map);
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – NUMBER OF SETS
    // ──────────────────────────────────────────────────────────────

    private void generateTTNumberOfSetsBest5() {
        // Number of Sets (Best of 5)
        // Market ID: LIVETT_SETSB5
        // Outcome suffixes: _3, _4, _5
        Map<String, String> map = new HashMap<>();
        map.put("3", "3 Sets");
        map.put("4", "4 Sets");
        map.put("5", "5 Sets");
        addMarket("LIVETT_SETSB5", null, "Number of Sets (Best of 5)", map);
    }

    private void generateTTNumberOfSetsBest7() {
        // Number of Sets (Best of 7)
        // Market ID: LIVETT_SETSBO5
        // Outcome suffixes: _4, _5, _6, _7
        Map<String, String> map = new HashMap<>();
        map.put("4", "4 Sets");
        map.put("5", "5 Sets");
        map.put("6", "6 Sets");
        map.put("7", "7 Sets");
        addMarket("LIVETT_SETSBO5", null, "Number of Sets (Best of 7)", map);
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – SETS EXCEEDING SCORE LIMIT
    // ──────────────────────────────────────────────────────────────

    private void generateTTSetsExceedingBest5() {
        // How Many Sets Exceed Score Limit (Best of 5)
        // Market ID: LIVETT_SETSEXN
        // Outcome suffixes: _0 … _5
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i <= 5; i++) {
            map.put(String.valueOf(i), i + " Sets");
        }
        addMarket("LIVETT_SETSEXN", null, "Sets Exceeding Score Limit (Best of 5)", map);
    }

    private void generateTTSetsExceedingBest7() {
        // How Many Sets Exceed Score Limit (Best of 7)
        // Market ID: LIVETT_SETSEX
        // Outcome suffixes: _0 … _7
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i <= 7; i++) {
            map.put(String.valueOf(i), i + " Sets");
        }
        addMarket("LIVETT_SETSEX", null, "Sets Exceeding Score Limit (Best of 7)", map);
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – WHO SCORES Nth POINT (sets 1-5)
    // ──────────────────────────────────────────────────────────────

    private void generateTTNthPointScorer() {
        // Who Scores the Nth Point – sets 1 to 5
        //
        // Market ID patterns:
        //   LIVETT_125P{set}   – 5th  point
        //   LIVETT_1210P{set}  – 10th point
        //   LIVETT_1215P{set}  – 15th point
        //   LIVETT_1220P{set}  – 20th point
        //
        // Outcome suffixes: _1HH (Player 1), _2HH (Player 2)

        int[] points = {5, 10, 15, 20};

        for (int set = 1; set <= 5; set++) {
            for (int point : points) {
                String marketId;
                if (point == 5) {
                    marketId = "LIVETT_125P" + set;
                } else {
                    marketId = "LIVETT_12" + point + "P" + set;
                }

                Map<String, String> map = new HashMap<>();
                map.put("1HH", "Player 1");
                map.put("2HH", "Player 2");
                addMarket(marketId, null, "Who Scores " + point + "th Point – Set " + set, map);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – WHO SCORES Nth POINT (sets 6-7)
    // ──────────────────────────────────────────────────────────────

    private void generateTTNthPointScorer67() {
        // Who Scores the Nth Point – sets 6 and 7
        //
        // Market ID pattern: LIVETT_12S{point}P{set}P
        //   e.g. LIVETT_12S5P6P   = 5th  point, set 6
        //        LIVETT_12S20P7P  = 20th point, set 7
        //
        // Outcome suffixes: _1HH (Player 1), _2HH (Player 2)

        int[] points = {5, 10, 15, 20};

        for (int set = 6; set <= 7; set++) {
            for (int point : points) {
                String marketId = "LIVETT_12S" + point + "P" + set + "P";

                Map<String, String> map = new HashMap<>();
                map.put("1HH", "Player 1");
                map.put("2HH", "Player 2");
                addMarket(marketId, null, "Who Scores " + point + "th Point – Set " + set, map);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // TABLE TENNIS – UTILITY
    // ──────────────────────────────────────────────────────────────

    private String getTTSetOrdinal(int set) {
        String[] ordinals = {"", "1st", "2nd", "3rd", "4th", "5th", "6th", "7th"};
        if (set >= 1 && set <= 7) {
            return ordinals[set];
        }
        return set + "th";
    }




}