package com.mouse.bet.util.sporty;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.mouse.bet.converter.ModelConverter;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.service.ArbOutcomeService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Slf4j
public class SportyBetPlacement {

    private static final double TOLERANCE_PERCENT = 0.05; // 5% tolerance - adjust as needed
    private static final long MAX_DURATION_MS = 10 * 60 * 1000L; // 10 minutes

    private enum BetslipStatus {
        ACCEPTABLE,
        SUSPENDED,
        UNAVAILABLE,
        ODDS_TOO_LOW,
        ODDS_TOO_HIGH;
    }

//    public static boolean placeBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
//        BigDecimal stake = BigDecimal.valueOf(task.stakeAmount());
//        String arbId = task.taskId();
//        BigDecimal expectedOdds = BigDecimal.valueOf(task.expectedOdds());
//        long startTime = System.currentTimeMillis();
//        final long deadline = startTime + MAX_DURATION_MS;
//
//        log.info("─────────────────────────────────────────────────────────────");
//        log.info("START placeBet() → {} → {} @ {} | Stake: {} | {}",
//                task.marketType(), task.outcome(), expectedOdds, stake,
//                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
//
//        try {
//            // ── 1. ENTER STAKE ─────────────────────────────────────
//            log.info("[1/6] Entering stake...");
//            Locator stakeInput = page.locator("#j_stake_0 input.m-input, .m-input[placeholder*='min']").first();
//            if (stakeInput.count() == 0) {
//                log.error("Stake input missing");
//                return false;
//            }BettingTask freshTask = getFreshTask(task, arbOutcomeService);
//            if (freshTask != null) {
//                log.info("Using fresh betting task from DB");
//                task = freshTask;
//            } else {
//                log.warn("Could not fetch fresh task, using current task");
//            }
//
//
//            stakeInput.fill(""); // Clear
//            SportyBetLoginUtil.typeFastHumanLike(stakeInput, String.valueOf(stake)); // Type stake directly
//            stakeInput.press("Enter");
//            log.info("[OK] Stake entered");
//
//            // ── 2. WAIT FOR BET IN SLIP ─────────────────────────────────
//            Locator betItemCheck = page.locator("div.m-item, .m-bet-item");
//            if (betItemCheck.count() == 0) {
//                betItemCheck.first().waitFor(new Locator.WaitForOptions().setTimeout(4000));
//                if (page.locator("div.m-item").count() == 0) {
//                    log.error("Bet never appeared in slip");
//                    return false;
//                }
//            }
//            log.info("[OK] Bet in slip");
//
//            // ── 3. UNKILLABLE LOOP + FULL POPUP HANDLING ─────────────────────
//            log.info("[3/6] Starting UNKILLABLE placement loop...");
//
//            boolean betConfirmed = false;
//            boolean permanentFailure = false;
//
//            while (!betConfirmed && !permanentFailure && System.currentTimeMillis() < deadline) {
//                long elapsedMs = System.currentTimeMillis() - startTime;
//                log.info("[Elapsed: {}s / {}s max] Checking state...", elapsedMs / 1000, MAX_DURATION_MS / 1000);
//
//                // ── A. CRITICAL ODDS NOT ACCEPTABLE POPUP ──
//                Locator oddsChangePopup = page.locator("div.m-dialog-wrapper p:has-text('Odds not acceptable')").first();
//                if (oddsChangePopup.isVisible(new Locator.IsVisibleOptions().setTimeout(500))) {
//                    log.error("ODDS NOT ACCEPTABLE POPUP → Permanent failure");
//                    permanentFailure = true;
//                    break;
//                }
//
//                BettingTask newFreshTask = getFreshTask(task, arbOutcomeService);
//                if (freshTask != null) {
//                    log.info("Using fresh betting task from DB");
//                    task = newFreshTask;
//                } else {
//                    log.warn("Could not fetch fresh task, using current task");
//                }
//
//                expectedOdds = BigDecimal.valueOf(Objects.requireNonNull(task).expectedOdds());
//
//                // ── B. MONITOR BETSLIP STATUS ──
//                BetslipStatus status = monitorAndHandleOddsInBetslip(page, expectedOdds);
//                switch (status) {
//                    case UNAVAILABLE:
//                        log.error("❌ MARKET UNAVAILABLE → Game over → ABORTING");
//                        permanentFailure = true;
//                        break;
//
//                    case ODDS_TOO_LOW:
//                        log.warn("⚠️ ODDS CURRENTLY TOO LOW → Waiting for odds to improve...");
//                        continue;
//                    case ODDS_TOO_HIGH:
//                        log.warn("⚠️ ODDS CURRENTLY TOO HIGH → Waiting for odds to sync...");
//                        continue;
//
//                    case SUSPENDED:
//                        log.warn("⏸️ Market suspended → Waiting for odds to return...");
//                        continue;
//                    case ACCEPTABLE:
//                        log.info("✅ Odds acceptable in betslip → Proceeding");
//                        break;
//                }
//                if (permanentFailure) break;
//
//                // ── C. FINAL CONFIRM POPUP ──
//                Locator finalConfirm = page.locator("xpath=//button[.//span[text()='Confirm' or text()='Yes' or text()='OK']]").first();
//                if (finalConfirm.isVisible(new Locator.IsVisibleOptions().setTimeout(800))) {
//                    log.warn("FINAL CONFIRM POPUP → Clicking 'Confirm'");
//
//                    if (arbOutcomeService.isActiveByExternalIdAndBookmaker(task.taskId(), task.bookmakerId())) {
//                        log.info("the arb is still active");
//                        finalConfirm.click(new Locator.ClickOptions()
//                                .setForce(true)
////                                .setNoWaitAfter(true)
//                                .setTimeout(1500));
//                    }
//
//                }
//
//                // ── D. MAIN BUTTON LOGIC (ONLY IF ODDS ACCEPTABLE) ──
//                if (status != BetslipStatus.ACCEPTABLE) {
//                    continue;
//                }
//
//                Locator btn = page.locator("button.af-button--primary >> visible=true").first();
//                if (btn.count() == 0) {
//                    log.info("Primary button gone → likely success");
//                    continue;
//                }
//
//                String text = btn.innerText().trim();
//                log.info("Main button: \"{}\" | Disabled: {}", text, btn.isDisabled());
//
//                // ── STEP 1: ALWAYS HANDLE "ACCEPT CHANGES" FIRST ──
//                if (text.matches(".*(Accept Changes|Accept|Confirm Changes).*")) {
//                    log.warn("→ Clicking 'Accept Changes'");
//                    btn.click(new Locator.ClickOptions().setForce(true)
//                            .setNoWaitAfter(false));
//                    randomHumanDelay(200, 500);
//
//                    stakeInput.fill("");
//                    SportyBetLoginUtil.typeFastHumanLike(stakeInput, String.valueOf(stake));
//                    stakeInput.press("Enter");
//
//                    continue; // Loop again to recheck odds and button state
//                }
//
//
//                // ── STEP 3: ONLY NOW CLICK "PLACE BET" ──
//                if (text.matches(".*(Place Bet|Bet Now|Confirm Bet|Place bet).*") && !btn.isDisabled()) {
//                    log.info("→ Clicking 'Place Bet'");
//                    btn.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(true));
//
//                    // Check rejection right after
//                    Locator oddsRejected = page.locator("div.m-dialog-wrapper p:has-text('Odds not acceptable')").first();
//                    if (oddsRejected.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
//                        log.error("ODDS NOT ACCEPTABLE after clicking Place Bet → FAILURE");
//                        permanentFailure = true;
//                        break;
//                    }
//                    continue;
//                }
//
//                if (text.contains("Place Bet") && btn.isDisabled()) {
//                    log.info("Place Bet disabled → waiting...");
//                    continue;
//                }
//
//                // ── E. SUCCESS DETECTION ──
//                boolean successDetected = page.locator("div.m-dialog-wrapper.m-dialog-suc").count() > 0 ||
//                        page.locator("text='Submission Successful'").count() > 0 ||
//                        page.locator("i.m-icon-suc").count() > 0 ||
//                        page.locator("div.booking-code").isVisible(new Locator.IsVisibleOptions().setTimeout(1000));
//
//                if (successDetected) {
//                    log.info("SUCCESS CONFIRMED — Official success modal detected!");
//                    try {
//                        String code = page.locator("div.booking-code").textContent().trim();
//                        log.info("BOOKING CODE: {}", code.isEmpty() ? "Hidden" : code);
//                    } catch (Exception ignored) {}
//                    betConfirmed = true;
//                    break;
//                }
//            }
//
//            // ── HANDLE PERMANENT FAILURE ──
//            if (permanentFailure) {
//                log.error("BET PLACEMENT FAILED → Unrecoverable state (unavailable / rejected)");
//                try {
//                    Locator closeBtn = page.locator("div.m-dialog-wrapper button:has-text('OK'), " +
//                            "div.m-dialog-wrapper img.close-icon[data-action='close']").first();
//                    if (closeBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
//                        closeBtn.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(true));
//                    }
//                } catch (Exception ignored) {}
//                log.info("placeBet() COMPLETED | FAILURE | {}ms", System.currentTimeMillis() - startTime);
//                log.info("─────────────────────────────────────────────────────────────");
//                return false;
//            }
//
//            if (!betConfirmed) {
//                log.error("FAILED → Timeout after {} minutes (odds never became acceptable)", MAX_DURATION_MS / 60000);
//                return false;
//            }
//
//            log.info("[OK] Bet placed successfully");
//
//            // ── FINAL SUCCESS VERIFICATION (kept exactly as original) ──
//            try {
//                page.waitForFunction("""
//                () => {
//                    return document.querySelector('div.m-dialog-wrapper.m-dialog-suc') ||
//                           document.querySelector('span[data-cms-key="submission_successful"]') ||
//                           document.querySelector('div.booking-code');
//                }
//                """, null, new Page.WaitForFunctionOptions().setTimeout(15000));
//                log.info("FINAL SUCCESS VERIFIED");
//            } catch (TimeoutError te) {
//                log.error("NO SUCCESS MODAL AFTER 15s → BET FAILED");
//                return false;
//            }
//
//            // ── CLOSE SUCCESS MODAL (kept exactly as original) ──
//            try {
//                Locator closeSuccess = page.locator("div.m-dialog-suc button:has-text('OK'), " +
//                        "div.m-dialog-suc i.m-icon-close, " +
//                        "div.m-dialog-suc [data-action='close']").first();
//                closeSuccess.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(true));
//                log.info("Success modal closed");
//            } catch (Exception ignored) {}
//
//            long duration = System.currentTimeMillis() - startTime;
//            log.info("placeBet() COMPLETED | SUCCESS | {}ms", duration);
//            log.info("─────────────────────────────────────────────────────────────");
//            return true;
//
//        } catch (Exception e) {
//            log.error("FATAL in placeBet(): {}", e.toString());
//            e.printStackTrace();
//            return false;
//        }
//    }



public static boolean placeBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
    BigDecimal stake = BigDecimal.valueOf(task.stakeAmount());
    String arbId = task.taskId();
    BigDecimal expectedOdds = BigDecimal.valueOf(task.expectedOdds());
    long startTime = System.currentTimeMillis();
    final long deadline = startTime + MAX_DURATION_MS;

