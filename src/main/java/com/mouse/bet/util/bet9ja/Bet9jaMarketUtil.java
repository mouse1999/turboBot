package com.mouse.bet.util.bet9ja;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.mouse.bet.converter.ModelConverter;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.orchestrator.model.BetLeg;
import com.mouse.bet.service.ArbOutcomeService;
import com.mouse.bet.util.sporty.SportyMarketOutcomeFinder;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.mouse.bet.util.msport.MSportMarketSearchUtils.takeMarketScreenshot;
import static com.mouse.bet.util.msport.MSportNavigationUtil.randomHumanDelay;


@Slf4j
public class Bet9jaMarketUtil {

    private static final String EMOJI_NAVIGATION = "🧭";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_START = "▶️";
    private static final String EMOJI_BET = "🎯";
    private static final String EMOJI_SEARCH = "🔍";
    private static final String EMOJI_GAME = "🎮";
    private static final String EMOJI_CLOCK = "⏰";
    private static final String EMOJI_HEALTH = "💚";

    private static final String  EMOJI_INFO = "";
    private static final String EMOJI_TRASH = "";
    private static final String EMOJI_TARGET = "";
    private static final String EMOJI_ROCKET = "";



    private static final int RETRY_MAX_ATTEMPTS = 3;
    private static final long RETRY_TIMEOUT_MS = 10_000;
    private static final long RETRY_DELAY_MS = 1000;

    private static final int TOLERANCE_PERCENT = (int) 0.003;
    public static boolean selectAndVerifyBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        String market = task.marketType().trim();
        String outcome = task.outcome().trim();

        log.info("Selecting: {} → {}", market, outcome);

