package com.mouse.bet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreakingBetResponse {

    private Integer filtered;
    private List<ArbItem> items;
    private List<Event> events;
    private Boolean masked;
    private Long timestamp;
}
