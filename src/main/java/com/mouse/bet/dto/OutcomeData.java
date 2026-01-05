package com.mouse.bet.dto;

import com.mouse.bet.enums.BookMaker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutcomeData {

    private String subEventId;
    private Integer bookmakerId;
    private BookMaker bookmakerName;
    private String sport;
    private String outcomeName;
    private BigDecimal odds;
    private BigDecimal previousOdds;
    private LocalDateTime updated;
    private Boolean initiator;
    private String progress;
    private String originalId;
    private Boolean reordered;
}
