package com.mouse.bet.util.msport;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.mouse.bet.checker.ArbChecker;
import com.mouse.bet.converter.ModelConverter;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.orchestrator.model.BetLeg;
import com.mouse.bet.service.ArbOutcomeService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import static com.mouse.bet.util.msport.MSportMarketUtil.withLocatorRetry;
import static com.mouse.bet.util.msport.MSportNavigationUtil.randomHumanDelay;


/**
 * Handles bet placement operations for MSport platform
 * Thread-safe implementation with comprehensive error handling
 */
@Slf4j
public class MSportBetPlacement {

    // Configuration constants
    private static final long MAX_DURATION_MS = 10 * 60 * 1000L; // 10 minutes
    private static final long MAX_WAIT_FOR_RECOVERY_MS = 30_000; // 30 seconds
    private static final int MAX_STAKE_ATTEMPTS = 3;
    private static final double TOLERANCE_PERCENT = 0.05; // 5% tolerance for odds

    // Emojis for logging
    private static final String EMOJI_BET = "🎯";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_TRASH = "🗑️";

    // Prevent instantiation
    private MSportBetPlacement() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Main method to place a bet with time-based retry logic
     */
    public static boolean placeBet(Page page,
                                   BettingTask bettingTask,
                                   ArbOutcomeService arbOutcomeService,
                                   ArbChecker arbChecker) {
        long startTime = System.currentTimeMillis();
        final long deadline = startTime + MAX_DURATION_MS;

        logPlacementStart(bettingTask);

        try {
            // Step 1: Enter initial stake
//            if (!enterInitialStake(page, bettingTask, arbOutcomeService)) {
//                return false;
//            }

             //Step 2: Wait for bet in slip
            if (!waitForBetInSlip(page)) {
                return false;
            }

            // Step 3: Disable auto-accept odds changes
            disableAcceptOddsChanges(page);

            // Step 4: Main placement loop
            boolean success = executePlacementLoop(page, bettingTask, arbOutcomeService, arbChecker, startTime, deadline);

            if (!success) {
                handlePlacementTimeout(page);
                return false;
            }

            logPlacementSuccess(page,startTime);
//            return detectSuccessModal(page);
            return true;

        } catch (Exception e) {
            handlePlacementError(page, e);
            return false;
        }
    }