        try {
            // Ensure "All" tab is active
            ensureAllTabActive(page);

            // Get fresh task for latest odds
            BettingTask freshTask = getFreshTask(task, arbOutcomeService);
            if (freshTask != null) {
                log.info("Using fresh betting task from DB");
                task = freshTask;
            }

            double expectedOdds = task.expectedOdds();

            // ⚡ Use optimized finder with automatic waiting
            Bet9jaMarketOutcomeFinder.OutcomeResult result =
                    Bet9jaMarketOutcomeFinder.findAndClickOutcome(page, market, outcome, expectedOdds);

            // Handle not found
            if (!result.found) {
                log.error("Market '{}' or outcome '{}' NOT FOUND", market, outcome);

                if (result.availableOutcomes != null && !result.availableOutcomes.isEmpty()) {
                    log.warn("=== AVAILABLE OUTCOMES ===");
                    result.availableOutcomes.forEach(entry -> {
                        String status = (Boolean) entry.get("disabled") ? " [DISABLED]" : "";
                        log.warn(" → {} @ {} | Market: {}{}",
                                entry.get("outcomeText"), entry.get("odds"),
                                entry.get("marketTitle"), status);
                    });
                    log.warn("=== END DEBUG ===");
                }

                takeMarketScreenshot(page, "not-found-" + safeFileName(market + "-" + outcome));
                return false;
            }

            // Handle click/odds failure
            if (!result.success) {
                log.warn("Selection failed: {}", result.errorMessage);

                if ("Odds not acceptable".equals(result.errorMessage)) {
                    log.warn("Odds drifted: expected {} → got {}", expectedOdds, result.odds);
                    // TODO: Uncomment to enable strict odds rejection
                     takeMarketScreenshot(page, "odds-rejected-" + safeFileName(market + "-" + outcome));
                     return false;
                }

                takeMarketScreenshot(page, "failed-" + safeFileName(market + "-" + outcome));
                return false;
            }

            // Verify outcome match (sanity check)
            if (!result.outcomeText.equalsIgnoreCase(outcome)) {
                log.warn("Outcome mismatch: expected '{}' → got '{}'", outcome.trim(), result.outcomeText);
                takeMarketScreenshot(page, "mismatch-" + safeFileName(market + "-" + outcome));
                return false;
            }

            randomHumanDelay(200, 400);

            // Verify bet slip
            if (!verifyBetSlip(page, task)) {
                log.error("{} {} Bet slip verification failed", EMOJI_ERROR, EMOJI_BET);
                return false;
            }

            log.info("✅ CLICKED: {} → {} @ {}", result.marketTitle, result.outcomeText, result.odds);
            return true;

        } catch (Exception e) {
            log.error("Failed to select {} → {} | Error: {}", market, outcome, e.getMessage());
            takeMarketScreenshot(page, "error-" + safeFileName(market + "-" + outcome));
            return false;
        }
    }


    private static boolean verifyBetSlip(Page page, BettingTask task) {

        String expectedMarket = task.marketType();
        String expectedOutcome = task.outcome();
        String jsVerify = """
        (args) => {
            const { expectedOutcome, expectedMarket } = args;
            
            try {
                // Find bet slip body
                const betslipBody = document.querySelector('div.betslip__body');
                if (!betslipBody) {
                    return { success: false, error: 'Betslip body not found' };
                }
                
                // Get all bet matches
                const betMatches = betslipBody.querySelectorAll('div.betslip__match');
                
                if (betMatches.length === 0) {
                    return { success: false, error: 'No bets in slip' };
                }
                
                if (betMatches.length > 1) {
                    return { 
                        success: false, 
                        error: `Multiple bets found (${betMatches.length}). Expected single bet.` 
                    };
                }
                
                // Extract bet details from first (and only) match
                const betMatch = betMatches[0];
                
                // Get match/event name
                const matchHeadStrong = betMatch.querySelector('div.betslip__match-head strong');
                const matchName = matchHeadStrong ? matchHeadStrong.textContent.trim() : 'N/A';
                
                // Get live score if present
                const liveScore = betMatch.querySelector('div.betslip__match-head span.txt-orange');
                const score = liveScore ? liveScore.textContent.trim() : null;
                const isLive = score !== null;
                
                // Get outcome (strong tag in betslip__match-box)
                const outcomeEl = betMatch.querySelector('div.betslip__match-box div.betslip__match-row strong');
                if (!outcomeEl) {
                    return { 
                        success: false, 
                        error: 'Outcome element not found in bet slip' 
                    };
                }
                const displayedOutcome = outcomeEl.textContent.trim();
                
                // Get market (second row in betslip__match-box)
                const marketRows = betMatch.querySelectorAll('div.betslip__match-box div.betslip__match-row');
                let displayedMarket = 'N/A';
                
                if (marketRows.length >= 2) {
                    const marketEl = marketRows[1].querySelector('div.betslip__match-item');
                    if (marketEl) {
                        displayedMarket = marketEl.textContent.trim();
                    }
                }
                
                // Get odds
                const oddsEl = betMatch.querySelector('div.betslip__match-odds span.txt-darkorange, div.betslip__match-odds span');
                const displayedOdds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
                
                // Case-insensitive comparison
                const outcomeMatch = displayedOutcome.toLowerCase() === expectedOutcome.toLowerCase();
                const marketMatch = displayedMarket.toLowerCase() === expectedMarket.toLowerCase();
                
                if (!outcomeMatch) {
                    return {
                        success: false,
                        error: 'Outcome mismatch',
                        expected: expectedOutcome,
                        actual: displayedOutcome,
                        displayedMarket,
                        displayedOdds,
                        matchName,
                        score,
                        isLive
                    };
                }
                
                if (!marketMatch) {
                    return {
                        success: false,
                        error: 'Market mismatch',
                        expected: expectedMarket,
                        actual: displayedMarket,
                        displayedOutcome,
                        displayedOdds,
                        matchName,
                        score,
                        isLive
                    };
                }
                
                return {
                    success: true,
                    displayedOutcome,
                    displayedMarket,
                    displayedOdds,
                    matchName,
                    score,
                    isLive
                };
                
            } catch (err) {
                return { 
                    success: false, 
                    error: 'JavaScript error: ' + err.message 
                };
            }
        }
        """;

        try {
            // Wait briefly for betslip to populate
            page.waitForTimeout(1500);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(
                    jsVerify,
                    Map.of(
                            "expectedOutcome", expectedOutcome,
                            "expectedMarket", expectedMarket
                    )
            );

            if (result == null) {
                log.warn("❌ Bet verification returned null");
                debugBetslipContents(page, expectedOutcome, expectedMarket);
                return false;
            }

            Boolean success = (Boolean) result.get("success");

            if (Boolean.TRUE.equals(success)) {
                log.info("✅ BET VERIFIED IN SLIP: {} | Market: {} | Odds: {} | Match: {} | Score: {} | Live: {}",
                        result.get("displayedOutcome"),
                        result.get("displayedMarket"),
                        result.get("displayedOdds"),
                        result.get("matchName"),
                        result.get("score"),
                        result.get("isLive"));
                return true;
            } else {
                String error = (String) result.get("error");

                if ("Outcome mismatch".equals(error)) {
                    log.warn("⚠️ Outcome mismatch: expected '{}' → got '{}'",
                            result.get("expected"), result.get("actual"));
                } else if ("Market mismatch".equals(error)) {
                    log.warn("⚠️ Market mismatch: expected '{}' → got '{}'",
                            result.get("expected"), result.get("actual"));
                } else {
                    log.warn("⚠️ Bet verification failed: {}", error);
                }

                debugBetslipContents(page, expectedOutcome, expectedMarket);
                return false;
            }

        } catch (Exception e) {
            log.warn("❌ Error verifying bet slip: {}", e.getMessage());
            debugBetslipContents(page, expectedOutcome, expectedMarket);
            return false;
        }
    }

    /**
     * Debug betslip contents for troubleshooting
     */
    private static void debugBetslipContents(Page page, String expectedOutcome, String expectedMarket) {
        try {
            boolean slipVisible = page.locator("div.betslip__body").isVisible(
                    new Locator.IsVisibleOptions().setTimeout(1000));
            log.warn("🔍 DEBUG: Betslip body visible: {}", slipVisible);

            if (!slipVisible) {
                log.warn("Betslip not visible at all!");
                return;
            }

            Locator matches = page.locator("div.betslip__body div.betslip__match");
            int count = matches.count();
            log.warn("🔍 Betslip has {} match(es)", count);

            for (int i = 0; i < count; i++) {
                Locator match = matches.nth(i);

                String matchName = "N/A";
                try {
                    matchName = match.locator("div.betslip__match-head strong").first().textContent().trim();
                } catch (Exception ignored) {}

                String score = "N/A";
                try {
                    score = match.locator("div.betslip__match-head span.txt-orange").first().textContent().trim();
                } catch (Exception ignored) {}

                String outcome = "N/A";
                try {
                    outcome = match.locator("div.betslip__match-box div.betslip__match-row strong")
                            .first().textContent().trim();
                } catch (Exception ignored) {}

                String market = "N/A";
                try {
                    Locator rows = match.locator("div.betslip__match-box div.betslip__match-row");
                    if (rows.count() >= 2) {
                        market = rows.nth(1).locator("div.betslip__match-item")
                                .first().textContent().trim();
                    }
                } catch (Exception ignored) {}

                String odds = "N/A";
                try {
                    odds = match.locator("div.betslip__match-odds span").first().textContent().trim();
                } catch (Exception ignored) {}

                log.warn("   Match {}: outcome='{}' | market='{}' | odds='{}' | event='{}' | score='{}'",
                        i + 1, outcome, market, odds, matchName, score);
            }

            log.warn("🔍 Expected: outcome='{}' | market='{}'", expectedOutcome, expectedMarket);

        } catch (Exception ex) {
            log.warn("Debug failed: {}", ex.getMessage());
        }
    }



    /**
     * Ensure "All" tab is active
     */
    /**
     * Ensure the "All" markets tab is active before searching
     * Bet9ja has tabs: Popular Markets, Minutes, Halves, Player, Combo +, All
     */
    private static void ensureAllTabActive(Page page) {
        String jsEnsureAllTab = """
        () => {
            try {
                // Find all navigation items
                const navItems = document.querySelectorAll('li.sports-view__bar-navitem');
                
                if (navItems.length === 0) {
                    return { success: false, error: 'No navigation items found' };
                }
                
                // Find the "All" tab by exact text match
                let allTab = null;
                for (let i = 0; i < navItems.length; i++) {
                    const item = navItems[i];
                    const text = item.textContent.trim();
                    
                    if (text === 'All') {
                        allTab = item;
                        break;
                    }
                }
                
                if (!allTab) {
                    return { success: false, error: 'All tab not found' };
                }
                
                // Check if already active
                const classes = allTab.className || '';
                const isActive = classes.includes('sports-view__bar-navitem--current');
                
                if (isActive) {
                    return { 
                        success: true, 
                        alreadyActive: true,
                        message: 'All tab is already active' 
                    };
                }
                
                // Click the tab to activate it
                allTab.click();
                
                return { 
                    success: true, 
                    alreadyActive: false,
                    message: 'Successfully clicked All tab' 
                };
                
            } catch (err) {
                return { 
                    success: false, 
                    error: 'JavaScript error: ' + err.message 
                };
            }
        }
        """;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsEnsureAllTab);

            if (result == null) {
                log.warn("Could not ensure 'All' tab active: null result");
                return;
            }

            Boolean success = (Boolean) result.get("success");

            if (Boolean.TRUE.equals(success)) {
                Boolean alreadyActive = (Boolean) result.get("alreadyActive");
                String message = (String) result.get("message");

                if (Boolean.TRUE.equals(alreadyActive)) {
                    log.debug("'All' markets tab is already active");
                } else {
                    log.info("Switched to 'All' markets tab");
                    randomHumanDelay(200, 400);
                }
            } else {
                String error = (String) result.get("error");
                log.warn("Could not ensure 'All' tab active: {}", error);
            }

        } catch (Exception e) {
            log.warn("Could not ensure 'All' tab active: {}", e.getMessage());
        }
    }

    /**
     * Get fresh task from database
     */
    private static BettingTask getFreshTask(BettingTask task, ArbOutcomeService arbOutcomeService) {
        try {
            return ModelConverter.convertFromArbOutcome(
                    arbOutcomeService.findByExternalIdAndBookmaker(task.taskId(), task.bookmakerId())
                            .orElse(null)
            );
        } catch (Exception e) {
            log.warn("Could not fetch fresh task: {}", e.getMessage());
            return null;
        }
    }

    public static void clearBetSlip(Page page) {
    }

    private static String safeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
