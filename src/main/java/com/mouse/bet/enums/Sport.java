package com.mouse.bet.enums;

import lombok.Getter;

/**
 * Sports as defined in Breaking-Bet API.
 * The integer ID is used in API responses to identify the sport.
 */

@Getter
public enum Sport {

    FOOTBALL(1, "Football"),
    TENNIS(2, "Tennis"),
    BASKETBALL(3, "Basketball"),
    ICE_HOCKEY(4, "Ice Hockey"),
    VOLLEYBALL(5, "Volleyball"),
    HANDBALL(6, "Handball"),
    TABLE_TENNIS(12, "Table Tennis"),
    BASEBALL(16, "Baseball"),
    AMERICAN_FOOTBALL(18, "American Football"),
    E_SPORTS(91, "E-Sports");

    private final int breakingBetId;
    private final String displayName;

    Sport(int breakingBetId, String displayName) {
        this.breakingBetId = breakingBetId;
        this.displayName = displayName;
    }

    /**
     * Find Sport by Breaking-Bet ID (used when parsing API responses)
     */
    public static Sport fromBreakingBetId(int id) {
        for (Sport sport : values()) {
            if (sport.breakingBetId == id) {
                return sport;
            }
        }
        return null; // or throw IllegalArgumentException("Unknown sport ID: " + id)
    }

    /**
     * Find Sport by display name
     */
    public static Sport fromDisplayName(String name) {
        if (name == null) return null;
        for (Sport sport : values()) {
            if (sport.displayName.equalsIgnoreCase(name)) {
                return sport;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}