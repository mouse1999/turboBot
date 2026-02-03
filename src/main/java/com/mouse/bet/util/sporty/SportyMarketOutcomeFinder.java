package com.mouse.bet.util.sporty;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Fast Market Outcome Finder for MSport
 * Static utility class for finding and clicking betting outcomes
 */
@Slf4j
public class SportyMarketOutcomeFinder {

    private static final int DEFAULT_WAIT_TIMEOUT_MS = 12000;
    private static final int DEFAULT_POLL_INTERVAL_MS = 300;
    private static final double TOLERANCE_PERCENT = 0.003; // 0.3% tolerance

    // Private constructor to prevent instantiation
    private SportyMarketOutcomeFinder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Find and click outcome with odds validation (using defaults)
     *
     * @param page The Playwright page
     * @param marketName The market to search for (e.g., "4th game - total points")
     * @param outcomeName The outcome to click (e.g., "Over 16.5")
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
     * @param marketName The market to search for (e.g., "4th game - total points")
     * @param outcomeName The outcome to click (e.g., "Over 16.5")
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
     */
    /**
     * Ultra-fast JavaScript-based search with early exit optimization
     * Marks element for direct clicking (no re-query needed)
     * Uses SportyBet DOM structure
     */
    /**
     * Ultra-fast JavaScript-based search with early exit optimization
     * Marks element for direct clicking (no re-query needed)
     * Uses SportyBet DOM structure
     */
    /**
     * Ultra-fast JavaScript-based search with early exit optimization
     * Marks element for direct clicking (no re-query needed)
     * Uses SportyBet DOM structure
     * Handles multiple wrappers with same market title
     */
    private static Map<String, Object> fastSearchOutcome(Page page, String marketName, String outcomeName) {
        String jsSearch = """
    (args) => {
        const { market, outcome } = args;
        const marketNorm = market.trim();
        const outcomeNorm = outcome.trim();
        const outcomeLower = outcomeNorm.toLowerCase();
        const wrappers = document.querySelectorAll('div.m-table__wrapper');
        
        console.log('🔍 Searching for market:', marketNorm, '| outcome:', outcomeNorm);
        
        // ✅ Early exit on first match - but check BOTH market AND outcome match
        for (let wrapperIndex = 0; wrapperIndex < wrappers.length; wrapperIndex++) {
            const wrapper = wrappers[wrapperIndex];
            const header = wrapper.querySelector('span.m-table-header-title');
            
            if (!header) continue;
            
            const headerText = header.textContent.trim();
            
            // Only proceed if market matches
            if (headerText !== marketNorm) {
                continue;
            }
            
            console.log('📋 Checking wrapper with market:', headerText);
            
            // Look for outcome rows (m-table-row with m-outcome class)
            const outcomeRows = wrapper.querySelectorAll('div.m-table-row.m-outcome');
            console.log('  📊 Found', outcomeRows.length, 'outcome rows in this wrapper');
            
            // Search for the specific outcome in THIS wrapper
            for (let rowIndex = 0; rowIndex < outcomeRows.length; rowIndex++) {
                const row = outcomeRows[rowIndex];
                const cells = row.querySelectorAll('div.m-table-cell--responsive');
                
                for (let cellIndex = 0; cellIndex < cells.length; cellIndex++) {
                    const cell = cells[cellIndex];
                    const textSpan = cell.querySelector('span.m-table-cell-item');
                    
                    if (!textSpan) continue;
                    
                    const text = textSpan.textContent.trim();
                    const disabled = cell.classList.contains('m-table-cell--disable');
                    
                    if (disabled) {
                        console.log('  ⏭️ Skipping disabled:', text);
                        continue;
                    }
                    
                    // Check if this is the outcome we're looking for
                    if (text === outcomeNorm || text.toLowerCase() === outcomeLower) {
                        const oddsSpans = cell.querySelectorAll('span.m-table-cell-item');
                        const odds = oddsSpans.length > 1 ? oddsSpans[1].textContent.trim() : 'N/A';
                        
                        console.log('✅ MATCH FOUND!');
                        console.log('  Market:', headerText);
                        console.log('  Outcome:', text);
                        console.log('  Odds:', odds);
                        
                        // ✅ Mark the cell for direct clicking
                        cell.setAttribute('data-bet-target', 'true');
                        console.log('  ✅ Cell marked with data-bet-target');
                        
                        return {
                            found: true,
                            marketTitle: headerText,
                            outcomeText: text,
                            odds: odds,
                            wrapperIndex: wrapperIndex,
                            rowIndex: rowIndex,
                            cellIndex: cellIndex
                        };
                    }
                }
            }
            
            // Market matched but outcome not found in this wrapper - continue to next wrapper
            console.log('  ❌ Outcome not found in this wrapper, checking next...');
        }
        
        console.log('❌ No match found in any wrapper');
        // ❌ Not found - return false without debug info (handled separately)
        return {
            found: false
        };
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
            const wrappers = document.querySelectorAll('div.m-table__wrapper');
            
            wrappers.forEach(wrapper => {
                const header = wrapper.querySelector('span.m-table-header-title');
                if (!header) return;
                
                const cells = wrapper.querySelectorAll('div.m-table-cell--responsive');
                cells.forEach(cell => {
                    const textSpan = cell.querySelector('span.m-table-cell-item');
                    if (!textSpan) return;
                    
                    const text = textSpan.textContent.trim();
                    const oddsSpans = cell.querySelectorAll('span.m-table-cell-item');
                    const odds = oddsSpans.length > 1 ? oddsSpans[1].textContent.trim() : 'N/A';
                    const disabled = cell.classList.contains('m-table-cell--disable');
                    
                    allOutcomes.push({
                        marketTitle: header.textContent.trim(),
                        outcomeText: text,
                        odds: odds,
                        disabled: disabled
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
            // Simplified selector - just find any element with data-bet-target='true'
            Locator targetCell = page.locator("[data-bet-target='true']");

            if (targetCell.count() == 0) {
                log.error("Target cell not found (marking failed)");
                return false;
            }

            log.info("Found marked element for outcome: '{}'", expectedOutcome);

            // Scroll into view
            targetCell.scrollIntoViewIfNeeded();

            // Visual feedback
            targetCell.evaluate("el => el.style.border = '3px solid red'");
            Thread.sleep(150);

            // Click
            targetCell.evaluate("el => el.click()");

            // Remove visual feedback
            targetCell.evaluate("el => el.style.border = ''");
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
            return false; // Reject if can't parse
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