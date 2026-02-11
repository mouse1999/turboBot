//package com.mouse.bet.mock;
//
//import com.mouse.bet.entity.ArbOutcome;
//import com.mouse.bet.entity.ArbitrageOpportunity;
//import com.mouse.bet.enums.ArbStatus;
//import com.mouse.bet.enums.BookMaker;
//import com.mouse.bet.orchestrator.Orchestrator;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class MockBettingTask implements CommandLineRunner {
//
//    private final Orchestrator orchestrator;
//
//    @Override
//    public void run(String... args) throws Exception {
//        log.info("🎯 Starting Mock Betting Task - Creating arbitrage opportunities...");
//
//        // Create different types of arbitrage opportunities
//        createBasicWinnerArb();
////        createPointsOverUnderArb();
////        createPointHandicapArb();
////        createHalfMarketArb();
////        createQuarterMarketArb();
//
//        log.info("✅ Mock arbitrage opportunities created and sent to orchestrator");
//    }
//
//    /**
//     * Basic Winner Market Arbitrage
//     */
//    private void createBasicWinnerArb() {
//        log.info("Creating Winner market arbitrage...");
//
//        ArbitrageOpportunity arb = ArbitrageOpportunity.builder()
//                .externalId("mock_arb_winner_001")
//                .eventId("evt_nba_001")
//                .sport("Basketball")
//                .sportId(18)
//                .leagueName("NBA")
//                .country("USA")
//                .homeTeam("Los Angeles Lakers")
//                .awayTeam("Boston Celtics")
//                .matchStartTime(LocalDateTime.now().plusHours(2))
//                .isLive(false)
//                .matchProgress("Not Started")
//                .marketType("Winner")
//                .outCome("Home")
//                .profitPercentage(new BigDecimal("3.45"))
//                .roiPercentage(new BigDecimal("3.45"))
//                .status(ArbStatus.ACTIVE)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .arbAgeSeconds(5L)
//                .confidenceScore(new BigDecimal("95.00"))
//                .updateCount(1)
//                .outcomes(new ArrayList<>())
//                .build();
//
//        // Outcome 1: MSport - Home Win
//        ArbOutcome outcome1 = ArbOutcome.builder()
//                .bookmakerId(1)
//                .bookmakerName(BookMaker.MSPORT)
//                .homeTeam("Los Angeles Lakers")
//                .awayTeam("Boston Celtics")
//                .marketType("Winner (incl. overtime)")
//                .outComeName("Home")
//                .odds(new BigDecimal("3.05"))
//                .previousOdds(new BigDecimal("2.10"))
//                .stake(new BigDecimal("20"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(true)
//                .leagueName("NBA")
//                .bookMakerUrl("https://www.msport.com/ng/web/sports/Basketball/live/USA_NCAA__Regular_Season/Tulane_Green_Waves_vs_North_Texas_Mean_Green/sr:match:64048201")
//                .build();
//
//        // Outcome 2: OneWin - Away Win
//        ArbOutcome outcome2 = ArbOutcome.builder()
//                .bookmakerId(2)
//                .bookmakerName(BookMaker._1WIN)
//                .homeTeam("Los Angeles Lakers")
//                .awayTeam("Boston Celtics")
//                .marketType("Winner (incl. OT)")
//                .outComeName("North Texas Mean Green")
//                .odds(new BigDecimal("1.1"))
//                .previousOdds(new BigDecimal("2.00"))
//                .stake(new BigDecimal("534.88"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(false)
//                .leagueName("NBA")
//                .bookMakerUrl("https://1win.pro/betting/match/sport/tulane-green-waves-vs-north-texas-mean-green-32264930")
//                .build();
//
//        arb.addOutcome(outcome1);
//        arb.addOutcome(outcome2);
//
//        orchestrator.tryLoadArb(arb);
//    }
//
//    /**
//     * Points Over/Under Market Arbitrage
//     */
//    private void createPointsOverUnderArb() {
//        log.info("Creating Points O/U market arbitrage...");
//
//        ArbitrageOpportunity arb = ArbitrageOpportunity.builder()
//                .externalId("mock_arb_points_ou_002")
//                .eventId("evt_nba_002")
//                .sport("Basketball")
//                .sportId(18)
//                .leagueName("NBA")
//                .country("USA")
//                .homeTeam("Golden State Warriors")
//                .awayTeam("Miami Heat")
//                .matchStartTime(LocalDateTime.now().plusHours(3))
//                .isLive(false)
//                .matchProgress("Not Started")
//                .marketType("Points O/U")
//                .outCome("Over 220.5")
//                .profitPercentage(new BigDecimal("2.87"))
//                .roiPercentage(new BigDecimal("2.87"))
//                .status(ArbStatus.ACTIVE)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .arbAgeSeconds(3L)
//                .confidenceScore(new BigDecimal("92.50"))
//                .updateCount(1)
//                .outcomes(new ArrayList<>())
//                .build();
//
//        // Outcome 1: MSport - Over 220.5
//        ArbOutcome outcome1 = ArbOutcome.builder()
//                .bookmakerId(1)
//                .bookmakerName(BookMaker.MSPORT)
//                .homeTeam("Golden State Warriors")
//                .awayTeam("Miami Heat")
//                .marketType("Points O/U")
//                .outComeName("Over 220.5")
//                .odds(new BigDecimal("1.95"))
//                .previousOdds(new BigDecimal("1.92"))
//                .stake(new BigDecimal("512.82"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(true)
//                .leagueName("NBA")
//                .bookMakerUrl("https://www.msport.com/ng/event/22345")
//                .build();
//
//        // Outcome 2: OneWin - Under 220.5
//        ArbOutcome outcome2 = ArbOutcome.builder()
//                .bookmakerId(2)
//                .bookmakerName(BookMaker.SPORTYBET)
//                .homeTeam("Golden State Warriors")
//                .awayTeam("Miami Heat")
//                .marketType("Total Points")
//                .outComeName("Under 220.5")
//                .odds(new BigDecimal("1.98"))
//                .previousOdds(new BigDecimal("1.95"))
//                .stake(new BigDecimal("487.18"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(false)
//                .leagueName("NBA")
//                .bookMakerUrl("https://1winng.com/event/77890")
//                .build();
//
//        arb.addOutcome(outcome1);
//        arb.addOutcome(outcome2);
//
//        orchestrator.tryLoadArb(arb);
//    }
//
//    /**
//     * Point Handicap Market Arbitrage
//     */
//    private void createPointHandicapArb() {
//        log.info("Creating Point Handicap market arbitrage...");
//
//        ArbitrageOpportunity arb = ArbitrageOpportunity.builder()
//                .externalId("mock_arb_handicap_003")
//                .eventId("evt_nba_003")
//                .sport("Basketball")
//                .sportId(18)
//                .leagueName("NBA")
//                .country("USA")
//                .homeTeam("Brooklyn Nets")
//                .awayTeam("Philadelphia 76ers")
//                .matchStartTime(LocalDateTime.now().plusHours(4))
//                .isLive(false)
//                .matchProgress("Not Started")
//                .marketType("Point Handicap")
//                .outCome("Home (-12.5)")
//                .profitPercentage(new BigDecimal("4.12"))
//                .roiPercentage(new BigDecimal("4.12"))
//                .status(ArbStatus.ACTIVE)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .arbAgeSeconds(7L)
//                .confidenceScore(new BigDecimal("88.75"))
//                .updateCount(1)
//                .outcomes(new ArrayList<>())
//                .build();
//
//        // Outcome 1: MSport - Home (-12.5)
//        ArbOutcome outcome1 = ArbOutcome.builder()
//                .bookmakerId(1)
//                .bookmakerName(BookMaker.MSPORT)
//                .homeTeam("Brooklyn Nets")
//                .awayTeam("Philadelphia 76ers")
//                .marketType("Point Handicap")
//                .outComeName("Home (-12.5)")
//                .odds(new BigDecimal("1.88"))
//                .previousOdds(new BigDecimal("1.85"))
//                .stake(new BigDecimal("531.91"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(true)
//                .leagueName("NBA")
//                .bookMakerUrl("https://www.msport.com/ng/event/32345")
//                .build();
//
//        // Outcome 2: OneWin - Away (+12.5)
//        ArbOutcome outcome2 = ArbOutcome.builder()
//                .bookmakerId(2)
//                .bookmakerName(BookMaker._1WIN)
//                .homeTeam("Brooklyn Nets")
//                .awayTeam("Philadelphia 76ers")
//                .marketType("Handicap")
//                .outComeName("Away (+12.5)")
//                .odds(new BigDecimal("2.02"))
//                .previousOdds(new BigDecimal("2.00"))
//                .stake(new BigDecimal("468.09"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(false)
//                .leagueName("NBA")
//                .bookMakerUrl("https://1winng.com/event/87890")
//                .build();
//
//        arb.addOutcome(outcome1);
//        arb.addOutcome(outcome2);
//
//        orchestrator.tryLoadArb(arb);
//    }
//
//    /**
//     * 1st Half Market Arbitrage
//     */
//    private void createHalfMarketArb() {
//        log.info("Creating 1st Half market arbitrage...");
//
//        ArbitrageOpportunity arb = ArbitrageOpportunity.builder()
//                .externalId("mock_arb_half_004")
//                .eventId("evt_nba_004")
//                .sport("Basketball")
//                .sportId(18)
//                .leagueName("NBA")
//                .country("USA")
//                .homeTeam("Chicago Bulls")
//                .awayTeam("Detroit Pistons")
//                .matchStartTime(LocalDateTime.now().plusHours(5))
//                .isLive(false)
//                .matchProgress("Not Started")
//                .marketType("1st Half Winner")
//                .outCome("Home")
//                .profitPercentage(new BigDecimal("3.21"))
//                .roiPercentage(new BigDecimal("3.21"))
//                .status(ArbStatus.ACTIVE)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .arbAgeSeconds(4L)
//                .confidenceScore(new BigDecimal("91.00"))
//                .updateCount(1)
//                .outcomes(new ArrayList<>())
//                .build();
//
//        // Outcome 1: MSport - 1st Half Home
//        ArbOutcome outcome1 = ArbOutcome.builder()
//                .bookmakerId(1)
//                .bookmakerName(BookMaker.SPORTYBET)
//                .homeTeam("Chicago Bulls")
//                .awayTeam("Detroit Pistons")
//                .marketType("1st Half Winner")
//                .outComeName("Home")
//                .odds(new BigDecimal("2.10"))
//                .previousOdds(new BigDecimal("2.08"))
//                .stake(new BigDecimal("476.19"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(true)
//                .leagueName("NBA")
//                .bookMakerUrl("https://www.msport.com/ng/event/42345")
//                .build();
//
//        // Outcome 2: OneWin - 1st Half Away
//        ArbOutcome outcome2 = ArbOutcome.builder()
//                .bookmakerId(2)
//                .bookmakerName(BookMaker._1WIN)
//                .homeTeam("Chicago Bulls")
//                .awayTeam("Detroit Pistons")
//                .marketType("1st Half Winner")
//                .outComeName("Away")
//                .odds(new BigDecimal("1.92"))
//                .previousOdds(new BigDecimal("1.90"))
//                .stake(new BigDecimal("523.81"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(false)
//                .leagueName("NBA")
//                .bookMakerUrl("https://1winng.com/event/97890")
//                .build();
//
//        arb.addOutcome(outcome1);
//        arb.addOutcome(outcome2);
//
//        orchestrator.tryLoadArb(arb);
//    }
//
//    /**
//     * 1st Quarter Market Arbitrage
//     */
//    private void createQuarterMarketArb() {
//        log.info("Creating 1st Quarter market arbitrage...");
//
//        ArbitrageOpportunity arb = ArbitrageOpportunity.builder()
//                .externalId("mock_arb_quarter_005")
//                .eventId("evt_nba_005")
//                .sport("Basketball")
//                .sportId(18)
//                .leagueName("NBA")
//                .country("USA")
//                .homeTeam("Dallas Mavericks")
//                .awayTeam("Houston Rockets")
//                .matchStartTime(LocalDateTime.now().plusHours(6))
//                .isLive(false)
//                .matchProgress("Not Started")
//                .marketType("1st Quarter Winner")
//                .outCome("Home")
//                .profitPercentage(new BigDecimal("2.95"))
//                .roiPercentage(new BigDecimal("2.95"))
//                .status(ArbStatus.ACTIVE)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .arbAgeSeconds(6L)
//                .confidenceScore(new BigDecimal("89.50"))
//                .updateCount(1)
//                .outcomes(new ArrayList<>())
//                .build();
//
//        // Outcome 1: MSport - 1st Quarter Home
//        ArbOutcome outcome1 = ArbOutcome.builder()
//                .bookmakerId(1)
//                .bookmakerName(BookMaker.MSPORT)
//                .homeTeam("Dallas Mavericks")
//                .awayTeam("Houston Rockets")
//                .marketType("1st Quarter Winner")
//                .outComeName("Home")
//                .odds(new BigDecimal("2.05"))
//                .previousOdds(new BigDecimal("2.03"))
//                .stake(new BigDecimal("487.80"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(true)
//                .leagueName("NBA")
//                .bookMakerUrl("https://www.msport.com/ng/event/52345")
//                .build();
//
//        // Outcome 2: OneWin - 1st Quarter Away
//        ArbOutcome outcome2 = ArbOutcome.builder()
//                .bookmakerId(2)
//                .bookmakerName(BookMaker._1WIN)
//                .homeTeam("Dallas Mavericks")
//                .awayTeam("Houston Rockets")
//                .marketType("1st Quarter Winner")
//                .outComeName("Away")
//                .odds(new BigDecimal("1.97"))
//                .previousOdds(new BigDecimal("1.95"))
//                .stake(new BigDecimal("512.20"))
//                .sport("Basketball")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(false)
//                .leagueName("NBA")
//                .bookMakerUrl("https://1winng.com/event/17890")
//                .build();
//
//        arb.addOutcome(outcome1);
//        arb.addOutcome(outcome2);
//
//        orchestrator.tryLoadArb(arb);
//    }
//
//    /**
//     * Helper method to create live match arbitrage (bonus)
//     */
//    @SuppressWarnings("unused")
//    private void createLiveMatchArb() {
//        log.info("Creating LIVE match arbitrage...");
//
//        ArbitrageOpportunity arb = ArbitrageOpportunity.builder()
//                .externalId("mock_arb_live_006")
//                .eventId("evt_nba_live_001")
//                .sport("Basketball")
//                .sportId(18)
//                .leagueName("NBA")
//                .country("USA")
//                .homeTeam("Phoenix Suns")
//                .awayTeam("Denver Nuggets")
//                .matchStartTime(LocalDateTime.now().minusMinutes(25))
//                .isLive(true)
//                .matchProgress("Q2 - 03:45")
//                .marketType("Winner")
//                .outCome("Away")
//                .profitPercentage(new BigDecimal("5.67"))
//                .roiPercentage(new BigDecimal("5.67"))
//                .status(ArbStatus.ACTIVE)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .arbAgeSeconds(2L)
//                .confidenceScore(new BigDecimal("78.25"))
//                .updateCount(5)
//                .outcomes(new ArrayList<>())
//                .build();
//
//        ArbOutcome outcome1 = ArbOutcome.builder()
//                .bookmakerId(1)
//                .bookmakerName(BookMaker.MSPORT)
//                .homeTeam("Phoenix Suns")
//                .awayTeam("Denver Nuggets")
//                .marketType("Winner")
//                .outComeName("Home")
//                .odds(new BigDecimal("2.35"))
//                .previousOdds(new BigDecimal("2.30"))
//                .stake(new BigDecimal("425.53"))
//                .sport("Basketball")
//                .progress("Q2 - 03:45")
//                .reordered(false)
//                .initiator(false)
//                .leagueName("NBA")
//                .bookMakerUrl("https://www.msport.com/ng/event/live_62345")
//                .build();
//
//        ArbOutcome outcome2 = ArbOutcome.builder()
//                .bookmakerId(2)
//                .bookmakerName(BookMaker._1WIN)
//                .homeTeam("Phoenix Suns")
//                .awayTeam("Denver Nuggets")
//                .marketType("Winner")
//                .outComeName("Away")
//                .odds(new BigDecimal("1.75"))
//                .previousOdds(new BigDecimal("1.72"))
//                .stake(new BigDecimal("574.47"))
//                .sport("Basketball")
//                .progress("Q2 - 03:45")
//                .reordered(false)
//                .initiator(true)
//                .leagueName("NBA")
//                .bookMakerUrl("https://1winng.com/event/live_27890")
//                .build();
//
//        arb.addOutcome(outcome1);
//        arb.addOutcome(outcome2);
//
//        orchestrator.tryLoadArb(arb);
//    }
//}