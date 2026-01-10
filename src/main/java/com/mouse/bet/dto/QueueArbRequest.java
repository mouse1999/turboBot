package com.mouse.bet.dto;


import com.mouse.bet.enums.BookMaker;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for queueing an arbitrage opportunity with custom stake amounts for each bookmaker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueArbRequest {

    /**
     * Database ID of the arbitrage opportunity (optional if externalId is provided)
     */
    private Long id;

    /**
     * External ID of the arbitrage opportunity (optional if id is provided)
     */
    private String externalId;

    /**
     * Stakes for each bookmaker.
     * Key: BookMaker enum (e.g., "BET365", "BETWAY")
     * Value: Stake amount
     *
     * Example:
     * {
     *   "BET365": 476.19,
     *   "BETWAY": 285.71,
     *   "SPORTYBET": 250.00
     * }
     */
    @NotNull(message = "Stakes map is required")
    private Map<BookMaker, BigDecimal> stakes;

    /**
     * Whether to validate that stakes are provided for all outcomes
     */
    @Builder.Default
    private boolean validateAllStakes = true;

    /**
     * Get stake for a specific bookmaker
     */
    public BigDecimal getStakeForBookmaker(BookMaker bookmaker) {
        return stakes != null ? stakes.get(bookmaker) : null;
    }

    /**
     * Check if stake is provided for a bookmaker
     */
    public boolean hasStakeForBookmaker(BookMaker bookmaker) {
        return stakes != null && stakes.containsKey(bookmaker) && stakes.get(bookmaker) != null;
    }

    /**
     * Validate request has either id or externalId
     */
    public boolean hasValidIdentifier() {
        return (id != null) || (externalId != null && !externalId.trim().isEmpty());
    }

    /**
     * Get total stake amount across all bookmakers
     */
    public BigDecimal getTotalStake() {
        if (stakes == null || stakes.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return stakes.values().stream()
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get number of stakes provided
     */
    public int getStakeCount() {
        return stakes != null ? stakes.size() : 0;
    }
}
