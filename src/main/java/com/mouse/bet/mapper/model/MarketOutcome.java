package com.mouse.bet.mapper.model;

import lombok.Data;

import java.util.Map;
@Data
public class MarketOutcome {
    private String marketId;
    private String specifier;
    private String name;
    private Map<String, String> outcomes;

    public MarketOutcome(String marketId, String specifier, String name, Map<String, String> outcomes) {
        this.marketId = marketId;
        this.specifier = specifier;
        this.name = name;
        this.outcomes = outcomes;
    }


    public String getOutcome(String oid) {
        return outcomes.get(oid);
    }

    @Override
    public String toString() {
        return "MarketOutcome{" +
                "marketId='" + marketId + '\'' +
                ", specifier='" + specifier + '\'' +
                ", name='" + name + '\'' +
                ", outcomes=" + outcomes +
                '}';
    }
}
