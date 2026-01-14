package com.mouse.bet.mapper.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
@Data
public abstract class SimilarBookieMapper {
    protected String name;
    protected Map<String, MarketOutcome> marketMap;

    public SimilarBookieMapper(String name) {
        this.name = name;
        this.marketMap = new HashMap<>();
        initializeMarkets();
    }

    /**
     * Build search key - to be overridden by bookmakers with different key structures
     */
    public abstract String buildKey(String marketId, String specifier);

    /**
     * Search market by ID and specifier (or custom identifier)
     */
    public MarketOutcome searchMarket(String marketId, String specifier) {
        String key = buildKey(marketId, specifier);
        return marketMap.get(key);
    }

    /**
     * Get specific outcome from market
     */
    public String getOutcome(String marketId, String specifier, String outcomeId) {
        MarketOutcome market = searchMarket(marketId, specifier);
        if (market == null) {
            return null;
        }
        return market.getOutcome(outcomeId);
    }

    /**
     * Add market to the map
     */
    public void addMarket(String marketId, String specifier, String marketName, Map<String, String> outcomes) {
        String key = buildKey(marketId, specifier);
        MarketOutcome marketOutcome = new MarketOutcome(marketId, specifier, marketName, outcomes);
        marketMap.put(key, marketOutcome);
    }

    /**
     * Abstract method to be implemented by each bookmaker
     */
    public abstract void initializeMarkets();

}
