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

    private static final int TOLERANCE_PERCENT = (int) 0.003;

    private static boolean verifyBetSlip(Page page,  BettingTask task) {
        String market = task.marketType();
        String outcome = task.outcome();

        try {
            // 1. Fast check: is there exactly 1 bet item in the slip?
            Locator betItem = page.locator(".m-betslips .m-list .m-item") // More resilient than #j_betslip
                    .first();

            // Wait for it to be visible (auto-waiting + low timeout)
            if (!betItem.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                log.warn("⏱️ Bet not appeared in slip within 3s");
                debugBetslipContents(page, outcome, market);
                return false;
            }

            // 2. Extract all texts in parallel — super fast for single item
            String displayedOutcome = betItem.locator(".m-item-play span")
                    .first()
                    .textContent(new Locator.TextContentOptions().setTimeout(1000))
                    .trim();

            String displayedMarket = betItem.locator(".m-item-market")
                    .textContent(new Locator.TextContentOptions().setTimeout(800))
                    .trim();

            String displayedOdds = betItem.locator(".m-item-odds .m-text-main")
                    .textContent(new Locator.TextContentOptions().setTimeout(800))
                    .trim();

            String displayedTeam = betItem.locator(".m-item-team")
                    .textContent(new Locator.TextContentOptions().setTimeout(800))
                    .trim();

            boolean isLive = betItem.locator(".m-icon-live").count() > 0;

            // 3. Verification
            if (!displayedOutcome.equalsIgnoreCase(outcome)) {
                log.warn("⚠️ Outcome mismatch: expected '{}' → got '{}'", outcome, displayedOutcome);
                debugBetslipContents(page, outcome, market);
                return false;
            }

            if (!displayedMarket.equalsIgnoreCase(market)) {
                log.warn("⚠️ Market mismatch: expected '{}' → got '{}'", market, displayedMarket);
                debugBetslipContents(page, outcome, market);
                return false;
            }

            log.info("✅ BET VERIFIED IN SLIP: {} | Market: {} | Odds: {} | Match: {} | Live: {}",
                    displayedOutcome, displayedMarket, displayedOdds, displayedTeam, isLive);

            return true;

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
            BigDecimal minAcceptable = expectedOdds.multiply(BigDecimal.valueOf(1 - TOLERANCE_PERCENT));
            BigDecimal maxAcceptable = expectedOdds.multiply(BigDecimal.valueOf(1 + TOLERANCE_PERCENT));

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

    public static boolean selectAndVerifyBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        String market = task.marketType().trim();
        String outcome = task.outcome().trim();

        log.info("Selecting: {} → {}", market, outcome);

        try {
            // Ensure "All" tab is active
            Locator allTab = page.locator("div.m-nav-item:has-text('All')");
            if (allTab.isVisible()) {
                String classes = allTab.getAttribute("class");
                if (classes == null || !classes.contains("m-nav-item--active")) {
                    allTab.click(new Locator.ClickOptions().setTimeout(3000));
                    randomHumanDelay(200, 400);
                }
            }

            // JavaScript to find the best matching cell
            String jsFind = """
                (args) => {
                    const { market, outcome } = args;
                    const marketNorm = market.trim();
                    const outcomeNorm = outcome.trim();
                    const outcomeLower = outcomeNorm.toLowerCase();
                    const wrappers = document.querySelectorAll('div.m-table__wrapper');
                    const allOutcomes = [];
                    let bestMatch = null;
                    let wrapperIndex = 0;

                    wrappers.forEach(wrapper => {
                        const header = wrapper.querySelector('span.m-table-header-title');
                        if (!header || !header.textContent.trim().includes(marketNorm)) {
                            wrapperIndex++;
                            return;
                        }

                        const cells = wrapper.querySelectorAll('div.m-table-cell--responsive');
                        let cellIndex = 0;
                        cells.forEach(cell => {
                            const textSpan = cell.querySelector('span.m-table-cell-item');
                            if (!textSpan) {
                                cellIndex++;
                                return;
                            }
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

                            if (!disabled && (text === outcomeNorm || text.toLowerCase() === outcomeLower)) {
                                if (!bestMatch) {
                                    bestMatch = {
                                        wrapperIndex: wrapperIndex,
                                        cellIndex: cellIndex,
                                        outcomeText: text,
                                        odds: odds
                                    };
                                }
                            }
                            cellIndex++;
                        });
                        wrapperIndex++;
                    });

                    return {
                        found: bestMatch,
                        allOutcomes: allOutcomes,
                        matchingBlockCount: wrapperIndex
                    };
                }
                """;

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsFind, Map.of("market", market, "outcome", outcome));
            Object foundObj = result.get("found");

            if (foundObj == null) {
                log.error("Market '{}' or outcome '{}' NOT FOUND", market, outcome);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> allOutcomes = (List<Map<String, Object>>) result.get("allOutcomes");
                log.warn("=== AVAILABLE OUTCOMES ===");
                allOutcomes.forEach(entry -> {
                    String status = (Boolean) entry.get("disabled") ? " [DISABLED]" : "";
                    log.warn(" → {} @ {} | Market: {}{}",
                            entry.get("outcomeText"), entry.get("odds"), entry.get("marketTitle"), status);
                });
                log.warn("=== END DEBUG ===");
                takeMarketScreenshot(page, "not-found-" + safeFileName(market + "-" + outcome));
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> match = (Map<String, Object>) foundObj;
            int wrapperIdx = (Integer) match.get("wrapperIndex");
            int cellIdx = (Integer) match.get("cellIndex");
            String actualOutcome = (String) match.get("outcomeText");
            String actualOddsStr = (String) match.get("odds");

            log.info("FOUND: {} → {} @ {}", market, actualOutcome, actualOddsStr);

            // Verify outcome match
            if (!actualOutcome.equalsIgnoreCase(outcome)) {
                log.warn("Outcome mismatch: expected '{}' → got '{}'", outcome, actualOutcome);
                takeMarketScreenshot(page, "mismatch-" + safeFileName(market + "-" + outcome));
                return false;
            }

            BettingTask freshTask = ModelConverter.convertFromArbOutcome(arbOutcomeService.findByExternalIdAndBookmaker(task.taskId(), task.bookmakerId()).orElse(null));
            if (freshTask != null) {
                log.info("fresh betting task from DB is not null");
                task = freshTask;

            }

//            double expectedOdds =arbOutcomeService.findByArbitrageAndBookmaker(Long.valueOf(task.taskId()), task.bookmakerId()).getOdds().doubleValue(); todo: cleanup
            double expectedOdds = task.expectedOdds();

            // Odds check
            if (!isOddsAcceptable(expectedOdds, actualOddsStr)) {
                log.warn("Odds drifted: expected ≥ {} → got {}", expectedOdds, actualOddsStr);
//                return false; // Fail on odds drift//todo: drifted
            }


            // Re-query cell for reliability
            Locator wrapper = page.locator("div.m-table__wrapper").nth(wrapperIdx);
            Locator outcomeCell = wrapper.locator("div.m-table-cell--responsive:not(.m-table-cell--disable)").nth(cellIdx);

            // Verify cell content before clicking
            String cellText = outcomeCell.locator("span.m-table-cell-item").first().textContent().trim();
            if (!cellText.equalsIgnoreCase(outcome)) {
                log.error("Cell content mismatch: expected '{}' → got '{}'", outcome, cellText);
                takeMarketScreenshot(page, "cell-mismatch-" + safeFileName(market + "-" + outcome));
                return false;
            }

            // Click with visual feedback
            outcomeCell.scrollIntoViewIfNeeded();
            try {
                outcomeCell.evaluate("el => el.style.border = '3px solid red'");
                randomHumanDelay(100, 200);
                outcomeCell.click(new Locator.ClickOptions().setForce(true).setTimeout(8000));
                outcomeCell.evaluate("el => el.style.border = ''");
            } catch (Exception e) {
                log.warn("Primary click failed, attempting fallback click");
                outcomeCell.evaluate("el => el.click()");
            }
            randomHumanDelay(200, 400);

            // Post-click verification (check if selection is reflected, e.g., in a bet slip)
            Locator betSlipOutcome = page.locator("div.bet-slip .outcome-name"); // Adjust selector based on actual bet slip
            if (betSlipOutcome.isVisible()) {
                String betSlipText = betSlipOutcome.textContent().trim();
                if (!betSlipText.contains(outcome)) {
                    log.error("Bet slip mismatch: expected '{}' → got '{}'", outcome, betSlipText);
                    takeMarketScreenshot(page, "betslip-mismatch-" + safeFileName(market + "-" + outcome));
                    return false;
                }
            }

            log.info("CLICKED: {} → {} @ {}", market, actualOutcome, actualOddsStr);
            if (!verifyBetSlip(page, task)){
                log.error("{} {} Bet slip verification failed", EMOJI_ERROR, EMOJI_BET);
                return false;
            }
            return true;

        } catch (Exception e) {
            log.error("Failed to select {} → {} | Error: {}", market, outcome, e.getMessage());
            takeMarketScreenshot(page, "error-" + safeFileName(market + "-" + outcome));
            return false;
        }
    }

    private static String safeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }



    /**
     * Checks if the displayed odds are acceptable within a symmetric tolerance percentage.
     *
     * Acceptance rules:
     * - Displayed odds must be within ±tolerance% of expected odds
     * - Examples with expected = 1.80 and tolerance = 5%:
     *   - Acceptable range: 1.71 to 1.89
     *   - 1.75 → accept
     *   - 1.90 → reject (too high)
     *   - 1.65 → reject (too low)
     *
     * @param expectedOdds       The target odds (e.g., 1.80)
     * @param displayedOddsStr   The odds string from the site (e.g., "1.75")
     * @return true if within tolerance, false otherwise
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
                        displayedOdds, percentDiff, expectedOdds, TOLERANCE_PERCENT);
            } else {
                String reason = displayedOdds < lowerBound ? "too low" : "too high";
                double percentDiff = ((displayedOdds - expectedOdds) / expectedOdds) * 100.0;
                log.debug("Displayed odds {} is {}% {} expected {} → REJECTED (±{}% tolerance)",
                        displayedOdds, Math.abs(percentDiff), reason, expectedOdds, TOLERANCE_PERCENT);
            }

            return isAcceptable;

        } catch (NumberFormatException e) {
            log.warn("Could not parse displayed odds string: '{}'", displayedOddsStr);
            return false; // Reject if can't parse
        }
    }




}
