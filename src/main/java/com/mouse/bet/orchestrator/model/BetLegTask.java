package com.mouse.bet.orchestrator.model;

import com.mouse.bet.enums.BookMaker;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Phaser;

/**
 * Wrapper for BetLeg that includes orchestration metadata for worker execution.
 * Workers execute this task and signal completion via the barrier.
 */
@Slf4j
@Getter
@Builder
public class BetLegTask {

    /** The actual betting task to execute */
    private final BetLeg betLeg;

    /** ArbitrageOpportunity ID for tracking */
    private final Long arbId;

    /** Bookmaker for this leg */
    private final BookMaker bookmaker;

    /** Synchronization barrier - worker must call arriveAndDeregister() when done */
    private final Phaser barrier;

    /** Results map - worker must put its LegResult here */
    private final ConcurrentMap<BookMaker, LegResult> results;

    /** Maximum retry attempts for this leg */
    @Builder.Default
    private final int maxRetries = 3;

    /** Backoff duration between retries */
    @Builder.Default
    private final Duration retryBackoff = Duration.ofSeconds(2);

    /** Timestamp when task was created */
    @Builder.Default
    private final long createdAtMs = System.currentTimeMillis();

    /**
     * Mark this task as completed with a result.
     * Workers should call this method after executing the bet.
     *
     * @param success whether the bet was placed successfully
     * @param message result message or error details
     */
    public void complete(boolean success, String message) {
        try {
            LegResult result = new LegResult(success, message);
            results.put(bookmaker, result);

            log.info("BetLegTask completed | ArbId: {} | Bookmaker: {} | Success: {} | Message: {} | Duration: {}ms",
                    arbId, bookmaker, success, message,
                    System.currentTimeMillis() - createdAtMs);

        } finally {
            // Always signal completion to unblock orchestrator
            barrier.arriveAndDeregister();
            log.debug("Phaser signaled | ArbId: {} | Bookmaker: {} | Phase: {}",
                    arbId, bookmaker, barrier.getPhase());
        }
    }

    /**
     * Mark this task as failed with an exception.
     * Workers should call this in catch blocks.
     *
     * @param error the exception that caused the failure
     */
    public void fail(Throwable error) {
        String message = String.format("Failed: %s - %s",
                error.getClass().getSimpleName(),
                error.getMessage());
        complete(false, message);
    }

    /**
     * Get the task ID (ArbitrageOpportunity externalId)
     */
    public String getTaskId() {
        return betLeg.taskId();
    }

    /**
     * Get the outcome being bet on
     */
    public String getOutcome() {
        return betLeg.outcome();
    }

    /**
     * Get the stake amount
     */
    public double getStakeAmount() {
        return betLeg.stakeAmount();
    }

    /**
     * Get the expected odds
     */
    public double getExpectedOdds() {
        return betLeg.expectedOdds();
    }

    /**
     * Check if odds are within acceptable range
     */
    public boolean isOddsAcceptable(double actualOdds) {
        return actualOdds >= betLeg.minOdds() && actualOdds <= betLeg.maxOdds();
    }

    /**
     * Get a summary of this task for logging
     */
    public String getSummary() {
        return String.format("BetLegTask[arbId=%d, bookmaker=%s, outcome=%s, odds=%.2f, stake=%.2f, taskId=%s]",
                arbId, bookmaker, betLeg.outcome(), betLeg.expectedOdds(),
                betLeg.stakeAmount(), betLeg.taskId());
    }

    @Override
    public String toString() {
        return getSummary();
    }
}