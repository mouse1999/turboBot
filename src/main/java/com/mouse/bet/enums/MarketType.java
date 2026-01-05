package com.mouse.bet.enums;

public enum MarketType {
    WINNER,           // Home/Away/Draw - button with outcome name as text
    OVER_UNDER,       // Over/Under with line values - has descriptor column
    POINT_HANDICAP,   // Handicap markets - similar structure to O/U
    BOTH_TEAMS_SCORE, // Yes/No markets
    UNKNOWN           // Fallback
}
