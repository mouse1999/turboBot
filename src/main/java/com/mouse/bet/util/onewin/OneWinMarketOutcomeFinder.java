package com.mouse.bet.util.onewin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Fast Market Outcome Finder for OneWin
 * Static utility class for finding and clicking betting outcomes
 * Uses JavaScript-based search with Playwright clicking
 */
@Slf4j
public class OneWinMarketOutcomeFinder {

    private static final int DEFAULT_WAIT_TIMEOUT_MS = 12000;
    private static final int DEFAULT_POLL_INTERVAL_MS = 300;
    private static final double TOLERANCE_PERCENT = 0.003; // 0.3% tolerance

    // Private constructor to prevent instantiation
    private OneWinMarketOutcomeFinder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Find and click outcome with odds validation (using defaults)
     *
     * @param page The Playwright page
     * @param marketName The market to search for (e.g., "Winner", "Total Points")
     * @param outcomeName The outcome to click (e.g., "Home", "Over 76.5")
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
     * HYBRID approach: JavaScript FINDS, Playwright CLICKS
     */
    private static Map<String, Object> fastSearchOutcome(Page page, String marketName, String outcomeName) {
        String jsSearch = """
    (args) => {
        const { market, outcome } = args;
        
        function normalize(text) {
            if (!text) return '';
            return text.toLowerCase()
                .trim()
                .replace(/\\s+/g, ' ')
                .replace(/[^a-z0-9\\s.:+-]/g, '');
        }
        
        const normalizedTargetMarket = normalize(market);
        const normalizedTargetOutcome = normalize(outcome);
        
        const marketGroups = document.querySelectorAll('div._root_m2ytg_2');
        let matchedMarket = null;
        
        for (const group of marketGroups) {
            const titleElement = group.querySelector('div._title_8ulje_6');
            if (!titleElement) continue;
            
            const marketTitle = titleElement.textContent.trim();
            const normalizedMarket = normalize(marketTitle);
            
            if (normalizedMarket === normalizedTargetMarket) {
                matchedMarket = { group, titleElement, marketTitle };
                break;
            }
        }
        
        if (!matchedMarket) {
            console.log('❌ Exact market not found:', market);
            return { 
                found: false, 
                error: 'Exact market not found',
                searchedMarket: market,
                availableMarkets: Array.from(marketGroups).map(g => 
                    g.querySelector('div._title_8ulje_6')?.textContent.trim()
                ).filter(Boolean)
            };
        }
        
        console.log('✅ Exact market found:', matchedMarket.marketTitle);
        
        const { group, titleElement, marketTitle } = matchedMarket;
        const contentSection = group.querySelector('div._content_8ulje_2');
        
        if (!contentSection) {
            return {
                found: false,
                error: 'Market content section not found',
                matchedMarket: marketTitle
            };
        }
        
        const betButtons = contentSection.querySelectorAll('button._root_1hr84_2');
        let matchedOutcome = null;
        
        for (let i = 0; i < betButtons.length; i++) {
            const button = betButtons[i];
            
            const cell = button.closest('div._cell_9pkob_21');
            if (cell?.querySelector('div._headerCell_xgz91_2')) continue;
            
            const nameSpan = button.querySelector('span._name_1hr84_36');
            const oddsSpan = button.querySelector('span._cf_17if8_2');
            
            if (!nameSpan || !oddsSpan) continue;
            
            const outcomeName = nameSpan.textContent.trim();
            const normalizedOutcome = normalize(outcomeName);
            
            if (normalizedOutcome === normalizedTargetOutcome) {
                const oddsText = oddsSpan.textContent.trim();
                const odds = parseFloat(oddsText);
                
                if (isNaN(odds)) continue;
                
                const isDisabled = button.disabled || 
                                  button.classList.contains('disabled') ||
                                  button.classList.contains('_locked_1hr84_2');
                
                // Mark the button BEFORE creating the result object
                button.setAttribute('data-bet-target', 'true');
                button.setAttribute('data-bet-market', marketTitle);
                button.setAttribute('data-bet-outcome', outcomeName);
                
                // Add visual indicator for debugging
                button.style.outline = '2px solid blue';
                
                console.log('✅ Button marked with data-bet-target=true');
                console.log('Button element:', button);
                console.log('Has attribute:', button.hasAttribute('data-bet-target'));
                console.log('Attribute value:', button.getAttribute('data-bet-target'));
                
                // Verify the marking immediately
                const verifyMarked = document.querySelector('button[data-bet-target="true"]');
                console.log('Verification - Can find marked button:', verifyMarked !== null);
                
                matchedOutcome = {
                    button,
                    buttonIndex: i,
                    outcomeName,
                    odds,
                    isDisabled,
                    markedSuccessfully: verifyMarked !== null
                };
                break;
            }
        }
        
        if (!matchedOutcome) {
            const availableOutcomes = Array.from(betButtons)
                .map(btn => {
                    const cell = btn.closest('div._cell_9pkob_21');
                    if (cell?.querySelector('div._headerCell_xgz91_2')) return null;
                    return btn.querySelector('span._name_1hr84_36')?.textContent.trim();
                })
                .filter(Boolean);
            
            console.log('❌ Exact outcome not found in market:', marketTitle);
            console.log('Searched for:', outcome);
            console.log('Available outcomes:', availableOutcomes);
            
            return {
                found: false,
                error: 'Exact outcome not found in market',
                matchedMarket: marketTitle,
                searchedOutcome: outcome,
                availableOutcomes
            };
        }
        
        console.log('✅ Exact outcome found:', matchedOutcome.outcomeName);
        
        return {
            found: true,
            market: marketTitle,
            outcome: matchedOutcome.outcomeName,
            odds: matchedOutcome.odds,
            disabled: matchedOutcome.isDisabled,
            buttonIndex: matchedOutcome.buttonIndex,
            markedSuccessfully: matchedOutcome.markedSuccessfully
        };
    }
    """;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(
                    jsSearch,
                    Map.of("market", marketName, "outcome", outcomeName)
            );

            // Add Java-side verification
            if (result != null && Boolean.TRUE.equals(result.get("found"))) {
                log.info("Search completed: marked={}", result.get("markedSuccessfully"));

                // Verify from Java side that the button is actually marked
                int markedCount = page.locator("button[data-bet-target='true']").count();
                log.info("Java-side verification: found {} marked buttons", markedCount);

                if (markedCount == 0) {
                    log.error("WARNING: JavaScript marked the button but Java can't find it!");
                }
            }

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
        // Fixed: Use correct selector div._root_m2ytg_2 instead of div._group_ahjwn_2
        const marketGroups = document.querySelectorAll('div._root_m2ytg_2');
        
        marketGroups.forEach(group => {
            const titleElement = group.querySelector('div._title_8ulje_6');
            if (!titleElement) return;
            
            const marketTitle = titleElement.textContent.trim();
            
            // Search within the content section of each market
            const contentSection = group.querySelector('div._content_8ulje_2');
            if (!contentSection) return;
            
            const betButtons = contentSection.querySelectorAll('button._root_1hr84_2');
            
            betButtons.forEach(button => {
                // Skip header cells
                const cell = button.closest('div._cell_9pkob_21');
                if (cell?.querySelector('div._headerCell_xgz91_2')) return;
                
                const nameSpan = button.querySelector('span._name_1hr84_36');
                const oddsSpan = button.querySelector('span._cf_17if8_2');
                
                if (!nameSpan || !oddsSpan) return;
                
                const outcomeName = nameSpan.textContent.trim();
                const oddsText = oddsSpan.textContent.trim();
                const disabled = button.disabled || 
                                button.classList.contains('disabled') ||
                                button.classList.contains('_locked_1hr84_2');
                
                allOutcomes.push({
                    marketTitle: marketTitle,
                    outcomeText: outcomeName,
                    odds: oddsText,
                    disabled: disabled
                });
            });
        });
        
        return { 
            found: allOutcomes.length > 0, 
            totalMarkets: marketGroups.length,
            totalOutcomes: allOutcomes.length,
            allOutcomes: allOutcomes 
        };
    }
    """;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsDebug);
            return result != null ? result : Map.of("found", false, "totalMarkets", 0, "totalOutcomes", 0, "allOutcomes", List.of());
        } catch (Exception e) {
            log.error("Debug info gathering failed: {}", e.getMessage());
            return Map.of("found", false, "totalMarkets", 0, "totalOutcomes", 0, "allOutcomes", List.of());
        }
    }

    /**
     * Process result, validate odds, and click
     */
    private static OutcomeResult processAndClick(Page page, Map<String, Object> result,
                                                 String expectedOutcome, double expectedOdds) {
        String actualOutcome = (String) result.get("outcome");
        Object oddsObj = result.get("odds");
        String marketTitle = (String) result.get("market");
        Boolean isDisabled = (Boolean) result.getOrDefault("disabled", false);

        // Parse odds
        Double odds = null;
        if (oddsObj instanceof Number) {
            odds = ((Number) oddsObj).doubleValue();
        }

        log.info("FOUND: {} → {} @ {}", marketTitle, actualOutcome, odds);

        // Check if disabled
        if (isDisabled) {
            log.warn("✗ Outcome is disabled/locked");
            cleanupMarker(page);
            return OutcomeResult.disabled(marketTitle, actualOutcome, odds);
        }

        // Validate odds
        if (!isOddsAcceptable(expectedOdds, odds)) {
            log.warn("✗ Odds not acceptable: expected {} → got {}", expectedOdds, odds);
            cleanupMarker(page);
            return OutcomeResult.oddsRejected(marketTitle, actualOutcome, odds);
        }

        // Click the marked element
        boolean clicked = clickMarkedOutcome(page, expectedOutcome);

        if (!clicked) {
            return OutcomeResult.clickFailed(marketTitle, actualOutcome, odds);
        }

        log.info("✓ Clicked: {} → {} @ {}", marketTitle, actualOutcome, odds);
        return OutcomeResult.success(marketTitle, actualOutcome, odds);
    }

    /**
     * Click the marked outcome element using Playwright locator
     */
    private static boolean clickMarkedOutcome(Page page, String expectedOutcome) {
        try {
            Locator targetButton = page.locator("button[data-bet-target='true']");

            if (targetButton.count() == 0) {
                log.error("Target button not found (marking failed)");
                return false;
            }

            // Scroll into view
            targetButton.scrollIntoViewIfNeeded();
            Thread.sleep(150);

            // Click
            try {
                targetButton.click(new Locator.ClickOptions().setForce(false).setTimeout(8000));
            } catch (Exception e) {
                log.warn("Primary click failed, attempting force click");
                targetButton.click(new Locator.ClickOptions().setForce(true).setTimeout(8000));
            }

            // Clean up markers
            cleanupMarker(page);

            return true;

        } catch (Exception e) {
            log.error("Click failed: {}", e.getMessage());
            cleanupMarker(page);
            return false;
        }
    }

    /**
     * Check if odds are within acceptable range
     */
    private static boolean isOddsAcceptable(double expectedOdds, Double displayedOdds) {
        if (displayedOdds == null) {
            log.warn("Displayed odds is null");
            return false;
        }

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
    }

    /**
     * Clean up marker attributes
     */
    private static void cleanupMarker(Page page) {
        try {
            page.evaluate("""
                const btn = document.querySelector('button[data-arb-target]');
                if (btn) {
                    btn.removeAttribute('data-arb-target');
                    btn.removeAttribute('data-arb-market');
                    btn.removeAttribute('data-arb-outcome');
                }
            """);
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

        public static OutcomeResult disabled(String marketTitle, String outcomeText, Double odds) {
            return new OutcomeResult(false, true, marketTitle, outcomeText, odds,
                    "Outcome is disabled/locked", null);
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