    /**
     * Wait for bet to appear in betslip
     */
    private static boolean waitForBetInSlip(Page page) {
        try {
            log.info("[2/5] Waiting for bet in slip...");
            page.locator("#target-betslip .m-selections-list .m-bet-selection")
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(8000));
            log.info("[OK] Bet appeared in slip");
            return true;
        } catch (Exception e) {
            log.error("{} Bet did not appear in slip: {}", EMOJI_ERROR, e.getMessage());
            return false;
        }
    }

    /* ===================== MAIN PLACEMENT LOOP ===================== */

    /**
     * Execute the main placement loop with state monitoring
     */
    private static boolean executePlacementLoop(Page page, BettingTask bettingTask,
                                                ArbOutcomeService arbOutcomeService,
                                                ArbChecker arbChecker,
                                                long startTime, long deadline) {
        log.info("[4/5] Starting optimized placement loop...");

        boolean success = false;
        long waitStartTime = 0;
        String jsMonitor = getStateMonitorScript();

        while (!success && System.currentTimeMillis() < deadline) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            logLoopProgress(elapsedMs);

            @SuppressWarnings("unchecked")
            Map<String, Object> state = (Map<String, Object>) page.evaluate(jsMonitor);

            String status = (String) state.getOrDefault("status", "NO_SELECTION");

            // Handle critical states
            if ("NO_SELECTION".equals(status)) {
                log.warn("Bet selection disappeared from slip");
                success = true;
            }

            if ("UNAVAILABLE".equals(status)) {
                log.error("Market UNAVAILABLE - match likely over");
                clearBetSlip(page);
                return false;
            }

            // Handle suspended market
            if ("SUSPENDED".equals(status)) {
                waitStartTime = handleSuspendedMarket(page, waitStartTime);
                if (waitStartTime == -1) return false;
                continue;
            }

            // Get current state
            String currentOddsText = (String) state.get("oddsText");
            String buttonText = (String) state.getOrDefault("buttonText", "");
            boolean buttonDisabled = Boolean.TRUE.equals(state.get("buttonDisabled"));

            // ── Push live slip odds into ArbChecker ────────────────────────
            // This feeds Bet9ja's side of the arb on every tick.
            // ArbChecker immediately recalculates isArbValid() + stakes so
            // the other bookie's window sees the updated result on its next poll.
            if (currentOddsText != null) {
                try {
                    BigDecimal liveOdds = new BigDecimal(currentOddsText.trim());
                    arbChecker.updateOdds(bettingTask.bookmaker(), liveOdds);
                } catch (NumberFormatException e) {
                    log.warn("[ArbChecker] Could not parse slip odds '{}' — skipping update", currentOddsText);
                }
            }

            BettingTask freshTask = getFreshTask(bettingTask, arbOutcomeService);
            if (freshTask != null) {
                log.info("Using fresh betting task from DB");
                bettingTask = freshTask;
            } else {
                log.warn("Could not fetch fresh task, using current task");
            }

            double expectedOdds = bettingTask.expectedOdds();

            logCurrentState(state, expectedOdds);

            // Handle unfavorable odds
            if (currentOddsText == null || !isOddsAcceptable(expectedOdds, currentOddsText)) {
                waitStartTime = handleUnfavorableOdds(page, currentOddsText, expectedOdds, waitStartTime);
                if (waitStartTime == -1) return false;
                continue;
            }

            // Reset wait timer if odds recovered
            if (waitStartTime > 0) {
                log.info("✓ Odds RECOVERED → {} ✓", currentOddsText);
                waitStartTime = 0;
            }

            // Handle disabled button
            if (buttonDisabled) {
                log.info("Place bet button disabled → waiting...");
                randomHumanDelay(200, 500);
                continue;
            }

            // Handle button actions
            String btnLower = buttonText.toLowerCase();

            // Step 1: Handle "Accept Changes"
            if (btnLower.contains("accept changes")) {
                handleAcceptChanges(page, currentOddsText);
                continue;
            }

            ArbChecker.ArbResult arbResult = arbChecker.getResult();

            // Step 2: Re-enter stake before final placement
            if (btnLower.contains("place bet") || btnLower.contains("place") || btnLower.contains("submit")) {
                if (!reEnterStakeBeforePlacement(page, arbResult.getStake(bettingTask.bookmaker()))) {
                    continue;
                }

                // Step 3: Click place bet
                if (arbResult.isArbValid()) {
                    log.error("Arb is valide");
                    handlePlaceBet(page, buttonText, currentOddsText);

                    continue;

                }else {
                    log.info("❌ Arb no longer valid for {}", bettingTask.bookmaker());
                }

            }

            // Unknown state
            log.warn("Unknown button text: '{}' - waiting...", buttonText);
            randomHumanDelay(400, 700);
        }

        return success;
    }

    /* ===================== STATE HANDLERS ===================== */

    /**
     * Handle suspended market state
     */
    private static long handleSuspendedMarket(Page page, long waitStartTime) {
        if (waitStartTime == 0) {
            waitStartTime = System.currentTimeMillis();
            log.warn("Market SUSPENDED - waiting for recovery...");
        }

        long waited = System.currentTimeMillis() - waitStartTime;
        if (waited > MAX_WAIT_FOR_RECOVERY_MS) {
            log.error("Suspended too long ({}ms) - aborting", waited);
            clearBetSlip(page);
            return -1;
        }

        log.info("Suspended... waiting ({}ms / {}ms)", waited, MAX_WAIT_FOR_RECOVERY_MS);
        randomHumanDelay(500, 900);
        return waitStartTime;
    }

    /**
     * Handle unfavorable odds state
     */
    private static long handleUnfavorableOdds(Page page, String currentOddsText,
                                              double expectedOdds, long waitStartTime) {
        if (waitStartTime == 0) {
            waitStartTime = System.currentTimeMillis();
            log.warn("Odds UNFAVORABLE: {} (need ≥ {}) - waiting...", currentOddsText, expectedOdds);
        }

        long waited = System.currentTimeMillis() - waitStartTime;
        if (waited > MAX_WAIT_FOR_RECOVERY_MS) {
            log.error("Odds not recovering after {}ms - aborting", waited);
            clearBetSlip(page);
            return -1;
        }

        randomHumanDelay(200, 500);
        return waitStartTime;
    }

    /* ===================== BUTTON ACTIONS ===================== */

    /**
     * Handle "Accept Changes" button click
     */
    private static void handleAcceptChanges(Page page, String currentOddsText) {
        log.info("→ CLICKING 'Accept Changes' @ {}", currentOddsText);
        clickPlaceButton(page);
        randomHumanDelay(200, 500);
        disableAcceptOddsChanges(page);
    }

    /**
     * Re-enter stake before final placement
     */
    private static boolean reEnterStakeBeforePlacement(Page page, BigDecimal stake) {
        if (!enterStakeUsingJS(page, stake)) {
            log.warn("Failed to re-enter stake before Place Bet → will retry");
            randomHumanDelay(500, 800);
            return false;
        }
        randomHumanDelay(200, 400);
        return true;
    }

    /**
     * Handle "Place Bet" button click
     */
    private static void handlePlaceBet(Page page, String buttonText, String currentOddsText) {
        log.info("→ CLICKING '{}' button @ {} ✓", buttonText, currentOddsText);
        clickPlaceButton(page);
        randomHumanDelay(200, 500);
    }

    /* ===================== STAKE MANAGEMENT ===================== */

    /**
     * Enter stake with overflow handling and multiple retry strategies
     */
