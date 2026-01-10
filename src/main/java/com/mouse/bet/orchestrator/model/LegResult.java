package com.mouse.bet.orchestrator.model;

import com.mouse.bet.enums.BookMaker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result of executing a single bet leg in an arbitrage opportunity.
 * Contains outcome information, bet details, and any error messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegResult {

    /** Whether the leg was executed successfully */
    private boolean success;

    /** Result message or error description */
    private String message;

    /** Bookmaker where the bet was placed */
    private BookMaker bookmaker;

    /** Bet ID from the bookmaker (if successful) */
    private String betId;

    /** Actual odds at which the bet was placed */
    private Double actualOdds;

    /** Expected odds from the arbitrage opportunity */
    private Double expectedOdds;

    /** Stake amount placed */
    private Double stakeAmount;

    /** Outcome that was bet on */
    private String outcome;

    /** Market type */
    private String marketType;

    /** Number of retry attempts made */
    private Integer retryAttempts;

    /** Timestamp when the result was created */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Duration of execution in milliseconds */
    private Long executionTimeMs;

    /** Error code if failed */
    private String errorCode;

    /** Whether the failure is retryable */
    private Boolean retryable;

    /**
     * Create a simple success result with just success flag and message
     */
    public LegResult(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Create a detailed success result
     */
    public static LegResult success(BookMaker bookmaker, String betId, Double actualOdds,
                                    Double expectedOdds, Double stakeAmount, String outcome,
                                    String marketType, Long executionTimeMs) {
        return LegResult.builder()
                .success(true)
                .message("Bet placed successfully")
                .bookmaker(bookmaker)
                .betId(betId)
                .actualOdds(actualOdds)
                .expectedOdds(expectedOdds)
                .stakeAmount(stakeAmount)
                .outcome(outcome)
                .marketType(marketType)
                .executionTimeMs(executionTimeMs)
                .retryAttempts(0)
                .build();
    }

    /**
     * Create a simple success result
     */
    public static LegResult success(String betId, String message) {
        return LegResult.builder()
                .success(true)
                .betId(betId)
                .message(message)
                .build();
    }

    /**
     * Create a detailed failure result
     */
    public static LegResult failure(BookMaker bookmaker, String message, String errorCode,
                                    boolean retryable, Integer retryAttempts,
                                    Long executionTimeMs) {
        return LegResult.builder()
                .success(false)
                .message(message)
                .bookmaker(bookmaker)
                .errorCode(errorCode)
                .retryable(retryable)
                .retryAttempts(retryAttempts)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    /**
     * Create a simple failure result
     */
    public static LegResult failure(String message) {
        return LegResult.builder()
                .success(false)
                .message(message)
                .build();
    }

    /**
     * Create a failure result with error code
     */
    public static LegResult failure(String message, String errorCode, boolean retryable) {
        return LegResult.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .retryable(retryable)
                .build();
    }

    /**
     * Check if odds deviation is acceptable (within tolerance)
     */
    public boolean hasAcceptableOddsDeviation(double maxDeviationPercent) {
        if (actualOdds == null || expectedOdds == null) {
            return false;
        }

        double deviation = Math.abs(actualOdds - expectedOdds) / expectedOdds * 100;
        return deviation <= maxDeviationPercent;
    }

    /**
     * Get odds deviation percentage
     */
    public Double getOddsDeviationPercent() {
        if (actualOdds == null || expectedOdds == null) {
            return null;
        }

        return (actualOdds - expectedOdds) / expectedOdds * 100;
    }

    /**
     * Check if this was a successful bet placement
     */
    public boolean isSuccessful() {
        return success && betId != null;
    }

    /**
     * Check if this failure is retryable
     */
    public boolean isRetryableFailure() {
        return !success && Boolean.TRUE.equals(retryable);
    }

    /**
     * Get a summary string for logging
     */
    public String getSummary() {
        if (success) {
            return String.format("SUCCESS | Bookmaker: %s | BetId: %s | Odds: %.2f (expected: %.2f) | Stake: %.2f | Outcome: %s",
                    bookmaker, betId, actualOdds, expectedOdds, stakeAmount, outcome);
        } else {
            return String.format("FAILED | Bookmaker: %s | Error: %s | Code: %s | Retryable: %s | Attempts: %d",
                    bookmaker, message, errorCode, retryable, retryAttempts);
        }
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
