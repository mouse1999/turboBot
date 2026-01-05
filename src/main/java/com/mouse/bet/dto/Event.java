package com.mouse.bet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @JsonProperty("sport_id")
    private Integer sportId;

    private String id;
    private String league;

    @JsonProperty("team_1")
    private String team1;

    @JsonProperty("team_2")
    private String team2;

    private String start;  // "2030-01-01 00:00"
    private Boolean live;

    @JsonProperty("break")
    private Boolean breakTime;

    private Integer filtered;

    @JsonProperty("sub_events")
    private List<SubEvent> subEvents;
}
