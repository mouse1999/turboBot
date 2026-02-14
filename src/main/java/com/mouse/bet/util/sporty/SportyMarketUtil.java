package com.mouse.bet.util.sporty;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.mouse.bet.converter.ModelConverter;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.service.ArbOutcomeService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.mouse.bet.util.msport.MSportMarketSearchUtils.takeMarketScreenshot;
import static com.mouse.bet.util.msport.MSportNavigationUtil.randomHumanDelay;

@Slf4j
public class SportyMarketUtil {

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

    private static final double UPPER_TOLERANCE_PERCENT = 0.4; // 0.3% tolerance
    private static final double LOWER__TOLERANCE_PERCENT = 0.1;

    private static boolean verifyBetSlip(Page page, BettingTask task) {
        String market = task.marketType();
        String outcome = task.outcome();

        String jsVerify = """
        (args) => {
            const { expectedOutcome, expectedMarket } = args;
            
            try {
                // Find bet slip container
                const betslipContainer = document.querySelector('.m-betslips .m-list');
                if (!betslipContainer) {
                    return { success: false, error: 'Betslip container not found' };
                }
                
                // Get all bet items
                const betItems = betslipContainer.querySelectorAll('.m-item');
                
                if (betItems.length === 0) {
                    return { success: false, error: 'No bets in slip' };
                }
                
                if (betItems.length > 1) {
                    return { 
                        success: false, 
                        error: `Multiple bets found (${betItems.length}). Expected single bet.` 
                    };
                }
                
                // Extract bet details from first (and only) item
                const betItem = betItems[0];
                
                const outcomeEl = betItem.querySelector('.m-item-play span');
                const marketEl = betItem.querySelector('.m-item-market');
                const oddsEl = betItem.querySelector('.m-item-odds .m-text-main');
                const teamEl = betItem.querySelector('.m-item-team');
                const liveIcon = betItem.querySelector('.m-icon-live');
                
                if (!outcomeEl || !marketEl) {
                    return { 
                        success: false, 
                        error: 'Missing required elements in bet item' 
                    };
                }
                
                const displayedOutcome = outcomeEl.textContent.trim();
                const displayedMarket = marketEl.textContent.trim();
                const displayedOdds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
                const displayedTeam = teamEl ? teamEl.textContent.trim() : 'N/A';
                const isLive = liveIcon !== null;
                
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
                        displayedTeam,
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
                        displayedTeam,
                        isLive
                    };
                }
                
                return {
                    success: true,
                    displayedOutcome,
                    displayedMarket,
                    displayedOdds,
                    displayedTeam,
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
                            "expectedOutcome", outcome,
                            "expectedMarket", market
                    )
            );

            if (result == null) {
                log.warn("❌ Bet verification returned null");
                debugBetslipContents(page, outcome, market);
                return false;
            }

            Boolean success = (Boolean) result.get("success");

            if (Boolean.TRUE.equals(success)) {
                log.info("✅ BET VERIFIED IN SLIP: {} | Market: {} | Odds: {} | Match: {} | Live: {}",
                        result.get("displayedOutcome"),
                        result.get("displayedMarket"),
                        result.get("displayedOdds"),
                        result.get("displayedTeam"),
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

                debugBetslipContents(page, outcome, market);
                return false;
            }

        } catch (Exception e) {
            log.warn("❌ Error verifying bet slip: {}", e.getMessage());
            debugBetslipContents(page, outcome, market);
            return false;
        }
    }


    private static void debugBetslipContents(Page page, String expectedOutcome, String expectedMarket) {
        try {
            boolean slipVisible = page.locator(".m-betslips").isVisible(new Locator.IsVisibleOptions().setTimeout(1000));
            log.warn("🔍 DEBUG: Betslip container visible: {}", slipVisible);

            if (!slipVisible) {
                log.warn("Betslip not visible at all!");
                return;
            }

            Locator items = page.locator(".m-betslips .m-list .m-item");
            int count = items.count();
            log.warn("🔍 Betslip has {} item(s)", count);

            for (int i = 0; i < count; i++) {
                Locator item = items.nth(i);
                String outc = item.locator(".m-item-play span").first().textContent().trim();
                String mkt = item.locator(".m-item-market").textContent().trim();
                String odds = item.locator(".m-item-odds .m-text-main").textContent().trim();
                String team = item.locator(".m-item-team").textContent().trim();

                log.warn("   Item {}: outcome='{}' | market='{}' | odds='{}' | team='{}'",
                        i + 1, outc, mkt, odds, team);
            }

            log.warn("🔍 Expected: outcome='{}' | market='{}'", expectedOutcome, expectedMarket);

            // Optional: screenshot only on debug
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("screenshots/debug-slip-" + System.currentTimeMillis() + ".png")));

        } catch (Exception ex) {
            log.warn("Debug failed: {}", ex.getMessage());
        }
    }


    /**
     * Place bet on the page
     */
    private enum BetslipStatus {
        ACCEPTABLE,
        SUSPENDED,
        UNAVAILABLE,
        ODDS_TOO_LOW,
        ODDS_TOO_HIGH;
    }

    public static boolean placeBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        BigDecimal stake = BigDecimal.valueOf(task.stakeAmount());
//        BetLeg betLeg;
        String arbId = task.taskId();
        BigDecimal expectedOdds = BigDecimal.valueOf(task.expectedOdds());
        long startTime = System.currentTimeMillis();

        // ── CONFIGURABLE MAX DURATION (change this value as needed) ──
        final long MAX_DURATION_MS = 10 * 60 * 1000L; // 12 minutes
        final long deadline = startTime + MAX_DURATION_MS;



        log.info("─────────────────────────────────────────────────────────────");
        log.info("START placeBet() → {} → {} @ {} | Stake: {} | {}",
                task.marketType(), task.outcome(), expectedOdds, stake,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));

        try {
            // ── 1. ENTER STAKE ─────────────────────────────────────
            log.info("[1/6] Entering stake...");
            Locator stakeInput = page.locator("#j_stake_0 input.m-input, .m-input[placeholder*='min']").first();
            if (stakeInput.count() == 0) {
                log.error("Stake input missing");
                return false;
            }
            stake = arbOutcomeService.findByExternalIdAndBookmaker(task.taskId(), task.bookmakerId()).get().getStake();

            stakeInput.fill(""); // Clear
            stakeInput.fill(String.valueOf(stake)); // Type stake directly
            stakeInput.press("Enter");
            log.info("[OK] Stake entered");

            // ── 2. WAIT FOR BET IN SLIP ─────────────────────────────────
            Locator betItemCheck = page.locator("div.m-item, .m-bet-item");
            if (betItemCheck.count() == 0) {
                betItemCheck.first().waitFor(new Locator.WaitForOptions().setTimeout(4000));
                if (page.locator("div.m-item").count() == 0) {
                    log.error("Bet never appeared in slip");
                    return false;
                }
            }
            log.info("[OK] Bet in slip");

            // ── 3. UNKILLABLE LOOP + FULL POPUP HANDLING ─────────────────────
            log.info("[3/6] Starting UNKILLABLE placement loop...");

            boolean betConfirmed = false;
            boolean permanentFailure = false;

            while (!betConfirmed && !permanentFailure && System.currentTimeMillis() < deadline) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                log.info("[Elapsed: {}s / {}s max] Checking state...", elapsedMs / 1000, MAX_DURATION_MS / 1000);

                // ── A. CRITICAL ODDS NOT ACCEPTABLE POPUP ──
                Locator oddsChangePopup = page.locator("div.m-dialog-wrapper p:has-text('Odds not acceptable')").first();
                if (oddsChangePopup.isVisible(new Locator.IsVisibleOptions().setTimeout(500))) {
                    log.error("ODDS NOT ACCEPTABLE POPUP → Permanent failure");
                    permanentFailure = true;
                    break;
                }

                expectedOdds = arbOutcomeService.findByExternalIdAndBookmaker(task.taskId(), task.bookmakerId()).get().getOdds();

                // ── B. MONITOR BETSLIP STATUS ──
                BetslipStatus status = monitorAndHandleOddsInBetslip(page, expectedOdds);
                switch (status) {
                    case UNAVAILABLE:
                        log.error("❌ MARKET UNAVAILABLE → Game over → ABORTING");
                        permanentFailure = true;
                        break;

                    case ODDS_TOO_LOW:
                        log.warn("⚠️ ODDS CURRENTLY TOO LOW → Waiting for odds to improve...");
                        continue;
                    case ODDS_TOO_HIGH:
                        log.warn("⚠️ ODDS CURRENTLY TOO HIGH → Waiting for odds to sync...");
                        continue;

                    case SUSPENDED:
                        log.warn("⏸️ Market suspended → Waiting for odds to return...");
                        continue;
                    case ACCEPTABLE:
                        log.info("✅ Odds acceptable in betslip → Proceeding");
                        break;
                }
                if (permanentFailure) break;

                // ── C. FINAL CONFIRM POPUP ──
                Locator finalConfirm = page.locator("xpath=//button[.//span[text()='Confirm' or text()='Yes' or text()='OK']]").first();
                if (finalConfirm.isVisible(new Locator.IsVisibleOptions().setTimeout(800))) {
                    log.warn("FINAL CONFIRM POPUP → Clicking 'Confirm'");
                    finalConfirm.click(new Locator.ClickOptions()
                            .setForce(true)
                            .setNoWaitAfter(true)
                            .setTimeout(1500));
                }

                // ── D. MAIN BUTTON LOGIC (ONLY IF ODDS ACCEPTABLE) ──
                if (status != BetslipStatus.ACCEPTABLE) {
                    continue;
                }

                Locator btn = page.locator("button.af-button--primary >> visible=true").first();
                if (btn.count() == 0) {
                    log.info("Primary button gone → likely success");
                    continue;
                }

                String text = btn.innerText().trim();
                log.info("Main button: \"{}\" | Disabled: {}", text, btn.isDisabled());

                // ── STEP 1: ALWAYS HANDLE "ACCEPT CHANGES" FIRST ──
                if (text.matches(".*(Accept Changes|Accept|Confirm Changes).*")) {
                    log.warn("→ Clicking 'Accept Changes'");
                    btn.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(true));
                    continue; // Loop again to recheck odds and button state
                }

                // ── STEP 2: RE-ENTER STAKE BEFORE FINAL PLACE BET ──
                stakeInput.fill("");
                stakeInput.fill(stake.toPlainString());
                stakeInput.press("Enter");
                randomHumanDelay(200, 500); // Small delay after re-entering

                // ── STEP 3: ONLY NOW CLICK "PLACE BET" ──
                if (text.matches(".*(Place Bet|Bet Now|Confirm Bet|Place bet).*") && !btn.isDisabled()) {
                    log.info("→ Clicking 'Place Bet'");
                    btn.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(true));

                    // Check rejection right after
                    Locator oddsRejected = page.locator("div.m-dialog-wrapper p:has-text('Odds not acceptable')").first();
                    if (oddsRejected.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                        log.error("ODDS NOT ACCEPTABLE after clicking Place Bet → FAILURE");
                        permanentFailure = true;
                        break;
                    }
                    continue;
                }

                if (text.contains("Place Bet") && btn.isDisabled()) {
                    log.info("Place Bet disabled → waiting...");
                    continue;
                }

                // ── E. SUCCESS DETECTION ──
                boolean successDetected = page.locator("div.m-dialog-wrapper.m-dialog-suc").count() > 0 ||
                        page.locator("text='Submission Successful'").count() > 0 ||
                        page.locator("i.m-icon-suc").count() > 0 ||
                        page.locator("div.booking-code").isVisible(new Locator.IsVisibleOptions().setTimeout(1000));

                if (successDetected) {
                    log.info("SUCCESS CONFIRMED — Official success modal detected!");
                    try {
                        String code = page.locator("div.booking-code").textContent().trim();
                        log.info("BOOKING CODE: {}", code.isEmpty() ? "Hidden" : code);
                    } catch (Exception ignored) {}
                    betConfirmed = true;
                    break;
                }
            }

            // ── HANDLE PERMANENT FAILURE ──
            if (permanentFailure) {
                log.error("BET PLACEMENT FAILED → Unrecoverable state (unavailable / rejected)");
                try {
                    Locator closeBtn = page.locator("div.m-dialog-wrapper button:has-text('OK'), " +
                            "div.m-dialog-wrapper img.close-icon[data-action='close']").first();
                    if (closeBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        closeBtn.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(true));
                    }
                } catch (Exception ignored) {}
                log.info("placeBet() COMPLETED | FAILURE | {}ms", System.currentTimeMillis() - startTime);
                log.info("─────────────────────────────────────────────────────────────");
                return false;
            }

            if (!betConfirmed) {
                log.error("FAILED → Timeout after {} minutes (odds never became acceptable)", MAX_DURATION_MS / 60000);
                return false;
            }

            log.info("[OK] Bet placed successfully");

            // ── FINAL SUCCESS VERIFICATION (kept exactly as original) ──
            try {
                page.waitForFunction("""
                () => {
                    return document.querySelector('div.m-dialog-wrapper.m-dialog-suc') ||
                           document.querySelector('span[data-cms-key="submission_successful"]') ||
                           document.querySelector('div.booking-code');
                }
                """, null, new Page.WaitForFunctionOptions().setTimeout(15000));
                log.info("FINAL SUCCESS VERIFIED");
            } catch (TimeoutError te) {
                log.error("NO SUCCESS MODAL AFTER 15s → BET FAILED");
                return false;
            }

            // ── CLOSE SUCCESS MODAL (kept exactly as original) ──
            try {
                Locator closeSuccess = page.locator("div.m-dialog-suc button:has-text('OK'), " +
                        "div.m-dialog-suc i.m-icon-close, " +
                        "div.m-dialog-suc [data-action='close']").first();
                closeSuccess.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(true));
                log.info("Success modal closed");
            } catch (Exception ignored) {}

            long duration = System.currentTimeMillis() - startTime;
            log.info("placeBet() COMPLETED | SUCCESS | {}ms", duration);
            log.info("─────────────────────────────────────────────────────────────");
            return true;

        } catch (Exception e) {
            log.error("FATAL in placeBet(): {}", e.toString());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Monitors the betslip odds and status - ALWAYS READS FRESH FROM DOM
     * @return BetslipStatus indicating current state
     */
    private static BetslipStatus monitorAndHandleOddsInBetslip(Page page, BigDecimal expectedOdds) {
        try {
            // Fresh read every time via JS
            String oddsValue = (String) page.evaluate("""
            () => {
                const oddsSpan = document.querySelector('div.m-item-odds span.m-text-main');
                if (!oddsSpan || !oddsSpan.offsetParent) return ''; // Not visible
                return oddsSpan.textContent.trim();
            }
            """);

            // Check status indicators
            String statusText = (String) page.evaluate("""
            () => {
                const status = document.querySelector('div.m-item-odds span.m-text-min.m-text-btn');
                return status ? status.textContent.trim().toLowerCase() : '';
            }
            """);

            if (statusText.contains("unavailable")) {
                return BetslipStatus.UNAVAILABLE;
            }
            if (statusText.contains("suspended")) {
                return BetslipStatus.SUSPENDED;
            }

            if (oddsValue.isEmpty()) {
                return BetslipStatus.SUSPENDED;
            }

            BigDecimal currentOdds = new BigDecimal(oddsValue);
            BigDecimal minAcceptable = expectedOdds.multiply(BigDecimal.valueOf(1 - LOWER__TOLERANCE_PERCENT));
            BigDecimal maxAcceptable = expectedOdds.multiply(BigDecimal.valueOf(1 + UPPER_TOLERANCE_PERCENT));

            log.info("📊 LIVE ODDS: {} | Expected: {} | Min: {} | Max: {}" , currentOdds, expectedOdds, minAcceptable, minAcceptable);

            if (currentOdds.compareTo(minAcceptable) < 0) {
                return BetslipStatus.ODDS_TOO_LOW;
            }

            if (currentOdds.compareTo(maxAcceptable) > 0) {
                return BetslipStatus.ODDS_TOO_HIGH;
            }

            // Ensure checkbox is checked
            Boolean checked = (Boolean) page.evaluate("""
            () => {
                const check = document.querySelector('div.m-lay-left i.m-icon-check');
                return check && check.classList.contains('m-icon-check--checked');
            }
            """);

            if (Boolean.FALSE.equals(checked)) {
                page.locator("div.m-lay-left").first().click(new Locator.ClickOptions().setForce(true));
            }

            return BetslipStatus.ACCEPTABLE;

        } catch (Exception e) {
            log.debug("Error reading betslip odds: {}", e.getMessage());
            return BetslipStatus.ACCEPTABLE; // Don't block on read error
        }
    }

    public static boolean clearBetSlip(Page page) {
        try {
            Locator betslipContainer = withLocatorRetry(
                    page, "#j_betslip .m-betslips",
                    loc -> loc.first(),
                    RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
            );

            if (betslipContainer == null || !betslipContainer.isVisible()) {
                log.info("{} Betslip not visible", EMOJI_INFO);
                return true;
            }

            String betCountText = getBetCount(page);
            if ("0".equals(betCountText) || betCountText.isEmpty()) {
                log.info("{} Betslip already empty", EMOJI_SUCCESS);
                return true;
            }

            Locator removeAllBtn = withLocatorRetry(
                    page, "#j_betslip .m-text-min[data-cms-key='remove_all']",
                    loc -> loc.first(),
                    RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
            );

            if (removeAllBtn != null && removeAllBtn.count() > 0 && removeAllBtn.isVisible()) {
                log.info("{} Using 'Remove All' button", EMOJI_TRASH);
                removeAllBtn.click();
                page.waitForTimeout(1000);

                if (isBetstipEmpty(page)) {
                    log.info("{} Betslip cleared via 'Remove All'", EMOJI_SUCCESS);
                    return true;
                }
            }

            return clearBetsIndividually(page);

        } catch (Exception e) {
            log.error("{} Error clearing betslip: {}", EMOJI_ERROR, e.getMessage());
            return false;
        }
    }

    private static boolean clearBetsIndividually(Page page) {
        Locator betList = withLocatorRetry(
                page, "#j_betslip .m-list",
                loc -> loc.first(),
                RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
        );

        if (betList == null || betList.count() == 0 || !betList.isVisible()) {
            return true;
        }

        Locator deleteButtons = withLocatorRetry(
                page, "#j_betslip .m-list .m-item .m-icon-delete",
                loc -> loc,
                RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
        );

        if (deleteButtons == null) return true;

        int betCount = deleteButtons.count();
        if (betCount == 0) return true;

        log.info("{} Removing {} bet(s) individually...", EMOJI_TRASH, betCount);

        for (int i = betCount - 1; i >= 0; i--) {
            try {
                Locator deleteBtn = deleteButtons.nth(i);
                deleteBtn.scrollIntoViewIfNeeded();
                page.waitForTimeout(150);

                deleteBtn.click(new Locator.ClickOptions()
                        .setForce(true)
                        .setTimeout(5000));

                page.waitForTimeout(500);

            } catch (Exception e) {
                log.warn("{} Failed to remove bet {}: {}", EMOJI_WARNING, i, e.getMessage());
            }
        }

        page.waitForTimeout(200);
        boolean cleared = isBetstipEmpty(page);

        if (cleared) {
            log.info("{} All bets removed", EMOJI_SUCCESS);
        } else {
            log.warn("{} Some bets may remain", EMOJI_WARNING);
        }

        return cleared;
    }

    private static boolean isBetstipEmpty(Page page) {
        return page.locator("#j_betslip .m-list .m-item").count() == 0;
    }

    private static String getBetCount(Page page) {
        try {
            return page.locator("#j_betslip .m-bet-count").first().textContent().trim();
        } catch (Exception e) {
            return "0";
        }
    }



    private static  <T> T withLocatorRetry(Page page, String selector, Function<Locator, T> action,
                                           int maxRetries, long timeoutPerAttemptMs, long delayMs) {
        Locator locator = page.locator(selector);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return action.apply(locator);  // e.g., locator::click, locator::textContent, etc.
            } catch (TimeoutError te) {
                log.warn("Timeout attempt {} on '{}'", attempt, selector);
//                if (attempt == maxRetries) throw te;
//                page.waitForTimeout(delayMs);
            }
        }
        log.info("returning null for selector {}", selector);
        return null;  // Never reached
    }

    /**
     * Optimized MSport bet selection - ~2x faster
     * Early exit + marks element for direct clicking
     */
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
            SportyMarketOutcomeFinder.OutcomeResult result =
                    SportyMarketOutcomeFinder.findAndClickOutcome(page, market, outcome, expectedOdds);

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



    /**
     * Ensure "All" tab is active
     */
    private static void ensureAllTabActive(Page page) {
        try {
            Locator allTab = page.locator("div.m-nav-item:has-text('All')");
            if (allTab.isVisible()) {
                String classes = allTab.getAttribute("class");
                if (classes == null || !classes.contains("m-nav-item--active")) {
                    allTab.click(new Locator.ClickOptions().setTimeout(3000));
                    randomHumanDelay(200, 400);
                }
            }
        } catch (Exception e) {
            log.warn("Could not ensure All tab active: {}", e.getMessage());
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



    private static String safeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }








}
