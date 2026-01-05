package com.mouse.bet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubEvent {

    @JsonProperty("bookmaker_id")
    private Integer bookmakerId;

    private String id;
    private String sport;
    private String league;

    @JsonProperty("team_1")
    private String team1;

    @JsonProperty("team_2")
    private String team2;

    private String start;

    @JsonProperty("original_id")
    private String originalId;

    private Boolean reordered;
    private String progress;
    private Object crumbs;
}
