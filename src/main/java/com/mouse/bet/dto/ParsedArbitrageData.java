package com.mouse.bet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedArbitrageData {
    // From ArbItem
    private String arbId;
    private String eventId;
    private BigDecimal profitPercentage;
    private BigDecimal roi;
    private LocalDateTime created;
    private List<Integer> groupsIds;
    private String generalMarketType;
    private String generalOutcomeName;

    // From Event
    private Integer sportId;
    private String sportName;
    private String league;
    private String team1;
    private String team2;
    private LocalDateTime matchStart;
    private Boolean isLive;
    private String progress;

    // Outcomes with bookmaker details
    private List<OutcomeData> outcomes;

    /**
     * Check if this is a 2-way arbitrage
     */
    public boolean isTwoWay() {
        return outcomes != null && outcomes.size() == 2;
    }

    /**
     * Check if this is a 3-way arbitrage
     */
    public boolean isThreeWay() {
        return outcomes != null && outcomes.size() == 3;
    }

    /**
     * Get total number of outcomes
     */
    public int getOutcomeCount() {
        return outcomes != null ? outcomes.size() : 0;
    }
}
