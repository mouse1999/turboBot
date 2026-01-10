package com.mouse.bet.enums;

public enum ArbStatus {
    /**
     * Currently available and can be bet on
     */
    ACTIVE,

    /**
     * No longer available (odds changed or removed)
     */
    EXPIRED,

    /**
     * Bet has been successfully placed
     */
    EXECUTED,

    /**
     * Failed to place bet (technical error, insufficient balance, etc.)
     */
    FAILED,

    /**
     * Being monitored for changes (e.g., waiting for better odds)
     */
    MONITORING,

    /**
     * Old data that hasn't been updated recently
     */
    STALE,
    IN_PROGRESS,

    /**
     * Manually ignored by user/system
     */
    IGNORED,

    /**
     * Pending execution (in queue)
     */
    PENDING,
    COMPLETED
}
