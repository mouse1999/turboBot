package com.mouse.bet.enums;


import lombok.Getter;

/**
 * Bookmakers as used in Breaking-Bet API.
 * The integer ID comes from the "bookmakers" array in the JWT token and API responses.
 */
@Getter
public enum BookMaker {

    _1XBET(21, "1xBet"),
    BET365(79, "Bet365"),
    WILLIAM_HILL(3, "William Hill"),
    UNIBET(6, "Unibet"),
    PINNACLE(10, "Pinnacle"),
    BETFAIR(14, "Betfair"),
    MARATHON_BET(23, "Marathon Bet"),
    _888SPORT(31, "888Sport"),
    BWIN(91, "Bwin"),
    BETWAY(36, "Betway"),
    LADBROKES(39, "Ladbrokes"),
    POINTSBET(48, "PointsBet"),
    BETVICTOR(49, "BetVictor"),
    CORAL(53, "Coral"),
    _22BET(82, "22Bet"),
    _1WIN(83, "1Win"),
    MELBET(84, "Melbet"),
    PARIMATCH(85, "Parimatch"),
    LEOVEGAS(89, "LeoVegas"),
    BETANO(92, "Betano"),
    BETFRED(93, "Betfred"),
    MATCHBOOK(94, "Matchbook"),
    BET9JA(33, "Bet9ja"),
    UNKNOWN(0, "unknown"),


    // === YOUR TARGET BOOKMAKERS ===
    // TODO: Replace with actual IDs once you find them in the API response or token
    MSPORT(999, "MSport"),         // Placeholder ID — update when discovered
    SPORTYBET(998, "SportyBet");   // Placeholder ID — update when discovered

    private final int breakingBetId;
    private final String displayName;

    BookMaker(int breakingBetId, String displayName) {
        this.breakingBetId = breakingBetId;
        this.displayName = displayName;
    }


    /**
     * Find BookMaker by Breaking-Bet ID (used when parsing API responses)
     */
    public static BookMaker fromBreakingBetId(int id) {
        for (BookMaker bm : values()) {
            if (bm.breakingBetId == id) {
                return bm;
            }
        }
        return null; // or throw IllegalArgumentException
    }

    /**
     * Find BookMaker by display name (useful for config or logs)
     */
    public static BookMaker fromDisplayName(String name) {
        for (BookMaker bm : values()) {
            if (bm.displayName.equalsIgnoreCase(name)) {
                return bm;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
