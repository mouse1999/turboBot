package com.mouse.bet.dto;

import com.mouse.bet.enums.BookMaker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents one leg/outcome of an arbitrage opportunity.
 * Contains full context from the event/sub-event the odd belongs to,
 * including teams, league, sport, and bookmaker details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutcomeData {

    /** ID of the sub-event (or main event ID if no sub-event) this odd belongs to */
    private String subEventId;

    /** Numeric bookmaker ID from BreakingBet API */
    private Integer bookmakerId;

    /** Mapped human-readable bookmaker enum */
    private BookMaker bookmakerName;

    /** Sport name (e.g., "Football", "Basketball") */
    private String sport;

    /** League or tournament name */
    private String league;

    /** Home team name */
    private String team1;

    /** Away team name */
    private String team2;

    /** Description of the outcome (e.g., "Home Win", "Over 2.5", "Side 1") */
    private String outComeName;

    /** Current decimal odds value */
    private BigDecimal odds;

    /** Previous odds value (for tracking movement) */
    private BigDecimal previousOdds;

    /** When this odd was last updated on the bookmaker */
    private LocalDateTime updated;

    /** True if this bookmaker triggered/created the arb opportunity */
    private Boolean initiator;

    /** Current match progress (e.g., "45'", "2nd Half", "Live") */
    private String progress;

    /** Bookmaker's internal event/market ID (useful for direct links) */
    private String originalId;

    /** Indicates if the event was reordered or postponed */
    private Boolean reordered;

    private String marketType;

    // ==================== Additional useful fields ====================

    /** Calculated stake amount for this outcome to achieve balanced arb (optional, filled later) */
    private BigDecimal stake;

    /** Profit if this outcome wins (based on total stake, optional) */
    private BigDecimal payout;

    /** Percentage of total stake allocated to this leg */
    private BigDecimal stakePercentage;

    /**
     * Convenience: returns true if odds are valid and greater than 1.00
     */
    public boolean hasValidOdds() {
        return odds != null && odds.compareTo(BigDecimal.ONE) > 0;
    }

    /**
     * Safe display name for bookmaker
     */
    public String getBookmakerDisplayName() {
        return bookmakerName != null ? bookmakerName.getDisplayName() : "Unknown";
    }

    /**
     * Full match string: "Team1 vs Team2"
     */
    public String getMatchup() {
        if (team1 == null || team2 == null) return "Unknown Match";
        return team1 + " vs " + team2;
    }
}