//
    /**
     * JavaScript method to find and input stake in the betslip
     * Add this method to the MSportBetPlacement class
     */

    /**
     * Enter stake using JavaScript evaluation for direct DOM manipulation
     * This method locates the stake input and enters the value directly
     */
    private static boolean enterStakeUsingJS(Page page, BigDecimal stakeAmount) {
        String stakeString = stakeAmount.toPlainString();

        try {
            log.info("Entering stake using JS method: {}", stakeString);

            // JavaScript to find and input stake
            String jsInputStake = """
            (stakeValue) => {
                // Strategy 1: Try Singles section input in m-mutiple-edit
                let input = document.querySelector('div.m-mutiple-edit div.v-input-wrap.bet-input input[type="text"][placeholder="min. 10"]');
                
                // Strategy 2: Try input in m-bet-selection (individual bet)
                if (!input) {
                    input = document.querySelector('div.m-bet-selection div.m-single-input-wrap div.v-input-wrap.bet-input input[type="text"][placeholder="min. 10"]');
                }
                
                // Strategy 3: Try any input with placeholder "min. 10" in betslip
                if (!input) {
                    input = document.querySelector('#target-betslip input[placeholder="min. 10"]');
                }
                
                // Strategy 4: Try any bet-input class input
                if (!input) {
                    input = document.querySelector('div.bet-input input[type="text"]');
                }
                
                if (!input) {
                    return { success: false, message: 'Stake input not found' };
                }
                
                // Scroll input into view
                input.scrollIntoView({ behavior: 'instant', block: 'center' });
                
                // Focus the input
                input.focus();
                
                // Clear existing value
                input.value = '';
                
                // Set new value
                input.value = stakeValue;
                
                // Trigger input events
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                
                // Trigger blur to ensure value is registered
                input.blur();
                
                return { 
                    success: true, 
                    enteredValue: input.value,
                    message: 'Stake entered successfully'
                };
            }
            """;

            // Execute the JavaScript
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsInputStake, stakeString);

            boolean success = Boolean.TRUE.equals(result.get("success"));
            String message = (String) result.get("message");
            String enteredValue = (String) result.get("enteredValue");

            if (success) {
                log.info("Stake entered via JS: {} ({})", enteredValue, message);

                // Verify the value was actually entered
                if (stakeString.equals(enteredValue)) {
                    page.waitForTimeout(300);
                    return true;
                } else {
                    log.warn("Stake mismatch. Expected: {}, Got: {}", stakeString, enteredValue);
                    return false;
                }
            } else {
                log.error("Failed to enter stake via JS: {}", message);
                return false;
            }

        } catch (Exception e) {
            log.error("Error entering stake via JS: {}", e.getMessage());
            return false;
        }
    }


    /**
     * Clear the betslip
     */
    public static boolean clearBetSlip(Page page) {
        try {
            Locator betslipContainer = withLocatorRetry(page, "#target-betslip .m-betslip",
                    loc -> loc.count() > 0 ? loc : null, 3, 5000, 1000);

            if (betslipContainer == null) {
                log.info("Betslip container not found");
                return true;
            }

            String betCountText = getBetslipCount(page);

            boolean wasEmpty = "0".equals(betCountText) || betCountText.isEmpty();
            if (wasEmpty) {
                log.info("{} Betslip already empty", EMOJI_SUCCESS);
                return true;
            }

            log.info("{} Clearing {} selection(s)...", EMOJI_TRASH, betCountText);

            clearBetslipSelections(page);
            page.waitForTimeout(800);

            String finalCount = getBetslipCount(page);
            boolean isCleared = "0".equals(finalCount) || finalCount.isEmpty();

            if (isCleared) {
                log.info("{} Betslip cleared successfully", EMOJI_SUCCESS);
            } else {
                log.warn("{} Betslip may not be fully cleared. Remaining: {}", EMOJI_WARNING, finalCount);
            }

            return isCleared;

        } catch (Exception e) {
            log.error("{} Error in clearBetSlip: {}", EMOJI_ERROR, e.getMessage());
            return false;
        }
    }

    /**
     * Get betslip count
     */
    private static String getBetslipCount(Page page) {
        return page.evaluate("""
            () => {
                const badge = document.querySelector('#target-betslip .m-count-ball');
                return badge ? badge.textContent.trim() : '0';
            }
            """).toString();
    }

    /**
     * Clear all betslip selections
     */
    private static void clearBetslipSelections(Page page) {
        page.evaluate("""
            () => {
                const closeButtons = document.querySelectorAll('#target-betslip .m-bet-selection .m-close-btn');
                closeButtons.forEach(btn => btn.click());
            }
            """);
    }

    /**
     * Disable auto-accept odds changes
     */
    public static void disableAcceptOddsChanges(Page page) {
        try {
            log.info("[3/5] Disabling auto-accept odds changes...");

            page.evaluate("""
                () => {
                    const checkbox = document.querySelector('input[type="checkbox"].m-auto-accept-checkbox');
                    if (checkbox && checkbox.checked) {
                        checkbox.click();
                    }
                }
                """);

            log.info("Auto-accept odds changes disabled");
        } catch (Exception e) {
            log.debug("Could not disable auto-accept: {}", e.getMessage());
        }
    }

    /**
     * Close success modal if present
     */
    public static void closeSuccessModal(Page page) {
        try {
            page.evaluate("""
                () => {
                    const modal = document.querySelector('.m-success-modal, .bet-success');
                    if (modal) {
                        const closeBtn = modal.querySelector('.close-btn, .m-close-btn');
                        if (closeBtn) closeBtn.click();
                    }
                }
                """);
        } catch (Exception e) {
            log.debug("Could not close success modal: {}", e.getMessage());
        }
    }

    /**
     * Handle success modal
     */
    public static void handleSuccessModal(Page page) {
        try {
            randomHumanDelay(1000, 1500);
            closeSuccessModal(page);
            randomHumanDelay(500, 800);
        } catch (Exception e) {
            log.debug("Could not handle success modal: {}", e.getMessage());
        }
    }

    /* ===================== JAVASCRIPT MONITORING ===================== */

    /**
     * Get the JavaScript state monitoring script
     */
    private static String getStateMonitorScript() {
        return """
            () => {
                const selection = document.querySelector('.m-selections-list .m-bet-selection');
                if (!selection) {
                    return { status: 'NO_SELECTION' };
                }
                if (selection.classList.contains('unavailable')) {
                    return { status: 'UNAVAILABLE' };
                }
                if (selection.classList.contains('abnormal')) {
                    return { status: 'SUSPENDED' };
                }
                const errorText = selection.querySelector('.m-error-text, p.tw-text-red');
                if (errorText && errorText.textContent.trim() && errorText.style.display !== 'none') {
                    return { status: 'ERROR', errorMessage: errorText.textContent.trim() };
                }
                
                const oddsCandidates = [
                    '.m-betslip-odds span:last-child',
                    '.m-betslip-odds span:not(.m-icon-trangle)',
                    '.m-odds span',
                    '.m-stake-info--right span.m-betslip-odds span'
                ];
                let oddsText = null;
                for (const selector of oddsCandidates) {
                    const el = selection.querySelector(selector);
                    if (el && el.textContent.trim()) {
                        oddsText = el.textContent.trim();
                        break;
                    }
                }
                
                const placeBtn = document.querySelector('button.m-place-btn, button.v-button.m-place-btn');
                const btnText = placeBtn ? placeBtn.textContent.trim() : '';
                const btnDisabled = placeBtn ? placeBtn.disabled : true;
                
                const successModal = document.querySelector('.m-success-modal, .bet-success, .ui-dialog--wrap[style*="display: block"], .ui-dialog--wrap[style*="flex"]');
                const successVisible = !!(successModal && successModal.offsetParent !== null);
                const rejectPopup = document.querySelector('.odds-reject-popup, .m-odds-change-popup, .ui-dialog--wrap[style*="display: block"] .ui-dialog');
                const rejectVisible = !!(rejectPopup && rejectPopup.offsetParent !== null);
                
                const marketTitle = selection.querySelector('.market-title')?.textContent.trim() || null;
                const teams = selection.querySelector('.m-teams')?.textContent.trim().replace(/\\s+/g, ' ') || null;
                const toReturn = selection.querySelector('.m-to-return')?.textContent.trim() || null;
                
                return {
                    status: 'OK',
                    oddsText,
                    buttonText: btnText,
                    buttonDisabled: btnDisabled,
                    successVisible,
                    rejectVisible,
                    marketTitle,
                    teams,
                    toReturn,
                    hasError: false
                };
            }
            """;
    }

    /**
     * Click the place button using JavaScript
     */
    private static void clickPlaceButton(Page page) {
        page.evaluate("""
            () => {
                const btn = document.querySelector('button.m-place-btn, button.v-button.m-place-btn');
                if (btn) {
                    btn.scrollIntoView({ block: 'center', behavior: 'smooth' });
                    btn.click();
                }
            }
            """);
    }

    /* ===================== VALIDATION ===================== */

    /**
     * Check if odds are acceptable within tolerance
     */
    private static boolean isOddsAcceptable(double expectedOdds, String displayedOddsStr) {
//        if (displayedOddsStr == null || displayedOddsStr.trim().isEmpty()) {
//            return false;
//        }
//
//        try {
//            double displayedOdds = Double.parseDouble(displayedOddsStr.trim());
//
//            if (expectedOdds <= 0) {
//                return false;
//            }
//
//            double lowerBound = expectedOdds * (1 - TOLERANCE_PERCENT);
//            double upperBound = expectedOdds * (1 + TOLERANCE_PERCENT);
//
//            return displayedOdds >= lowerBound && displayedOdds <= upperBound;
//
//        } catch (NumberFormatException e) {
//            log.warn("Could not parse odds: '{}'", displayedOddsStr);
//            return false;
//        }

        return true;

    }

    private static boolean detectSuccessModal(Page page) {
        try {
            Locator successModal = page.locator("div.m-betslip-success");

            successModal.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));

            log.info("✅ SUCCESS MODAL DETECTED!");

            // Extract booking code
            try {
                String bookingCode = page.locator(
                        "div.m-info-item:has-text('Booking Code') span.tw-text-black"
                ).first().textContent().trim();

                log.info("📋 Booking Code: {}", bookingCode);
                return true;
            } catch (Exception e) {
                log.warn("Could not extract booking code");
                return false;
            }

        } catch (Exception e) {
            log.debug("Success modal not detected: {}", e.getMessage());
            return false;
        }
    }




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


    /**
     * Log placement start
     */
    private static void logPlacementStart(BettingTask bettingTask) {
        log.info("─────────────────────────────────────────────────────────────");
        log.info("START placeBet → {} → {} | Stake: {} | {}",
                bettingTask.marketType(),
                bettingTask.outcome(),
                bettingTask.stakeAmount(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
    }

    /**
     * Log loop progress
     */
    private static void logLoopProgress(long elapsedMs) {
        log.info("[Elapsed: {}s / {}s max] Checking state...",
                elapsedMs / 1000, MAX_DURATION_MS / 1000);
    }

    /**
     * Log current state
     */
    private static void logCurrentState(Map<String, Object> state, double expectedOdds) {
        String buttonText = (String) state.getOrDefault("buttonText", "");
        boolean buttonDisabled = Boolean.TRUE.equals(state.get("buttonDisabled"));
        String currentOddsText = (String) state.get("oddsText");

        log.info("Button: \"{}\" | Disabled: {} | Current-Odds: {} | expected-Odds: {} |",
                buttonText, buttonDisabled, currentOddsText, expectedOdds);
    }

    /**
     * Log placement success
     */
    private static void logPlacementSuccess(Page page, long startTime) {

        if (detectSuccessModal(page)) {
            long duration = System.currentTimeMillis() - startTime;
            log.info("{} BET PLACED SUCCESSFULLY! | {}ms", EMOJI_SUCCESS, duration);
            log.info("────────────────────────────────────────────────────────────");
            handleSuccessModal(page);

        }else {
            log.info("");
        }


    }

    /**
     * Handle placement timeout
     */
    private static void handlePlacementTimeout(Page page) {
        log.error("{} FAILED to place bet → Timeout after {} minutes",
                EMOJI_ERROR, MAX_DURATION_MS / 60000);
        clearBetSlip(page);
    }

    /**
     * Handle placement error
     */
    private static void handlePlacementError(Page page, Exception e) {
        log.error("{} FATAL ERROR in placeBet(): {}", EMOJI_ERROR, e.toString());
        e.printStackTrace();
        closeSuccessModal(page);
    }

    /* ===================== ENUMS ===================== */

    /**
     * Bet state enum
     */
    private enum BetState {
        SELECTION_LOST,
        MARKET_UNAVAILABLE,
        MARKET_SUSPENDED,
        ODDS_UNFAVORABLE,
        ODDS_RECOVERED,
        BUTTON_DISABLED,
        READY_TO_PLACE,
        ERROR
    }

    /**
     * Button action enum
     */
    private enum ButtonAction {
        ACCEPT_CHANGES,
        PLACE_BET,
        UNKNOWN
    }
}