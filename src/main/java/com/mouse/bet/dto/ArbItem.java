package com.mouse.bet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArbItem {
    private String id;

    @JsonProperty("event_id")
    private String eventId;

    private BigDecimal value;  // Profit percentage
    private BigDecimal roi;
    private String created;  // "2026-01-03 10:29:13"
    private String f;

    @JsonProperty("groups_ids")
    private List<Integer> groupsIds;

    private List<Odd> odds;
}
