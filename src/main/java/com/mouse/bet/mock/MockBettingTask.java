//package com.mouse.bet.mock;
//
//import com.mouse.bet.enums.BookMaker;
//import com.mouse.bet.interfaces.BettingTask;
//import lombok.Builder;
//
///**
// * Mock implementation of BettingTask for testing and development purposes.
// * Uses Lombok for cleaner code (optional – you can remove if not using Lombok).
// */
//@Builder
//public record MockBettingTask(BookMaker bookmaker, Integer bookmakerId, String marketType, String outcome,
//                              double expectedOdds, double minOdds, double maxOdds, double stakeAmount,
//                              String leagueName, String homeTeam, String awayTeam,
//                              String taskId) implements BettingTask {
//
//    // Default constructor required for builder if using Lombok
//
//    // Example factory method to create a realistic mock task
//    public static MockBettingTask createSamplePinnacleTask() {
//        return MockBettingTask.builder()
//                .taskId("arb-20260108-001")
//                .bookmaker(BookMaker.BETANO)
//                .bookmakerId(79)
//                .marketType("Handicap")
//                .outcome("Away +13.5")
//                .expectedOdds(1.95)
//                .minOdds(1.85)
//                .maxOdds(2.10)
//                .stakeAmount(150.00)
//                .leagueName("Table Tennis - Setka Cup")
//                .homeTeam("Ivanov, Alex")
//                .awayTeam("Petrov, Sergey")
//                .build();
//    }
//
//    public static MockBettingTask createSampleSoftBookTask() {
//        return MockBettingTask.builder()
//                .taskId("arb-20260108-001-counter")
//                .bookmaker(BookMaker._1WIN) // or whatever soft book corresponds to ID 80
//                .bookmakerId(80)
//                .marketType("Odd/even (incl. overtime)")
//                .outcome("Odd")
//                .expectedOdds(1.85)
//                .minOdds(1.80)
//                .maxOdds(2.05)
//                .stakeAmount(15)
//                .leagueName("Paranaense")
//                .homeTeam("Rasta Vechta")
//                .awayTeam("Wurzburg Baskets")
//                .build();
//    }
//}