package com.mouse.bet.util.msport;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Fast Market Outcome Finder for MSport
 * Static utility class for finding and clicking betting outcomes
 * Handles both regular markets and specifier-based markets (O/U, Handicap, etc.)
 */
@Slf4j
public class MsportMarketOutcomeFinder {

    private static final int DEFAULT_WAIT_TIMEOUT_MS = 12000;
    private static final int DEFAULT_POLL_INTERVAL_MS = 300;
    private static final double TOLERANCE_PERCENT = 0.010; // 0.3% tolerance

    // Private constructor to prevent instantiation
    private MsportMarketOutcomeFinder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Find and click outcome with odds validation (using defaults)
     *
     * @param page The Playwright page
     * @param marketName The market to search for (e.g., "Points O/U", "Winner")
     * @param outcomeName The outcome to click (e.g., "Over 69.5", "Home", "Under 16.5")
     * @param expectedOdds Expected odds for validation
     * @return OutcomeResult with success status and odds info
     */
    public static OutcomeResult findAndClickOutcome(Page page, String marketName,
                                                    String outcomeName, double expectedOdds) {
        return findAndClickOutcome(page, marketName, outcomeName, expectedOdds,
                DEFAULT_WAIT_TIMEOUT_MS, DEFAULT_POLL_INTERVAL_MS);
    }

    /**
     * Find and click outcome with odds validation (full control)
     * Phase 1: Fast immediate search (< 3.5s)
     * Phase 2: Continuous waiting if not found (configurable timeout)
     *
     * @param page The Playwright page
     * @param marketName The market to search for
     * @param outcomeName The outcome to click
     * @param expectedOdds Expected odds for validation
     * @param waitTimeoutMs Maximum wait time in milliseconds
     * @param pollIntervalMs Polling interval in milliseconds
     * @return OutcomeResult with success status and odds info
     */
    public static OutcomeResult findAndClickOutcome(Page page, String marketName,
                                                    String outcomeName, double expectedOdds,
                                                    int waitTimeoutMs, int pollIntervalMs) {
        long startTime = System.currentTimeMillis();

        try {
            // Phase 1: Fast immediate search
            Map<String, Object> result = fastSearchOutcome(page, marketName, outcomeName);
            Boolean found = (Boolean) result.get("found");

            if (found) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("✓ Found outcome immediately in {}ms", elapsed);
                return processAndClick(page, result, outcomeName, expectedOdds);
            }

            // Phase 2: Continuous waiting with polling
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("⏳ Outcome not found, waiting up to {}ms...", waitTimeoutMs);

            result = waitForOutcome(page, marketName, outcomeName, waitTimeoutMs, pollIntervalMs);
            found = (Boolean) result.get("found");

            if (found) {
                elapsed = System.currentTimeMillis() - startTime;
                log.info("✓ Found outcome after waiting ({}ms total)", elapsed);
                return processAndClick(page, result, outcomeName, expectedOdds);
            }

            // Not found - return debug info
            elapsed = System.currentTimeMillis() - startTime;
            log.warn("✗ Outcome not found after {}ms", elapsed);
            return OutcomeResult.notFound(result);

        } catch (Exception e) {
            log.error("Error finding outcome: {}", e.getMessage(), e);
            cleanupMarker(page);
            return OutcomeResult.error(e.getMessage());
        }
    }

    /**
     * Ultra-fast JavaScript-based search (executes in browser context)
     * Handles both regular markets and specifier-based markets (O/U, Handicap)
     */