    log.info("─────────────────────────────────────────────────────────────");
    log.info("START placeBet() → {} → {} @ {} | Stake: {} | {}",
            task.marketType(), task.outcome(), expectedOdds, stake,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));

    try {
        // ── 1. ENTER STAKE ─────────────────────────────────────
        log.info("[1/6] Entering stake...");

        // UPDATED: target the input directly inside #j_stake_0 > span.m-input-com
        // The actual element: <input class="m-input fs-exclude" placeholder="min. 10">
        Locator stakeInput = page.locator("#j_stake_0 span.m-input-com input.m-input").first();

        // Wait for it to be visible and enabled before touching it
        stakeInput.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(10000));

        if (stakeInput.count() == 0) {
            log.error("Stake input missing");
            return false;
        }

        BettingTask freshTask = getFreshTask(task, arbOutcomeService);
        if (freshTask != null) {
            log.info("Using fresh betting task from DB");
            task = freshTask;
            stake = BigDecimal.valueOf(task.stakeAmount());
            expectedOdds = BigDecimal.valueOf(task.expectedOdds());
        } else {
            log.warn("Could not fetch fresh task, using current task");
        }

        // FIXED: click to focus first, then fill, then use keyboard Tab/Enter
        // Avoids the 30s press() timeout caused by focus loss mid-action
        stakeInput.click(new Locator.ClickOptions().setTimeout(5000));
        randomHumanDelay(100, 200);
        stakeInput.fill("");
        SportyBetLoginUtil.typeFastHumanLike(stakeInput, String.valueOf(stake));
        randomHumanDelay(100, 200);

        // Use Tab to commit the value instead of Enter (safer — Enter can submit the form prematurely)
        stakeInput.press("Tab", new Locator.PressOptions().setTimeout(5000));
        log.info("[OK] Stake entered: {}", stake);

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

            // ── A. CHECK FOR SUCCESS FIRST ──
            boolean successDetected = page.locator("div.m-dialog-wrapper.m-dialog-suc").count() > 0 ||
                    page.locator("text='Submission Successful'").count() > 0 ||
                    page.locator("i.m-icon-suc").count() > 0 ||
                    page.locator("div.booking-code").isVisible(new Locator.IsVisibleOptions().setTimeout(500));

            if (successDetected) {
                log.info("SUCCESS CONFIRMED — Official success modal detected!");
                try {
                    String code = page.locator("div.booking-code").textContent().trim();
                    log.info("BOOKING CODE: {}", code.isEmpty() ? "Hidden" : code);
                } catch (Exception ignored) {}
                betConfirmed = true;
                break;
            }

            // ── B. ODDS NOT ACCEPTABLE POPUP ──
            Locator oddsChangePopup = page.locator("div.m-dialog-wrapper p:has-text('Odds not acceptable')").first();
            if (oddsChangePopup.isVisible(new Locator.IsVisibleOptions().setTimeout(500))) {
                log.error("ODDS NOT ACCEPTABLE POPUP → Permanent failure");
                permanentFailure = true;
                break;
            }

            // ── C. REFRESH TASK FROM DB ──
            BettingTask newFreshTask = getFreshTask(task, arbOutcomeService);
            if (newFreshTask != null) {
                log.info("Using fresh betting task from DB");
                task = newFreshTask;
                stake = BigDecimal.valueOf(task.stakeAmount());
                expectedOdds = BigDecimal.valueOf(task.expectedOdds());
            } else {
                log.warn("Could not fetch fresh task, using current task");
            }

            // ── D. MONITOR BETSLIP STATUS ──
            BetslipStatus status = monitorAndHandleOddsInBetslip(page, expectedOdds);
            switch (status) {
                case UNAVAILABLE:
                    log.error("❌ MARKET UNAVAILABLE → ABORTING");
                    permanentFailure = true;
                    break;
                case ODDS_TOO_LOW:
                    log.warn("⚠️ ODDS TOO LOW → Waiting...");
                    continue;
                case ODDS_TOO_HIGH:
                    log.warn("⚠️ ODDS TOO HIGH → Waiting...");
                    continue;
                case SUSPENDED:
                    log.warn("⏸️ Market suspended → Waiting...");
                    continue;
                case ACCEPTABLE:
                    log.info("✅ Odds acceptable → Proceeding");
                    break;
            }
            if (permanentFailure) break;

            // ── E. HANDLE CONFIRM POPUP ──
            Locator finalConfirm = page.locator("xpath=//button[.//span[text()='Confirm' or text()='Yes' or text()='OK']]").first();
            if (finalConfirm.isVisible(new Locator.IsVisibleOptions().setTimeout(800))) {
                log.warn("FINAL CONFIRM POPUP → Clicking 'Confirm'");

                if (arbOutcomeService.isActiveByExternalIdAndBookmaker(task.taskId(), task.bookmakerId())) {
                    log.info("Arb is still active");
                    finalConfirm.click(new Locator.ClickOptions().setForce(true).setTimeout(1500));
                    randomHumanDelay(800, 1500);

                    Locator acceptChangesAfterConfirm = page.locator("button.af-button--primary >> visible=true").first();
                    if (acceptChangesAfterConfirm.count() > 0) {
                        String btnText = acceptChangesAfterConfirm.innerText().trim();
                        if (btnText.matches(".*(Accept Changes|Accept|Confirm Changes).*")) {
                            log.warn("→ 'Accept Changes' after Confirm! Clicking...");
                            acceptChangesAfterConfirm.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(false));
                            randomHumanDelay(200, 500);

                            // Re-enter stake — reuse same safe pattern
                            stakeInput.click(new Locator.ClickOptions().setTimeout(5000));
                            randomHumanDelay(100, 200);
                            stakeInput.fill("");
                            SportyBetLoginUtil.typeFastHumanLike(stakeInput, String.valueOf(stake));
                            randomHumanDelay(100, 200);
                            stakeInput.press("Tab", new Locator.PressOptions().setTimeout(5000));
                        }
                    }
                    continue;
                } else {
                    log.error("Arb no longer active → Aborting");
                    permanentFailure = true;
                    break;
                }
            }

            // ── F. MAIN BUTTON LOGIC ──
            if (status != BetslipStatus.ACCEPTABLE) {
                randomHumanDelay(1000, 2000);
                continue;
            }

            Locator btn = page.locator("button.af-button--primary >> visible=true").first();
            if (btn.count() == 0) {
                log.info("Primary button gone → checking for success...");
                randomHumanDelay(500, 1000);
                continue;
            }

            String text = btn.innerText().trim();
            log.info("Main button: \"{}\" | Disabled: {}", text, btn.isDisabled());

            if (text.matches(".*(Accept Changes|Accept|Confirm Changes).*")) {
                log.warn("→ Clicking 'Accept Changes'");
                btn.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(false));
                randomHumanDelay(200, 500);

                // Re-enter stake safely
                stakeInput.click(new Locator.ClickOptions().setTimeout(5000));
                randomHumanDelay(100, 200);
                stakeInput.fill("");
                SportyBetLoginUtil.typeFastHumanLike(stakeInput, String.valueOf(stake));
                randomHumanDelay(100, 200);
                stakeInput.press("Tab", new Locator.PressOptions().setTimeout(5000));
                continue;
            }

            if (text.matches(".*(Place Bet|Bet Now|Confirm Bet|Place bet).*") && !btn.isDisabled()) {
                log.info("→ Clicking 'Place Bet'");
                btn.click(new Locator.ClickOptions().setForce(true).setNoWaitAfter(true));
                randomHumanDelay(500, 1000);
                continue;
            }

            if (text.contains("Place Bet") && btn.isDisabled()) {
                log.info("Place Bet disabled → waiting...");
                randomHumanDelay(1000, 2000);
                continue;
            }

            log.warn("Unknown button state: \"{}\" → waiting...", text);
            randomHumanDelay(1000, 2000);
        }

        // ── HANDLE PERMANENT FAILURE ──
        if (permanentFailure) {
            log.error("BET PLACEMENT FAILED → Unrecoverable state");
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
            log.error("FAILED → Timeout after {} minutes", MAX_DURATION_MS / 60000);
            return false;
        }

        log.info("[OK] Bet placed successfully");

        // ── FINAL SUCCESS VERIFICATION ──
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

        // ── CLOSE SUCCESS MODAL ──
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

    private static void randomHumanDelay(int minMs, int maxMs) {
        try {
            int delay = minMs + (int) (Math.random() * (maxMs - minMs));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get fresh task from database for latest odds
     */
    private static BettingTask getFreshTask(BettingTask currentTask, ArbOutcomeService arbOutcomeService) {
        try {
            if (arbOutcomeService == null) {
                log.warn("ArbOutcomeService is null, cannot fetch fresh task");
                return null;
            }

            BettingTask task = ModelConverter.convertFromArbOutcome(
                    arbOutcomeService.findByExternalIdAndBookmaker(
                            currentTask.taskId(),
                            currentTask.bookmakerId()
                    ).orElse(null)
            );

            log.info("debug tsk: {}", task);
            return task;
        } catch (Exception e) {
            log.warn("Could not fetch fresh task from DB: {}", e.getMessage());
            return null;
        }
    }
}
