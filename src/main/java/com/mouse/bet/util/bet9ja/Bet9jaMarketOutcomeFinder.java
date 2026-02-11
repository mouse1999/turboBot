package com.mouse.bet.util.bet9ja;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Fast Market Outcome Finder for Bet9ja
 * Static utility class for finding and clicking betting outcomes
 */
@Slf4j
public class Bet9jaMarketOutcomeFinder {

    private static final int DEFAULT_WAIT_TIMEOUT_MS = 12000;
    private static final int DEFAULT_POLL_INTERVAL_MS = 300;
    private static final double TOLERANCE_PERCENT = 0.003; // 0.3% tolerance

    // Private constructor to prevent instantiation
    private Bet9jaMarketOutcomeFinder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Find and click outcome with odds validation (using defaults)
     *
     * @param page The Playwright page
     * @param marketName The market to search for (e.g., "Total", "3way")
     * @param outcomeName The outcome to click (e.g., "Over", "Draw")
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
     * @param marketName The market to search for (e.g., "Total", "3way")
     * @param outcomeName The outcome to click (e.g., "Over", "Draw")
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
     * Ultra-fast JavaScript-based search with early exit optimization
     * Marks element for direct clicking (no re-query needed)
     * Uses Bet9ja DOM structure (accordion-based markets)
     */
    private static Map<String, Object> fastSearchOutcome(Page page, String marketName, String outcomeName) {
        String jsSearch = """
(args) => {
    const { market, outcome } = args;
    const marketNorm = market.trim();
    const marketLower = marketNorm.toLowerCase();
    const outcomeNorm = outcome.trim();
    const outcomeLower = outcomeNorm.toLowerCase();
    
    console.log('🔍 [Bet9ja] Searching for market:', marketNorm, '| outcome:', outcomeNorm);
    
    // Find all accordion items
    const accordionItems = document.querySelectorAll('div.accordion-item');
    console.log('📋 Found', accordionItems.length, 'accordion items');
    
    for (let itemIdx = 0; itemIdx < accordionItems.length; itemIdx++) {
        const item = accordionItems[itemIdx];
        
        // Get market name from accordion-text
        const accordionText = item.querySelector('div.accordion-toggle div.accordion-text');
        if (!accordionText) continue;
        
        const marketText = accordionText.textContent.trim();
        const marketTextLower = marketText.toLowerCase();
        
        // EXACT MATCH - Check if this is the exact market
        if (marketText !== marketNorm && marketTextLower !== marketLower) {
            continue;
        }
        
        console.log('✅ Found exact matching market:', marketText);
        
        // Look for market-container within this accordion
        const marketContainer = item.querySelector('div.market-container');
        if (!marketContainer) {
            console.log('  ⚠️ No market-container found in this market');
            break;
        }
        
        const rows = marketContainer.querySelectorAll('div.market-row');
        console.log('  📊 Found', rows.length, 'rows in market-container');
        
        for (let rowIdx = 0; rowIdx < rows.length; rowIdx++) {
            const row = rows[rowIdx];
            
            const marketItems = row.querySelectorAll('div.market-item');
            console.log('    Found', marketItems.length, 'market items in row', rowIdx);
            
            if (marketItems.length === 0) continue;
            
            // Collect all text values from items without odds (potential selection/team values)
            const nonOddsTexts = [];
            let firstOddsItemIndex = -1;
            
            for (let i = 0; i < marketItems.length; i++) {
                const item = marketItems[i];
                const hasOdds = item.querySelector('div.market-odd') !== null;
                
                if (!hasOdds) {
                    const div = item.querySelector('div');
                    if (div) {
                        const text = div.textContent.trim();
                        if (text) {
                            nonOddsTexts.push(text);
                        }
                    }
                } else if (firstOddsItemIndex === -1) {
                    firstOddsItemIndex = i;
                }
            }
            
            console.log('    Non-odds texts:', nonOddsTexts);
            console.log('    First odds item at index:', firstOddsItemIndex);
            
            // Start from first item with odds
            const startIdx = firstOddsItemIndex >= 0 ? firstOddsItemIndex : 0;
            
            // Loop through outcome items (items with odds)
            for (let itemIdx = startIdx; itemIdx < marketItems.length; itemIdx++) {
                const marketItem = marketItems[itemIdx];
                
                // Get outcome name from txt-gray span
                const outcomeSpan = marketItem.querySelector('div.txt-gray span');
                if (!outcomeSpan) {
                    console.log('      Item', itemIdx, '- No outcome span found, skipping');
                    continue;
                }
                
                const outcomeText = outcomeSpan.textContent.trim();
                
                // Get odds from market-odd
                const marketOdd = marketItem.querySelector('div.market-odd');
                if (!marketOdd) {
                    console.log('      Item', itemIdx, '- No market-odd found, skipping');
                    continue;
                }
                
                // Check if odds are locked/unavailable
                if (marketOdd.classList.contains('locked')) {
                    console.log('      Item', itemIdx, '- Odds locked, skipping');
                    continue;
                }
                
                const oddsContainer = marketOdd.querySelector('div.odd-container div.arrow-container');
                if (!oddsContainer) {
                    console.log('      Item', itemIdx, '- No odds container found, skipping');
                    continue;
                }
                
                // Extract odds text (skip the arrow div)
                const oddsText = Array.from(oddsContainer.childNodes)
                    .filter(node => node.nodeType === Node.TEXT_NODE)
                    .map(node => node.textContent.trim())
                    .join('')
                    .trim();
                
                // Build all possible outcome combinations
                let possibleMatches = [];
                
                // Add the direct outcome text
                possibleMatches.push(outcomeText);
                
                // If we have non-odds texts, combine them with outcome in all possible ways
                if (nonOddsTexts.length > 0) {
                    for (const nonOddsText of nonOddsTexts) {
                        // Check if this is a handicap value
                      const isHandicap = nonOddsText.match(/^[+-][0-9]+[.][0-9]*$/);
                        
                        if (isHandicap) {
                            const hcpValue = nonOddsText;
                            const absValue = hcpValue.replace(/^[+-]/, '');
                            const isNegative = hcpValue.startsWith('-') || (!hcpValue.startsWith('+') && parseFloat(hcpValue) < 0);
                            
                            // Determine sign based on position (first odds item = home, second = away)
                            const relativePosition = itemIdx - firstOddsItemIndex;
                            let sign;
                            
                            if (relativePosition === 0) {
                                // First outcome - use original sign
                                sign = isNegative ? '-' : '+';
                            } else {
                                // Second outcome - flip sign
                                sign = isNegative ? '+' : '-';
                            }
                            
                            // Generate all handicap format variations
                            // Format: "Home (-10.5) Universitet Yugra Surgut"
                            possibleMatches.push(`Home (${sign}${absValue}) ${outcomeText}`);
                            possibleMatches.push(`Away (${sign}${absValue}) ${outcomeText}`);
                            
                            // Format: "Home -10.5 Universitet Yugra Surgut"
                            possibleMatches.push(`Home ${sign}${absValue} ${outcomeText}`);
                            possibleMatches.push(`Away ${sign}${absValue} ${outcomeText}`);
                            
                            // Format: "Universitet Yugra Surgut (-10.5) Home"
                            possibleMatches.push(`${outcomeText} (${sign}${absValue}) Home`);
                            possibleMatches.push(`${outcomeText} (${sign}${absValue}) Away`);
                            
                            // Format: "Universitet Yugra Surgut -10.5 Home"
                            possibleMatches.push(`${outcomeText} ${sign}${absValue} Home`);
                            possibleMatches.push(`${outcomeText} ${sign}${absValue} Away`);
                            
                            // Format: "Universitet Yugra Surgut Home (-10.5)"
                            possibleMatches.push(`${outcomeText} Home (${sign}${absValue})`);
                            possibleMatches.push(`${outcomeText} Away (${sign}${absValue})`);
                            
                            // Format: "(-10.5) Universitet Yugra Surgut Home"
                            possibleMatches.push(`(${sign}${absValue}) ${outcomeText} Home`);
                            possibleMatches.push(`(${sign}${absValue}) ${outcomeText} Away`);
                            
                            // Format: "-10.5 Universitet Yugra Surgut"
                            possibleMatches.push(`${sign}${absValue} ${outcomeText}`);
                            possibleMatches.push(`${outcomeText} ${sign}${absValue}`);
                            
                            // Format: "(-10.5) Universitet Yugra Surgut"
                            possibleMatches.push(`(${sign}${absValue}) ${outcomeText}`);
                            possibleMatches.push(`${outcomeText} (${sign}${absValue})`);
                            
                        } else {
                            // Non-handicap combination (like "2.5 Over", "Current Score 1:0")
                            possibleMatches.push(`${nonOddsText} ${outcomeText}`);
                            possibleMatches.push(`${outcomeText} ${nonOddsText}`);
                        }
                    }
                    
                    // Also combine ALL non-odds texts with outcome
                    if (nonOddsTexts.length > 1) {
                        const combined = nonOddsTexts.join(' ');
                        possibleMatches.push(`${combined} ${outcomeText}`);
                        possibleMatches.push(`${outcomeText} ${combined}`);
                    }
                }
                
                console.log('      Checking:', possibleMatches.slice(0, 5).join(' | '), '... (', possibleMatches.length, 'total) | Odds:', oddsText);
                
                // Check for exact match (case-insensitive)
                const isMatch = possibleMatches.some(possible => {
                    const possibleLower = possible.toLowerCase();
                    return outcomeNorm === possible || outcomeLower === possibleLower;
                });
                
                if (isMatch) {
                    console.log('✅ EXACT MATCH FOUND!');
                    console.log('  Market:', marketText);
                    console.log('  Non-odds values:', nonOddsTexts);
                    console.log('  Outcome:', outcomeText);
                    console.log('  Odds:', oddsText);
                    
                    // Mark the odds element for targeting
                    marketOdd.setAttribute('data-bet-target', 'true');
                    
                    return {
                        found: true,
                        marketTitle: marketText,
                        selectionValues: nonOddsTexts,
                        outcomeText: outcomeText,
                        fullOutcome: possibleMatches.find(p => 
                            p.toLowerCase() === outcomeLower || p === outcomeNorm
                        ),
                        odds: oddsText,
                        itemIndex: itemIdx,
                        rowIndex: rowIdx,
                        marketItemIndex: itemIdx
                    };
                }
            }
        }
        
        console.log('  ❌ Exact outcome not found in this market');
        break;
    }
    
    console.log('❌ No exact match found');
    return { found: false };
}
""";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(
                    jsSearch,
                    Map.of("market", marketName, "outcome", outcomeName)
            );
            return result != null ? result : Map.of("found", false, "error", "Null result from JavaScript");
        } catch (Exception e) {
            log.error("Fast search failed: {}", e.getMessage());
            return Map.of("found", false, "error", "Exception: " + e.getMessage());
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
            const accordionItems = document.querySelectorAll('div.accordion-item');
            
            accordionItems.forEach(item => {
                const marketTitle = item.querySelector('div.accordion-text');
                if (!marketTitle) return;
                
                const marketText = marketTitle.textContent.trim();
                const marketContainers = item.querySelectorAll('div.market-container');
                
                marketContainers.forEach(container => {
                    const marketRows = container.querySelectorAll('div.market-row');
                    
                    marketRows.forEach(row => {
                        const marketItems = row.querySelectorAll('div.market-item');
                        
                        marketItems.forEach(marketItemDiv => {
                            const textDiv = marketItemDiv.querySelector('div.txt-gray.txt-cut span');
                            if (!textDiv) return;
                            
                            const text = textDiv.textContent.trim();
                            const oddsDiv = marketItemDiv.querySelector('div.market-odd');
                            if (!oddsDiv) return;                            
                            const isLocked = oddsDiv.classList.contains('locked');
                            
                            let odds = 'N/A';
                            if (!isLocked) {
                                const oddContainer = oddsDiv.querySelector('div.odd-container div.arrow-container');
                                if (oddContainer) {
                                    const oddsText = oddContainer.textContent.trim();
                                    odds = oddsText.replace(/[^0-9.]/g, '').trim();
                                }
                            }
                            
                            allOutcomes.push({
                                marketTitle: marketText,
                                outcomeText: text,
                                odds: odds,
                                disabled: isLocked
                            });
                        });
                    });
                });
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
            // Find the marked odds div
            Locator targetOddsDiv = page.locator("[data-bet-target='true']");

            if (targetOddsDiv.count() == 0) {
                log.error("Target odds div not found (marking failed)");
                return false;
            }

            log.info("Found marked element for outcome: '{}'", expectedOutcome);

            // Scroll into view
            targetOddsDiv.scrollIntoViewIfNeeded();

            // Visual feedback
            targetOddsDiv.evaluate("el => el.style.border = '3px solid red'");
            Thread.sleep(150);

            // Click
            targetOddsDiv.evaluate("el => el.click()");

            // Remove visual feedback
            targetOddsDiv.evaluate("el => el.style.border = ''");
            Thread.sleep(100);

            cleanupMarker(page);

            log.info("Successfully clicked marked element");
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