//    private static Map<String, Object> fastSearchOutcome(Page page, String marketName, String outcomeName) {
//        String jsSearch = """
//    (args) => {
//        const { market, outcome } = args;
//        const marketNorm = market.trim();
//        const outcomeNorm = outcome.trim();
//        const outcomeLower = outcomeNorm.toLowerCase();
//
//        const marketItems = document.querySelectorAll('.m-market-item');
//        if (!marketItems || marketItems.length === 0) {
//            return { found: false, error: 'No market items found' };
//        }
//
//        // Helper: Extract specifier and value from outcome (e.g., "Over 69.5" -> {type: "Over", value: "69.5"})
//        const parseOutcome = (text) => {
//            const parts = text.trim().split(/\\s+/);
//            if (parts.length === 2) {
//                return { type: parts[0], value: parts[1] };
//            }
//            return { type: text, value: null };
//        };
//
//        const parsedOutcome = parseOutcome(outcomeNorm);
//
//        // Search through all market items
//        for (let i = 0; i < marketItems.length; i++) {
//            const marketItem = marketItems[i];
//            const marketNameEl = marketItem.querySelector('.m-market-item--name span.tw-line-clamp-2');
//
//            if (!marketNameEl) continue;
//
//            const marketText = marketNameEl.textContent.trim();
//
//            // Check if market name matches
//            if (!marketText.toLowerCase().includes(marketNorm.toLowerCase())) {
//                continue;
//            }
//
//            // Found the market - now search for outcome
//            // Check for specifier-based markets (O/U, Handicap, etc.)
//            const isSpecifierMarket = marketItem.querySelector('.m-market-specifier') !== null;
//            const isHandicapMarket = marketItem.querySelector('.m-market-handicap') !== null;
//
//            if (isSpecifierMarket || isHandicapMarket) {
//                // Handle O/U or Handicap markets with rows
//                const rows = marketItem.querySelectorAll('.m-market-row');
//
//                for (let j = 0; j < rows.length; j++) {
//                    const row = rows[j];
//
//                    // Get the specifier (e.g., "69.5" in Points O/U)
//                    const specifierEl = row.querySelector('.m-outcome-desc span');
//                    const specifier = specifierEl ? specifierEl.textContent.trim() : null;
//
//                    // Get all outcomes in this row
//                    const outcomes = row.querySelectorAll('.m-outcome:not(.m-outcome-desc)');
//
//                    for (let k = 0; k < outcomes.length; k++) {
//                        const outcomeEl = outcomes[k];
//
//                        // Skip disabled outcomes
//                        if (outcomeEl.classList.contains('disabled')) continue;
//
//                        // Get outcome type from column position or title
//                        let outcomeType = '';
//                        if (isSpecifierMarket) {
//                            // For O/U: Get titles from the FIRST row in m-market-specifier
//                            // The structure has an empty first column, then "Over", then "Under"
//                            // So we need to add 1 to k to get the correct title
//                            const firstRow = marketItem.querySelector('.m-market-specifier .m-market-row');
//                            if (firstRow) {
//                                const titles = firstRow.querySelectorAll('.m-title');
//                                // titles[0] is empty, titles[1] is "Over", titles[2] is "Under"
//                                // outcomes[0] should map to titles[1], outcomes[1] to titles[2]
//                                if (titles.length > k + 1) {
//                                    outcomeType = titles[k + 1].textContent.trim();
//                                }
//                            }
//                        } else if (isHandicapMarket) {
//                            // For Handicap: Look for desc inside outcome
//                            const descEl = outcomeEl.querySelector('.desc');
//                            if (descEl) {
//                                outcomeType = descEl.textContent.trim();
//                            }
//                        }
//
//                        // Match the outcome
//                        let isMatch = false;
//
//                        if (parsedOutcome.value && specifier) {
//                            // Format: "Over 69.5" or "Under 16.5"
//                            const fullOutcome = `${outcomeType} ${specifier}`.trim();
//                            isMatch = fullOutcome.toLowerCase() === outcomeLower ||
//                                     (outcomeType.toLowerCase() === parsedOutcome.type.toLowerCase() &&
//                                      specifier === parsedOutcome.value);
//                        } else if (isHandicapMarket && outcomeType) {
//                            // Handicap format: "-10.5" or "+9.5"
//                            isMatch = outcomeType.toLowerCase() === outcomeLower ||
//                                     outcomeType === outcomeNorm;
//                        }
//
//                        if (isMatch) {
//                            const oddsEl = outcomeEl.querySelector('.odds .tw-h-full');
//                            const odds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
//
//                            // Mark for clicking
//                            outcomeEl.setAttribute('data-bet-target', 'true');
//
//                            return {
//                                found: true,
//                                marketTitle: marketText,
//                                outcomeText: `${outcomeType} ${specifier || ''}`.trim(),
//                                odds: odds,
//                                marketType: isSpecifierMarket ? 'specifier' : 'handicap'
//                            };
//                        }
//                    }
//                }
//            } else {
//                // Regular market (Winner, Correct Score, etc.)
//                const outcomes = marketItem.querySelectorAll('.m-outcome');
//
//                for (let j = 0; j < outcomes.length; j++) {
//                    const outcomeEl = outcomes[j];
//
//                    // Skip disabled outcomes
//                    if (outcomeEl.classList.contains('disabled')) continue;
//
//                    const descEl = outcomeEl.querySelector('.desc');
//                    if (!descEl) continue;
//
//                    const text = descEl.textContent.trim();
//
//                    // Match outcome
//                    if (text === outcomeNorm || text.toLowerCase() === outcomeLower) {
//                        const oddsEl = outcomeEl.querySelector('.odds .tw-h-full');
//                        const odds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
//
//                        // Mark for clicking
//                        outcomeEl.setAttribute('data-bet-target', 'true');
//
//                        return {
//                            found: true,
//                            marketTitle: marketText,
//                            outcomeText: text,
//                            odds: odds,
//                            marketType: 'regular'
//                        };
//                    }
//                }
//            }
//        }
//
//        return { found: false };
//    }
//    """;
//
//        try {
//            @SuppressWarnings("unchecked")
//            Map<String, Object> result = (Map<String, Object>) page.evaluate(
//                    jsSearch,
//                    Map.of("market", marketName, "outcome", outcomeName)
//            );
//            return result != null ? result : Map.of("found", false);
//        } catch (Exception e) {
//            log.error("Fast search failed: {}", e.getMessage());
//            return Map.of("found", false);
//        }
//    }


    private static Map<String, Object> fastSearchOutcome(Page page, String marketName, String outcomeName) {
        String jsSearch = """
(args) => {
    const { market, outcome } = args;
    const marketNorm = market.trim();
    const outcomeNorm = outcome.trim();
    const outcomeLower = outcomeNorm.toLowerCase();
    
    const marketItems = document.querySelectorAll('.m-market-item');
    if (!marketItems || marketItems.length === 0) {
        return { found: false, error: 'No market items found' };
    }
    
    // Helper: Extract specifier and value from outcome (e.g., "Over 69.5" -> {type: "Over", value: "69.5"})
    const parseOutcome = (text) => {
        const parts = text.trim().split(/\\s+/);
        if (parts.length === 2) {
            return { type: parts[0], value: parts[1] };
        }
        return { type: text, value: null };
    };
    
    // Helper: Parse handicap outcome "Home (+2.5)" -> {team: "Home", value: "+2.5"}
    const parseHandicap = (text) => {
        const match = text.match(/(Home|Away)\\s*\\(([+-]?[\\d.]+)\\)/i);
        if (match) {
            return { team: match[1], value: match[2] };
        }
        return { team: null, value: text };
    };
    
    const parsedOutcome = parseOutcome(outcomeNorm);
    const parsedHandicap = parseHandicap(outcomeNorm);
    
    // Search through all market items
    for (let i = 0; i < marketItems.length; i++) {
        const marketItem = marketItems[i];
        const marketNameEl = marketItem.querySelector('.m-market-item--name span.tw-line-clamp-2');
        
        if (!marketNameEl) continue;
        
        const marketText = marketNameEl.textContent.trim();
        
        // Check if market name matches
        if (!marketText.toLowerCase().includes(marketNorm.toLowerCase())) {
            continue;
        }
        
        // Found the market - now search for outcome
        // Check for specifier-based markets (O/U, Handicap, etc.)
        const isSpecifierMarket = marketItem.querySelector('.m-market-specifier') !== null;
        const isHandicapMarket = marketItem.querySelector('.m-market-handicap') !== null;
        
        if (isSpecifierMarket || isHandicapMarket) {
            // Handle O/U or Handicap markets with rows
            const rows = marketItem.querySelectorAll('.m-market-row');
            
            // Get column titles for handicap markets
            let homeTitle = 'Home';
            let awayTitle = 'Away';
            let startRowIndex = 0;
            
            if (isHandicapMarket) {
                // First row contains the titles
                const headerRow = rows[0];
                if (headerRow) {
                    const titles = headerRow.querySelectorAll('.m-title');
                    if (titles.length >= 2) {
                        homeTitle = titles[0].textContent.trim();
                        awayTitle = titles[1].textContent.trim();
                    }
                }
                startRowIndex = 1; // Skip the header row
            }
            
            for (let j = startRowIndex; j < rows.length; j++) {
                const row = rows[j];
                
                // Get the specifier (e.g., "69.5" in Points O/U)
                const specifierEl = row.querySelector('.m-outcome-desc span');
                const specifier = specifierEl ? specifierEl.textContent.trim() : null;
                
                // Get all outcomes in this row
                const outcomes = row.querySelectorAll('.m-outcome:not(.m-outcome-desc)');
                
                for (let k = 0; k < outcomes.length; k++) {
                    const outcomeEl = outcomes[k];
                    
                    // Skip disabled outcomes
                    if (outcomeEl.classList.contains('disabled')) continue;
                    
                    // Get outcome type from column position or desc
                    let outcomeType = '';
                    if (isSpecifierMarket) {
                        // For O/U: Get titles from the FIRST row in m-market-specifier
                        const firstRow = marketItem.querySelector('.m-market-specifier .m-market-row');
                        if (firstRow) {
                            const titles = firstRow.querySelectorAll('.m-title');
                            if (titles.length > k + 1) {
                                outcomeType = titles[k + 1].textContent.trim();
                            }
                        }
                    } else if (isHandicapMarket) {
                        // For Handicap: Get the desc value (e.g., "+2.5" or "-2.5")
                        const descEl = outcomeEl.querySelector('.desc');
                        if (descEl) {
                            outcomeType = descEl.textContent.trim();
                        }
                    }
                    
                    // Match the outcome
                    let isMatch = false;
                    
                    if (parsedOutcome.value && specifier) {
                        // Format: "Over 69.5" or "Under 16.5"
                        const fullOutcome = `${outcomeType} ${specifier}`.trim();
                        isMatch = fullOutcome.toLowerCase() === outcomeLower || 
                                 (outcomeType.toLowerCase() === parsedOutcome.type.toLowerCase() && 
                                  specifier === parsedOutcome.value);
                    } else if (isHandicapMarket && outcomeType) {
                        // Determine team position (Home = index 0, Away = index 1)
                        const teamPosition = k === 0 ? homeTitle : awayTitle;
                        
                        // Build full outcome: "Home (+2.5)" or "Away (-2.5)"
                        const fullOutcome = `${teamPosition} (${outcomeType})`;
                        
                        // Match logic - be very strict
                        if (parsedHandicap.team && parsedHandicap.value) {
                            // User provided format like "Home (+2.5)"
                            // Must match BOTH team AND value exactly
                            isMatch = 
                                parsedHandicap.team.toLowerCase() === teamPosition.toLowerCase() &&
                                parsedHandicap.value === outcomeType;
                        } else {
                            // User provided just value like "(+2.5)" or "+2.5"
                            // Remove parentheses and match the value
                            const cleanUserInput = outcomeNorm.replace(/[()]/g, '').trim();
                            isMatch = outcomeType === cleanUserInput;
                        }
                        
                        // Also allow exact full string match
                        if (!isMatch) {
                            isMatch = fullOutcome === outcomeNorm || 
                                     fullOutcome.toLowerCase() === outcomeLower;
                        }
                    }
                    
                    if (isMatch) {
                        const oddsEl = outcomeEl.querySelector('.odds .tw-h-full');
                        const odds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
                        
                        // Mark for clicking
                        outcomeEl.setAttribute('data-bet-target', 'true');
                        
                        // Build proper outcome text for return
                        let finalOutcomeText;
                        if (isHandicapMarket) {
                            const teamPosition = k === 0 ? homeTitle : awayTitle;
                            finalOutcomeText = `${teamPosition} (${outcomeType})`;
                        } else {
                            finalOutcomeText = `${outcomeType} ${specifier || ''}`.trim();
                        }
                        
                        return {
                            found: true,
                            marketTitle: marketText,
                            outcomeText: finalOutcomeText,
                            odds: odds,
                            marketType: isSpecifierMarket ? 'specifier' : 'handicap'
                        };
                    }
                }
            }
        } else {
            // Regular market (Winner, Correct Score, etc.)
            const outcomes = marketItem.querySelectorAll('.m-outcome');
            
            for (let j = 0; j < outcomes.length; j++) {
                const outcomeEl = outcomes[j];
                
                // Skip disabled outcomes
                if (outcomeEl.classList.contains('disabled')) continue;
                
                const descEl = outcomeEl.querySelector('.desc');
                if (!descEl) continue;
                
                const text = descEl.textContent.trim();
                
                // Match outcome
                if (text === outcomeNorm || text.toLowerCase() === outcomeLower) {
                    const oddsEl = outcomeEl.querySelector('.odds .tw-h-full');
                    const odds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
                    
                    // Mark for clicking
                    outcomeEl.setAttribute('data-bet-target', 'true');
                    
                    return {
                        found: true,
                        marketTitle: marketText,
                        outcomeText: text,
                        odds: odds,
                        marketType: 'regular'
                    };
                }
            }
        }
    }
    
    return { found: false };
}
""";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(
                    jsSearch,
                    Map.of("market", marketName, "outcome", outcomeName)
            );
            return result != null ? result : Map.of("found", false);
        } catch (Exception e) {
            log.error("Fast search failed: {}", e.getMessage());
            return Map.of("found", false);
        }
    }

    /**
     * Continuous polling wait for outcome to appear
     */
    private static Map<String, Object> waitForOutcome(Page page, String marketName, String outcomeName,
                                                      int waitTimeoutMs, int pollIntervalMs) {
        long startTime = System.currentTimeMillis();
        int attempts = 0;

        while (System.currentTimeMillis() - startTime < waitTimeoutMs) {
            attempts++;

            // Search again
            Map<String, Object> result = fastSearchOutcome(page, marketName, outcomeName);

            if ((Boolean) result.getOrDefault("found", false)) {
                log.info("Found after {} polling attempts", attempts);
                return result;
            }

            // Wait before next poll
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Map.of("found", false);
            }
        }

        // Timeout - gather debug info
        log.warn("Timeout after {} attempts ({}ms)", attempts, waitTimeoutMs);
        return gatherDebugInfo(page);
    }

    /**
     * Gather all available outcomes for debugging
     */
    private static Map<String, Object> gatherDebugInfo(Page page) {
        String jsDebug = """
        () => {
            const allOutcomes = [];
            const marketItems = document.querySelectorAll('.m-market-item');
            
            marketItems.forEach(marketItem => {
                const marketNameEl = marketItem.querySelector('.m-market-item--name span.tw-line-clamp-2');
                if (!marketNameEl) return;
                
                const marketTitle = marketNameEl.textContent.trim();
                
                // Check market type
                const isSpecifierMarket = marketItem.querySelector('.m-market-specifier') !== null;
                const isHandicapMarket = marketItem.querySelector('.m-market-handicap') !== null;
                
                if (isSpecifierMarket || isHandicapMarket) {
                    // Handle O/U or Handicap markets
                    const rows = marketItem.querySelectorAll('.m-market-row');
                    
                    rows.forEach(row => {
                        const specifierEl = row.querySelector('.m-outcome-desc span');
                        const specifier = specifierEl ? specifierEl.textContent.trim() : '';
                        
                        const outcomes = row.querySelectorAll('.m-outcome:not(.m-outcome-desc)');
                        const titles = marketItem.querySelectorAll('.m-market-row .m-title');
                        
                        outcomes.forEach((outcomeEl, idx) => {
                            const disabled = outcomeEl.classList.contains('disabled');
                            
                            let outcomeType = '';
                            if (isSpecifierMarket && titles.length > idx) {
                                outcomeType = titles[idx].textContent.trim();
                            } else if (isHandicapMarket) {
                                const descEl = outcomeEl.querySelector('.desc');
                                outcomeType = descEl ? descEl.textContent.trim() : '';
                            }
                            
                            const oddsEl = outcomeEl.querySelector('.odds .tw-h-full');
                            const odds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
                            
                            allOutcomes.push({
                                marketTitle: marketTitle,
                                outcomeText: `${outcomeType} ${specifier}`.trim(),
                                odds: odds,
                                disabled: disabled
                            });
                        });
                    });
                } else {
                    // Regular market
                    const outcomes = marketItem.querySelectorAll('.m-outcome');
                    
                    outcomes.forEach(outcomeEl => {
                        const descEl = outcomeEl.querySelector('.desc');
                        if (!descEl) return;
                        
                        const text = descEl.textContent.trim();
                        const disabled = outcomeEl.classList.contains('disabled');
                        const oddsEl = outcomeEl.querySelector('.odds .tw-h-full');
                        const odds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
                        
                        allOutcomes.push({
                            marketTitle: marketTitle,
                            outcomeText: text,
                            odds: odds,
                            disabled: disabled
                        });
                    });
                }
            });
            
            return { found: false, allOutcomes: allOutcomes };
        }
        """;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsDebug);
            return result != null ? result : Map.of("found", false, "allOutcomes", List.of());
        } catch (Exception e) {
            return Map.of("found", false, "allOutcomes", List.of());
        }
    }

    /**
     * Process result, validate odds, and click
     */
    private static OutcomeResult processAndClick(Page page, Map<String, Object> result,
                                                 String expectedOutcome, double expectedOdds) {
        String actualOutcome = (String) result.get("outcomeText");
        String actualOddsStr = (String) result.get("odds");
        String marketTitle = (String) result.get("marketTitle");

        log.info("FOUND: {} → {} @ {}", marketTitle, actualOutcome, actualOddsStr);

        // Parse odds
        Double odds = parseOdds(actualOddsStr);

        // Validate odds
        if (!isOddsAcceptable(expectedOdds, actualOddsStr)) {
            log.warn("✗ Odds not acceptable: expected {} → got {}", expectedOdds, actualOddsStr);
            cleanupMarker(page);
            return OutcomeResult.oddsRejected(marketTitle, actualOutcome, odds);
        }

        // Click the marked element
        boolean clicked = clickMarkedOutcome(page, expectedOutcome);

        if (!clicked) {
            return OutcomeResult.clickFailed(marketTitle, actualOutcome, odds);
        }

        log.info("✓ Clicked: {} → {} @ {}", marketTitle, actualOutcome, actualOddsStr);
        return OutcomeResult.success(marketTitle, actualOutcome, odds);
    }

    /**
     * Click the marked outcome element
     */
    private static boolean clickMarkedOutcome(Page page, String expectedOutcome) {
        try {
            Locator targetCell = page.locator(".m-outcome[data-bet-target='true']");

            if (targetCell.count() == 0) {
                log.error("Target cell not found (marking failed)");
                return false;
            }

            // Scroll into view
            targetCell.scrollIntoViewIfNeeded();

            // Visual feedback
            targetCell.evaluate("el => el.style.border = '3px solid red'");
            Thread.sleep(150);

            // Click
//            try {
//                targetCell.click(new Locator.ClickOptions().setForce(true).setTimeout(8000));
//            } catch (Exception e) {
//                log.warn("Primary click failed, attempting JS click");
//                targetCell.evaluate("el => el.click()");
//            }

            targetCell.evaluate("el => el.click()");

            // Remove visual feedback
            targetCell.evaluate("el => el.style.border = ''");
            cleanupMarker(page);



            return true;

        } catch (Exception e) {
            log.error("Click failed: {}", e.getMessage());
            cleanupMarker(page);
            return false;
        }
    }

    /**
     * Parse odds string to double
     */
    private static Double parseOdds(String oddsStr) {
        try {
            return Double.parseDouble(oddsStr);
        } catch (NumberFormatException e) {
            log.warn("⚠ Could not parse odds: {}", oddsStr);
            return null;
        }
    }

    /**
     * Check if odds are within acceptable range
     */
    private static boolean isOddsAcceptable(double expectedOdds, String displayedOddsStr) {
        if (displayedOddsStr == null || displayedOddsStr.trim().isEmpty()) {
            log.warn("Displayed odds string is null or empty");
            return false;
        }

        try {
            double displayedOdds = Double.parseDouble(displayedOddsStr.trim());

            if (expectedOdds <= 0) {
                log.warn("Expected odds must be positive: {}", expectedOdds);
                return false;
            }

            // Calculate allowed range
            double lowerBound = expectedOdds * (1 - TOLERANCE_PERCENT);
            double upperBound = expectedOdds * (1 + TOLERANCE_PERCENT);

            boolean isAcceptable = displayedOdds >= lowerBound && displayedOdds <= upperBound;

            if (isAcceptable) {
                double percentDiff = ((displayedOdds - expectedOdds) / expectedOdds) * 100.0;
                log.debug("Displayed odds {} is {}% from expected {} → ACCEPTED (±{}% tolerance)",
                        displayedOdds, String.format("%.2f", percentDiff), expectedOdds, TOLERANCE_PERCENT * 100);
            } else {
                String reason = displayedOdds < lowerBound ? "too low" : "too high";
                double percentDiff = ((displayedOdds - expectedOdds) / expectedOdds) * 100.0;
                log.debug("Displayed odds {} is {}% {} expected {} → REJECTED (±{}% tolerance)",
                        displayedOdds, String.format("%.2f", Math.abs(percentDiff)), reason,
                        expectedOdds, TOLERANCE_PERCENT * 100);
            }

            return isAcceptable;

        } catch (NumberFormatException e) {
            log.warn("Could not parse displayed odds string: '{}'", displayedOddsStr);
            return false;
        }
    }

    /**
     * Clean up marker attribute
     */
    private static void cleanupMarker(Page page) {
        try {
            page.evaluate("document.querySelector('[data-bet-target]')?.removeAttribute('data-bet-target')");
        } catch (Exception ignored) {}
    }

    /**
     * Result container class
     */
    public static class OutcomeResult {
        public final boolean success;
        public final boolean found;
        public final String marketTitle;
        public final String outcomeText;
        public final Double odds;
        public final String errorMessage;
        public final List<Map<String, Object>> availableOutcomes;

        private OutcomeResult(boolean success, boolean found, String marketTitle,
                              String outcomeText, Double odds, String errorMessage,
                              List<Map<String, Object>> availableOutcomes) {
            this.success = success;
            this.found = found;
            this.marketTitle = marketTitle;
            this.outcomeText = outcomeText;
            this.odds = odds;
            this.errorMessage = errorMessage;
            this.availableOutcomes = availableOutcomes;
        }

        public static OutcomeResult success(String marketTitle, String outcomeText, Double odds) {
            return new OutcomeResult(true, true, marketTitle, outcomeText, odds, null, null);
        }

        public static OutcomeResult oddsRejected(String marketTitle, String outcomeText, Double odds) {
            return new OutcomeResult(false, true, marketTitle, outcomeText, odds,
                    "Odds not acceptable", null);
        }

        public static OutcomeResult clickFailed(String marketTitle, String outcomeText, Double odds) {
            return new OutcomeResult(false, true, marketTitle, outcomeText, odds,
                    "Click failed", null);
        }

        @SuppressWarnings("unchecked")
        public static OutcomeResult notFound(Map<String, Object> debugInfo) {
            List<Map<String, Object>> outcomes = (List<Map<String, Object>>)
                    debugInfo.getOrDefault("allOutcomes", List.of());
            return new OutcomeResult(false, false, null, null, null,
                    "Outcome not found", outcomes);
        }

        public static OutcomeResult error(String errorMessage) {
            return new OutcomeResult(false, false, null, null, null, errorMessage, null);
        }
    }
}