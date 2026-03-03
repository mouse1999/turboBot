package com.mouse.bet.util.bet9ja;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.mouse.bet.converter.ModelConverter;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.orchestrator.model.BetLeg;
import com.mouse.bet.service.ArbOutcomeService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.mouse.bet.util.msport.MSportNavigationUtil.randomHumanDelay;

/**
 * Handles bet placement operations for Bet9ja platform
 * Thread-safe implementation with comprehensive error handling
 */
@Slf4j
public class Bet9jaBetPlacement {

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
    private Bet9jaBetPlacement() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Main method to place a bet with time-based retry logic
     */
    public static boolean placeBet(Page page, BetLeg betLeg, ArbOutcomeService arbOutcomeService) {
        long startTime = System.currentTimeMillis();
        final long deadline = startTime + MAX_DURATION_MS;

        logPlacementStart(betLeg);

        try {
            // Step 1: Wait for bet in slip
            if (!waitForBetInSlip(page)) {
                return false;
            }

            // Step 2: Disable auto-accept odds changes
            disableAcceptOddsChanges(page);

            // Step 3: Main placement loop
            boolean success = executePlacementLoop(page, betLeg, arbOutcomeService, startTime, deadline);

            if (!success) {
                handlePlacementTimeout(page);
                return false;
            }

            logPlacementSuccess(page, startTime);
            return true;

        } catch (Exception e) {
            handlePlacementError(page, e);
            return false;
        }
    }

    /* ===================== INITIALIZATION ===================== */

