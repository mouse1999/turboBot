package com.mouse.bet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Odd {
    @JsonProperty("sub_event_id")
    private String subEventId;

    @JsonProperty("type_id")
    private Integer typeId;

    private BigDecimal value;  // Odds value
    private BigDecimal prev;
    private String index;
    private String bank;
    private String updated;

    @JsonProperty("rules_ids")
    private List<Integer> rulesIds;

    private Boolean initiator;
    private Map<String, String> crumbs;

}