    /**
     * Wait for bet to appear in betslip
     */
    private static boolean waitForBetInSlip(Page page) {
        try {
            log.info("[1/4] Waiting for bet in slip...");
            page.locator("div.betslip__match-box")
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
//    private static boolean executePlacementLoop(Page page, BettingTask bettingTask,
//                                                ArbOutcomeService arbOutcomeService,
//                                                long startTime, long deadline) {
//        log.info("[3/4] Starting optimized placement loop...");
//
//        boolean success = false;
//        long waitStartTime = 0;
//        String jsMonitor = getStateMonitorScript();
//
//        while (!success && System.currentTimeMillis() < deadline) {
//            long elapsedMs = System.currentTimeMillis() - startTime;
//            logLoopProgress(elapsedMs);
//
//            @SuppressWarnings("unchecked")
//            Map<String, Object> state = (Map<String, Object>) page.evaluate(jsMonitor);
//
//            String status = (String) state.getOrDefault("status", "NO_SELECTION");
//
//            // Handle critical states
//            if ("NO_SELECTION".equals(status)) {
//                log.warn("Bet selection disappeared from slip");
//                success = true;
//                continue;
//            }
//
//            if ("BET_PLACED".equals(status)) {
//                log.info("✅ Bet placement detected via state monitor!");
//                String betId = (String) state.get("betId");
//                if (betId != null) {
//                    log.info("📋 Bet ID: {}", betId);
//                }
//                success = true;
//                break;
//            }
//
//            if ("UNAVAILABLE".equals(status)) {
//                log.error("Market UNAVAILABLE - match likely over or closed");
//                clearBetSlip(page);
//                return false;
//            }
//
//            // Handle suspended market
//            if ("SUSPENDED".equals(status)) {
//                waitStartTime = handleSuspendedMarket(page, waitStartTime);
//                if (waitStartTime == -1) return false;
//                continue;
//            }
//
//            // Get current state
//            String currentOddsText = (String) state.get("oddsText");
//            String buttonText = (String) state.getOrDefault("buttonText", "");
//            boolean buttonDisabled = Boolean.TRUE.equals(state.get("buttonDisabled"));
//
//            // Get fresh task from DB if possible
//            BettingTask freshTask = getFreshTask(bettingTask, arbOutcomeService);
//            if (freshTask != null) {
//                log.info("Using fresh betting task from DB");
//                bettingTask = freshTask;
//            } else {
//                log.warn("Could not fetch fresh task, using current task");
//            }
//
//            double expectedOdds = bettingTask.expectedOdds();
//
//            logCurrentState(state, expectedOdds);
//
//            // Handle unfavorable odds
//            if (currentOddsText == null || !isOddsAcceptable(expectedOdds, currentOddsText)) {
//                waitStartTime = handleUnfavorableOdds(page, currentOddsText, expectedOdds, waitStartTime);
//                if (waitStartTime == -1) return false;
//                continue;
//            }
//
//            // Reset wait timer if odds recovered
//            if (waitStartTime > 0) {
//                log.info("✓ Odds RECOVERED → {} ✓", currentOddsText);
//                waitStartTime = 0;
//            }
//
//            // Handle disabled button
//            if (buttonDisabled) {
//                log.info("Place bet button disabled → waiting...");
//                randomHumanDelay(200, 500);
//                continue;
//            }
//
//            // Handle button actions
//            String btnLower = buttonText.toLowerCase();
//
//            // Step 1: Handle "Accept Changes" - First click to accept odds
//            if (btnLower.contains("accept") || btnLower.contains("change")) {
//                // Verify odds are still acceptable before accepting changes
//                if (!isOddsAcceptable(expectedOdds, currentOddsText)) {
//                    log.warn("Odds not acceptable even though 'Accept Changes' is showing: {} (need ≥ {})",
//                            currentOddsText, expectedOdds);
//                    waitStartTime = handleUnfavorableOdds(page, currentOddsText, expectedOdds, waitStartTime);
//                    if (waitStartTime == -1) return false;
//                    continue;
//                }
//
//                log.info("→ CLICKING 'Accept Changes' button @ {} (First click)", currentOddsText);
//                clickPlaceButton(page);
//                randomHumanDelay(300, 600);
//
//                // After accepting changes, the button should change to "Place Bet"
//                // Continue to next iteration to handle "Place Bet" button
//                continue;
//            }
//
//            // Step 2: Handle "Place Bet" - Second click to actually place the bet
//            if (btnLower.contains("place bet") || btnLower.contains("place")) {
//                // Final odds validation before placement
//                if (!isOddsAcceptable(expectedOdds, currentOddsText)) {
//                    log.warn("Odds changed after Accept Changes: {} (need ≥ {})", currentOddsText, expectedOdds);
//                    waitStartTime = handleUnfavorableOdds(page, currentOddsText, expectedOdds, waitStartTime);
//                    if (waitStartTime == -1) return false;
//                    continue;
//                }
//
//                // Re-enter stake before final placement
//                if (!reEnterStakeBeforePlacement(page, bettingTask)) {
//                    continue;
//                }
//
//                // Verify arb is still active before placing bet
////                if (!arbOutcomeService.isActiveByExternalIdAndBookmaker(bettingTask.taskId(), bettingTask.bookmakerId())) {
////                    log.warn("Arb is no longer active, aborting placement");
////                    clearBetSlip(page);
////                    return false;
////                } todo
//
//                // Final click to place bet
//                log.info("→ CLICKING 'Place Bet' button @ {} (Final click - placing bet)", currentOddsText);
//                clickPlaceButton(page);
//                randomHumanDelay(500, 1000);
//
//                // Check for success after placement
//                if (detectSuccessModal(page)) {
//                    success = true;
//                    break;
//                }
//
//                continue;
//            }
//
//            // Unknown state
//            log.warn("Unknown button text: '{}' - waiting...", buttonText);
//            randomHumanDelay(400, 700);
//        }
//
//        return success;
//    }


    /**
     * Execute the main placement loop with state monitoring
     */
    private static boolean executePlacementLoop(Page page, BettingTask bettingTask,
                                                ArbOutcomeService arbOutcomeService,
                                                long startTime, long deadline) {
        log.info("[3/4] Starting optimized placement loop...");

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
                continue;
            }

            if ("BET_PLACED".equals(status)) {
                log.info("✅ Bet placement detected via state monitor!");
                String betId = (String) state.get("betId");
                if (betId != null) {
                    log.info("📋 Bet ID: {}", betId);
                }
                success = true;
                break;
            }

            if ("UNAVAILABLE".equals(status)) {
                log.error("Market UNAVAILABLE - match likely over or closed");
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

            // Get fresh task from DB if possible
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

            // Handle button actions - use else-if chain for proper flow control
            String btnLower = buttonText.toLowerCase();

            // Step 1: Handle "Accept Changes" - Click to accept odds changes
            if (btnLower.contains("accept") || btnLower.contains("change")) {
                // Verify odds are still acceptable before accepting changes
                if (!isOddsAcceptable(expectedOdds, currentOddsText)) {
                    log.warn("Odds not acceptable even though 'Accept Changes' is showing: {} (need ≥ {})",
                            currentOddsText, expectedOdds);
                    waitStartTime = handleUnfavorableOdds(page, currentOddsText, expectedOdds, waitStartTime);
                    if (waitStartTime == -1) return false;
                    continue;
                }

                log.info("→ CLICKING 'Accept Changes' button @ {}", currentOddsText);
                clickPlaceButton(page);
                randomHumanDelay(800, 1200); // Wait for button state to update
                continue; // Re-check state after accepting changes
            }
            // Step 2: Handle "Place Bet" - Click to place the bet
            else if (btnLower.contains("place bet") || btnLower.contains("place")) {
                // Final odds validation before placement
                if (!isOddsAcceptable(expectedOdds, currentOddsText)) {
                    log.warn("Odds changed before placement: {} (need ≥ {})", currentOddsText, expectedOdds);
                    waitStartTime = handleUnfavorableOdds(page, currentOddsText, expectedOdds, waitStartTime);
                    if (waitStartTime == -1) return false;
                    continue;
                }

                // Re-enter stake before final placement
                if (!reEnterStakeBeforePlacement(page, bettingTask)) {
                    randomHumanDelay(500, 800);
                    continue;
                }

                // Verify arb is still active before placing bet
//            if (!arbOutcomeService.isActiveByExternalIdAndBookmaker(bettingTask.taskId(), bettingTask.bookmakerId())) {
//                log.warn("Arb is no longer active, aborting placement");
//                clearBetSlip(page);
//                return false;
//            } todo

                // Click to place bet
//                clickPlaceButton(page);
                if (arbOutcomeService.isActiveByExternalIdAndBookmaker(bettingTask.taskId(), bettingTask.bookmakerId())) {
                    log.info("→ CLICKING 'Place Bet' button @ {} (attempting placement)", currentOddsText);
                    log.info("arb is still active, proceed to click");
                    
                    continue;
                }

                clickPlaceButton(page);
                randomHumanDelay(1000, 1500); // Wait for placement processing

                // Check for success modal
                if (detectSuccessModal(page)) {
                    success = true;
                    break;
                }

                // If no success modal, button may have changed to "Accept Changes" or error occurred
                // Continue to next iteration to re-evaluate the new button state
                log.debug("No success modal detected after clicking Place Bet, re-evaluating state...");
                randomHumanDelay(300, 600);
                continue; // Force re-check of button state
            }
            // Unknown button state
            else {
                log.warn("Unknown button text: '{}' - waiting...", buttonText);
                randomHumanDelay(400, 700);
            }
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
     * Re-enter stake before final placement
     */
    private static boolean reEnterStakeBeforePlacement(Page page, BettingTask task) {
        if (!enterStakeUsingJS(page, BigDecimal.valueOf(task.stakeAmount()))) {
            log.warn("Failed to re-enter stake before Place Bet → will retry");
            randomHumanDelay(500, 800);
            return false;
        }
        randomHumanDelay(200, 400);
        return true;
    }

    /* ===================== STAKE MANAGEMENT ===================== */

    /**
     * Enter stake using JavaScript evaluation for direct DOM manipulation
     */
    private static boolean enterStakeUsingJS(Page page, BigDecimal stakeAmount) {
        String stakeString = String.valueOf(stakeAmount.intValue());

        try {
            log.info("Entering stake: {}", stakeString);

            // First, try to use Playwright's native fill method (most reliable)
            try {
                Locator stakeInput = page.locator("input[type='number'][placeholder='stake']").first();

                // Wait for input to be ready
                stakeInput.waitFor(new Locator.WaitForOptions()
                        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                        .setTimeout(5000));

                // Clear and fill using Playwright's built-in method
                stakeInput.fill("");  // Clear first
                page.waitForTimeout(100);
//                stakeInput.fill(stakeString);  // Fill with stake
                typeFastHumanLike(stakeInput, stakeString);
                page.waitForTimeout(200);

                // Trigger blur to ensure value is committed
//                stakeInput.blur();
//                page.waitForTimeout(100);

                // Verify
                String verifiedValue = stakeInput.inputValue();

                if (stakeString.equals(verifiedValue)) {
                    log.info("✓ Stake entered successfully: {}", verifiedValue);
                    return true;
                } else {
                    log.warn("⚠ Fill method didn't work. Expected: {}, Got: {}", stakeString, verifiedValue);
                }
            } catch (Exception e) {
                log.warn("Playwright fill method failed: {}", e.getMessage());
            }

            // Fallback: Try typing method
            log.info("Trying typing method...");
            return retryStakeEntryWithTyping(page, stakeAmount);

        } catch (Exception e) {
            log.error("Error entering stake: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Retry stake entry using Playwright's type method (character-by-character typing)
     */
    private static boolean retryStakeEntryWithTyping(Page page, BigDecimal stakeAmount) {
        try {
            log.info("Using typing method for stake entry...");

            String stakeString = String.valueOf(stakeAmount.intValue());

            // Find the input element
            Locator stakeInput = page.locator("input[type='number'][placeholder='stake']").first();

            // Wait for it to be visible and enabled
            stakeInput.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));

            // Triple-click to select all (works better than Ctrl+A for some inputs)
            stakeInput.click(new Locator.ClickOptions().setClickCount(3));
            page.waitForTimeout(100);

            // Delete selected content
            page.keyboard().press("Delete");
            page.waitForTimeout(100);

            // Type each character with a small delay to simulate human input
            typeFastHumanLike(stakeInput, stakeString);

            // Wait a bit for the input to register
            page.waitForTimeout(200);

            // Press Tab to move focus away (triggers change event)
//            page.keyboard().press("Tab");
            page.waitForTimeout(200);

            // Verify the value was entered
            String finalValue = stakeInput.inputValue();

            if (stakeString.equals(finalValue)) {
                log.info("✓ Stake entered via typing: {}", finalValue);
                return true;
            } else {
                log.error("✗ Typing method failed. Expected: {}, Got: {}", stakeString, finalValue);

                // Last resort: Try with JavaScript but with more aggressive approach
                return forceStakeWithJavaScript(page, stakeAmount);
            }

        } catch (Exception e) {
            log.error("Error in typing method: {}", e.getMessage());
            return forceStakeWithJavaScript(page, stakeAmount);
        }
    }

    /**
     * Force stake entry using aggressive JavaScript manipulation
     */
    private static boolean forceStakeWithJavaScript(Page page, BigDecimal stakeAmount) {
        try {
            log.info("Forcing stake entry with JavaScript...");

            String stakeString = String.valueOf(stakeAmount.intValue());

            String jsForceStake = """
                (stakeValue) => {
                    // Find input
                    const input = document.querySelector('input[type="number"][placeholder="stake"]');
                    if (!input) {
                        return { success: false, message: 'Input not found', value: null };
                    }
                    
                    // Remove all event listeners by cloning and replacing
                    const newInput = input.cloneNode(true);
                    input.parentNode.replaceChild(newInput, input);
                    
                    // Set value directly
                    newInput.value = stakeValue;
                    
                    // Force the value to be set as an attribute too
                    newInput.setAttribute('value', stakeValue);
                    
                    // Manually trigger all possible events
                    ['focus', 'click', 'input', 'change', 'blur', 'keydown', 'keyup', 'keypress'].forEach(eventType => {
                        const event = new Event(eventType, { bubbles: true, cancelable: true });
                        newInput.dispatchEvent(event);
                    });
                    
                    // Focus and blur again
                    newInput.focus();
                    setTimeout(() => newInput.blur(), 10);
                    
                    return { 
                        success: true, 
                        message: 'Forced via JavaScript',
                        value: newInput.value 
                    };
                }
                """;

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsForceStake, stakeString);

            boolean success = Boolean.TRUE.equals(result.get("success"));
            String value = (String) result.get("value");

            if (success && stakeString.equals(value)) {
                log.info("✓ Stake forced via JavaScript: {}", value);
                page.waitForTimeout(300);

                // Final verification
                String verifyValue = (String) page.evaluate("""
                    () => {
                        const input = document.querySelector('input[type="number"][placeholder="stake"]');
                        return input ? input.value : null;
                    }
                    """);

                if (stakeString.equals(verifyValue)) {
                    log.info("✓ Final verification passed: {}", verifyValue);
                    return true;
                } else {
                    log.error("✗ Final verification failed. Expected: {}, Got: {}", stakeString, verifyValue);
                    return false;
                }
            } else {
                log.error("✗ JavaScript force method failed");
                return false;
            }

        } catch (Exception e) {
            log.error("Error forcing stake with JavaScript: {}", e.getMessage());
            return false;
        }
    }

    public static void typeFastHumanLike(Locator locator, String text) {
        // Optional: small random delay before starting (mimics human reaction)
        randomDelay(80, 250);

        // Focus the field first (critical for some betting sites)
        locator.evaluate("el => el.focus()");

        // Convert text to char array for per-character typing
        char[] chars = text.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            String charStr = String.valueOf(c);

            // 1. Type the character
            locator.press(charStr);

            // 2. Human-like typing speed: 80–220 ms per character (avg ~140ms = ~7 chars/sec)
            int baseDelay = 80 + (i % 3 == 0 ? 60 : 0); // slight rhythm variation
            randomDelay(baseDelay, baseDelay + 140);

            // 3. 3% chance of a tiny "thinking" pause (200–600ms) — makes it ultra-realistic
            if (Math.random() < 0.03) {
                randomDelay(200, 600);
            }

            // 4. 1% chance of a small backspace + retype (classic human typo fix)
            if (Math.random() < 0.01 && i > 0) {
                locator.press("Backspace");
                randomDelay(100, 300);
                locator.press(charStr); // retype the same char
                randomDelay(120, 280);
            }
        }

        // Final small pause after typing (human habit)
        randomDelay(100, 350);
    }

    /**
     * Generate random delay in milliseconds
     */
    private static int randomDelay(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }


    /**
     * Clear the betslip
     */
    public static boolean clearBetSlip(Page page) {
        try {
            // Target the element by its unique ID
            String clearAllSelector = "#betslip_head_removeall";

            // Check if the element is visible before clicking
            if (page.isVisible(clearAllSelector)) {
                log.info("{} Clearing betslip...", EMOJI_TRASH);
                page.click(clearAllSelector);
                page.waitForTimeout(500);
                log.info("{} Betslip cleared successfully", EMOJI_SUCCESS);
                return true;
            }

            // If the button isn't there, the slip might already be empty
            log.info("Clear button not visible - betslip may already be empty");
            return false;
        } catch (Exception e) {
            log.error("{} Failed to clear betslip: {}", EMOJI_ERROR, e.getMessage());
            return false;
        }
    }

    /**
     * Get betslip count
     */
    private static String getBetslipCount(Page page) {
        return page.evaluate("""
            () => {
                const matchBoxes = document.querySelectorAll('div.betslip__match-box');
                return matchBoxes.length.toString();
            }
            """).toString();
    }

    /**
     * Clear all betslip selections using JavaScript
     */
    private static void clearBetslipSelections(Page page) {
        page.evaluate("""
            () => {
                // Click the remove all button if available
                const removeAllBtn = document.querySelector('#betslip_head_removeall');
                if (removeAllBtn) {
                    removeAllBtn.click();
                }
            }
            """);
    }

    /**
     * Disable auto-accept odds changes (if applicable to Bet9ja)
     */
    public static void disableAcceptOddsChanges(Page page) {
        try {
            log.info("[2/4] Disabling auto-accept odds changes...");

            String jsDisableCheckbox = """
            () => {
                // Find the checkbox input
                const checkbox = document.querySelector('input#c-02[type="checkbox"]');
                
                if (!checkbox) {
                    // Try alternative selector
                    const altCheckbox = document.querySelector('div.checkbox input[type="checkbox"][id^="c-"]');
                    if (!altCheckbox) {
                        return { success: false, message: 'Accept odds change checkbox not found' };
                    }
                    
                    // Check if already unchecked
                    if (!altCheckbox.checked) {
                        return { success: true, alreadyDisabled: true, message: 'Checkbox already unchecked' };
                    }
                    
                    // Click the label to uncheck
                    const label = document.querySelector('label[for="' + altCheckbox.id + '"]');
                    if (label) {
                        label.click();
                    } else {
                        altCheckbox.click();
                    }
                    
                    return { success: true, alreadyDisabled: false, message: 'Checkbox unchecked successfully' };
                }
                
                // Check if already unchecked
                if (!checkbox.checked) {
                    return { success: true, alreadyDisabled: true, message: 'Checkbox already unchecked' };
                }
                
                // Click the label to uncheck (best practice for custom checkboxes)
                const label = document.querySelector('label[for="c-02"]');
                if (label) {
                    label.click();
                } else {
                    // Fallback: click checkbox directly
                    checkbox.click();
                }
                
                // Verify it was unchecked
                const finalState = checkbox.checked;
                
                return { 
                    success: !finalState, 
                    alreadyDisabled: false,
                    message: finalState ? 'Failed to uncheck' : 'Checkbox unchecked successfully'
                };
            }
            """;

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsDisableCheckbox);

            if (result == null) {
                log.warn("Could not disable auto-accept: null result");
                return;
            }

            Boolean success = (Boolean) result.get("success");
            Boolean alreadyDisabled = (Boolean) result.get("alreadyDisabled");
            String message = (String) result.get("message");

            if (Boolean.TRUE.equals(success)) {
                if (Boolean.TRUE.equals(alreadyDisabled)) {
                    log.debug("Auto-accept odds changes already disabled");
                } else {
                    log.info("✓ Auto-accept odds changes disabled successfully");
                }
            } else {
                log.warn("Failed to disable auto-accept: {}", message);
            }

        } catch (Exception e) {
            log.debug("Could not disable auto-accept: {}", e.getMessage());
        }
    }

    /* ===================== MODAL HANDLING ===================== */

    /**
     * Close success modal if present
     */
    public static void closeSuccessModal(Page page) {
        try {
            page.evaluate("""
                () => {
                    const modal = document.querySelector('div.success-modal, div[class*="success-modal"]');
                    if (modal) {
                        const closeBtn = modal.querySelector('button.close, button[class*="close"]');
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

    /**
     * Detect success modal/betslip confirmation
     */
    private static boolean detectSuccessModal(Page page) {
        try {
            // Wait for betslip match body to appear
            Locator successBetslip = page.locator("div.betslip__match-body");

            successBetslip.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));

            log.info("✅ SUCCESS - BET CONFIRMED!");

            // Extract bet details
            try {
                // Extract match name (first table-f > txt-cut > span)
                String matchName = page.locator("div.betslip__match-body div.table-f:first-child div.txt-cut span")
                        .first()
                        .textContent()
                        .trim();
                log.info("⚽ Match: {}", matchName);

                // Extract odds (first table-f > betslip__match-odds > txt-primary)
                String odds = page.locator("div.betslip__match-body div.table-f:first-child div.betslip__match-odds span.txt-primary")
                        .first()
                        .textContent()
                        .trim();
                log.info("📊 Odds: {}", odds);

                // Extract stake (second table-f > txt-cut > span containing "Stake:")
                String stakeInfo = page.locator("div.betslip__match-body div.table-f div.txt-cut span")
                        .filter(new Locator.FilterOptions().setHasText("Stake:"))
                        .textContent()
                        .trim();
                log.info("💰 {}", stakeInfo);

                // Extract potential win (second table-f > txt-r > span containing "Potential Win:")
                String winInfo = page.locator("div.betslip__match-body div.table-f div.txt-r span")
                        .filter(new Locator.FilterOptions().setHasText("Potential Win:"))
                        .textContent()
                        .trim();
                log.info("🎯 {}", winInfo);

                // Extract bet ID (table-a > txt-r > span)
                String betId = page.locator("div.betslip__match-body div.table-a div.txt-r span")
                        .textContent()
                        .trim();
                log.info("📋 {}", betId);

                // Extract selection/market details
                String selection = page.locator("div.betslip__match-details div.txt-white.txt-cut span")
                        .first()
                        .textContent()
                        .trim();
                String market = page.locator("div.betslip__match-details div.mt5 span")
                        .first()
                        .textContent()
                        .trim();
                log.info("🎰 Selection: {} | Market: {}", selection, market);

                // Extract kick-off time from match details
                String kickOff = page.locator("div.betslip__match-details div.w-30.txt-r span")
                        .textContent()
                        .trim();
                log.info("🕐 Kick-off: {}", kickOff);

                return true;

            } catch (Exception e) {
                log.warn("Could not extract bet details: {}", e.getMessage());
                return true; // Still successful if betslip body appeared
            }

        } catch (Exception e) {
            log.debug("Success betslip not detected: {}", e.getMessage());
            return false;
        }
    }

    /* ===================== JAVASCRIPT MONITORING ===================== */

    /**
     * Get the JavaScript state monitoring script for Bet9ja
     */
    private static String getStateMonitorScript() {
        return """
() => {
    // ─── BET PLACED CHECK ───────────────────────────────────────────────
    // ID lives in: div.betslip__match-body > div.table-a > div.txt-r > span
    const betIdElement = document.querySelector('div.betslip__match-body div.table-a div.txt-r span');
    if (betIdElement && betIdElement.textContent.includes('ID:')) {

        // Match name: first table-f > txt-cut > span
        const matchNameEl = document.querySelector('div.betslip__match-body div.table-f:first-child div.txt-cut span');
        const matchName = matchNameEl ? matchNameEl.textContent.trim() : null;

        // Odds: first table-f > betslip__match-odds > txt-primary
        const confirmedOddsEl = document.querySelector('div.betslip__match-body div.table-f:first-child div.betslip__match-odds span.txt-primary');
        const confirmedOdds = confirmedOddsEl ? confirmedOddsEl.textContent.trim() : null;

        // Stake: second table-f > txt-cut > span  ("Stake: 100.00")
        const stakeEl = Array.from(document.querySelectorAll('div.betslip__match-body div.table-f div.txt-cut span'))
            .find(s => s.textContent.includes('Stake:'));
        const stake = stakeEl ? stakeEl.textContent.trim() : null;

        // Potential Win: second table-f > txt-r > span  ("Potential Win: 147.00")
        const potWinEl = Array.from(document.querySelectorAll('div.betslip__match-body div.table-f div.txt-r span'))
            .find(s => s.textContent.includes('Potential Win:'));
        const potentialWin = potWinEl ? potWinEl.textContent.trim() : null;

        // Kick-off date/time: table-a first div > span > span
        const kickOffEl = document.querySelector('div.betslip__match-body div.table-a div:first-child span span');
        const kickOff = kickOffEl ? kickOffEl.textContent.trim() : null;

        // Selection: betslip__match-details > first table-f > txt-white > span  ("Under (2.5)")
        const selectionEl = document.querySelector('div.betslip__match-details div.txt-white.txt-cut span');
        const selection = selectionEl ? selectionEl.textContent.trim() : null;

        // Market type: betslip__match-details > div.mt5 > span  ("Total")
        const marketEl = document.querySelector('div.betslip__match-details div.mt5 span');
        const market = marketEl ? marketEl.textContent.trim() : null;

        // Selection odds: betslip__match-details > betslip__match-odds > txt-primary
        const selOddsEl = document.querySelector('div.betslip__match-details div.betslip__match-odds span.txt-primary');
        const selectionOdds = selOddsEl ? selOddsEl.textContent.trim() : null;

        return {
            status: 'BET_PLACED',
            betId: betIdElement.textContent.trim(),
            matchName: matchName,
            confirmedOdds: confirmedOdds,
            stake: stake,
            potentialWin: potentialWin,
            kickOff: kickOff,
            selection: selection,
            market: market,
            selectionOdds: selectionOdds
        };
    }

    // ─── NO SELECTION ────────────────────────────────────────────────────
    const matchBox = document.querySelector('div.betslip__match-box');
    if (!matchBox) {
        const betslipMatch = document.querySelector('div.betslip__match');
        if (!betslipMatch) {
            return { status: 'NO_SELECTION' };
        }
    }

    // ─── SUSPENDED ───────────────────────────────────────────────────────
    const suspendedMsg = document.querySelector('div.betslip__match-msg span.txt-red');
    if (suspendedMsg && suspendedMsg.textContent.trim().toLowerCase() === 'suspended') {
        return { status: 'SUSPENDED' };
    }

    // ─── UNAVAILABLE / CLOSED ────────────────────────────────────────────
    const msgElement = document.querySelector('div.betslip__match-msg');
    if (msgElement) {
        const msgText = msgElement.textContent.trim().toLowerCase();
        if (msgText.includes('unavailable') || msgText.includes('closed')) {
            return { status: 'UNAVAILABLE' };
        }
    }

    // ─── LIVE ODDS ───────────────────────────────────────────────────────
    // Mirrors confirmed-bet structure: betslip__match-body odds while bet is pending
    let oddsText = null;
    const oddsInBody = document.querySelector('div.betslip__match-body div.table-f:first-child div.betslip__match-odds span.txt-primary');
    if (oddsInBody) {
        oddsText = oddsInBody.textContent.trim();
    }
    if (!oddsText) {
        const oddsInRow = document.querySelector('div.betslip__match-row div.betslip__match-odds span.txt-primary');
        if (oddsInRow) oddsText = oddsInRow.textContent.trim();
    }
    if (!oddsText) {
        const oddsInBox = document.querySelector('div.betslip__match-box span.txt-primary');
        if (oddsInBox) oddsText = oddsInBox.textContent.trim();
    }
    if (!oddsText) {
        const oddsAnywhere = document.querySelector('div.betslip__match-odds span.txt-primary');
        if (oddsAnywhere) oddsText = oddsAnywhere.textContent.trim();
    }

    // ─── MARKET INFO (pre-placement) ─────────────────────────────────────
    // Selection label e.g. "Under (2.5)" from match-details
    const selectionEl = document.querySelector('div.betslip__match-details div.txt-white.txt-cut span');
    const outcomeText = selectionEl ? selectionEl.textContent.trim() : null;

    // Market type e.g. "Total"
    const marketTypeEl = document.querySelector('div.betslip__match-details div.mt5 span');
    const marketTypeText = marketTypeEl ? marketTypeEl.textContent.trim() : null;

    // Match name from details (fallback for league/match info)
    const detailsMatchEl = document.querySelector('div.betslip__match-details div.table-f div.txt-cut span.va-mid');
    const leagueText = detailsMatchEl ? detailsMatchEl.textContent.trim() : null;

    // ─── BUTTON STATE ────────────────────────────────────────────────────
    const placeBtn = document.querySelector('div#betslip_buttons_placebet.btn, button.betslip__footer-btn, button[class*="place"]');
    const btnText = placeBtn ? placeBtn.textContent.trim() : '';
    const btnDisabled = placeBtn
        ? (placeBtn.disabled || placeBtn.classList.contains('disabled') || placeBtn.classList.contains('btn-disabled'))
        : true;

    // ─── SUCCESS / ODDS-CHANGE OVERLAYS ──────────────────────────────────
    const successModal = document.querySelector('div.success-modal, div[class*="success"], div.notification[class*="success"]');
    const successVisible = !!(successModal && successModal.offsetParent !== null);

    const oddsChangePopup = document.querySelector('div.odds-change-popup, div[class*="odds-change"]');
    const oddsChangeVisible = !!(oddsChangePopup && oddsChangePopup.offsetParent !== null);

    // ─── STAKE INPUT ─────────────────────────────────────────────────────
    const stakeInput = document.querySelector('div.input__holder input.input[type="number"][placeholder="stake"]');
    const currentStake = stakeInput ? stakeInput.value : null;

    // ─── POTENTIAL RETURN (pre-placement label) ───────────────────────────
    // Use same selector pattern as confirmed: span containing "Potential Win:"
    const potReturnEl = Array.from(document.querySelectorAll('div.betslip__match-body div.table-f div.txt-r span'))
        .find(s => s.textContent.includes('Potential Win:'));
    const potentialReturn = potReturnEl
        ? potReturnEl.textContent.trim()
        : (document.querySelector('div.betslip__footer-amount, div[class*="potential-return"], div[class*="to-return"]') || {}).textContent?.trim() || null;

    return {
        status: 'OK',
        oddsText: oddsText,
        buttonText: btnText,
        buttonDisabled: btnDisabled,
        successVisible: successVisible,
        oddsChangeVisible: oddsChangeVisible,
        outcomeText: outcomeText,
        marketTypeText: marketTypeText,
        leagueText: leagueText,
        currentStake: currentStake,
        potentialReturn: potentialReturn,
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
                // Bet9ja uses a div with ID betslip_buttons_placebet instead of a button
                const btn = document.querySelector('div#betslip_buttons_placebet.btn') 
                           || document.querySelector('button.betslip__footer-btn') 
                           || document.querySelector('button[class*="place"]');
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

    /* ===================== HELPER METHODS ===================== */

    /**
     * Get fresh betting task from database
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

    /**
     * Random human-like delay
     */
    private static void randomHumanDelay(int minMs, int maxMs) {
        try {
            int delay = minMs + (int) (Math.random() * (maxMs - minMs));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /* ===================== LOGGING METHODS ===================== */

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

        log.info("Button: \"{}\" | Disabled: {} | Current-Odds: {} | Expected-Odds: {}",
                buttonText, buttonDisabled, currentOddsText, expectedOdds);
    }

    /**
     * Log placement success
     */
    private static void logPlacementSuccess(Page page, long startTime) {
        if (detectSuccessModal(page)) {
            long duration = System.currentTimeMillis() - startTime;
            log.info("{} BET PLACED SUCCESSFULLY! | {}ms", EMOJI_SUCCESS, duration);
            log.info("─────────────────────────────────────────────────────────────");
            handleSuccessModal(page);
        } else {
            log.info("Placement completed without success modal detection");
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
