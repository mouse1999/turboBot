package com.mouse.bet.util.onewin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.mouse.bet.converter.ModelConverter;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.service.ArbOutcomeService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles bet placement operations for 1win platform
 */
@Slf4j
public class OneWinBetPlacement {

    // Configuration constants
    private static final int MAX_ATTEMPTS = 10;
    private static final int WAIT_BETWEEN_ATTEMPTS_MS = 3000;
    private static final int TOTAL_WAIT_TIME_MS = 30000;
    private static final double AMOUNT_TOLERANCE = 0.01;

    /**
     * Main method to place a bet with retry logic
     */
    public static boolean placeBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        try {
            log.info("Starting bet placement process for task: {}", task.taskId());
            
            long startTime = System.currentTimeMillis();
            int attempt = 0;

            while (attempt < MAX_ATTEMPTS) {
                attempt++;
                long elapsedTime = System.currentTimeMillis() - startTime;

                if (hasExceededTimeout(elapsedTime)) {
                    log.error("Timeout: Bet placement exceeded {} seconds", TOTAL_WAIT_TIME_MS / 1000);
                    return false;
                }

                log.info("Bet placement attempt {}/{}", attempt, MAX_ATTEMPTS);

                // Step 1: Select and verify bet (skip on first attempt if already selected)
                if (attempt > 1 && !selectAndVerifyBet(page, task, arbOutcomeService)) {
                    waitAndRetry("Bet not available or verification failed");
                    continue;
                }

                log.info("Bet successfully selected and verified");

                // Step 2: Verify bet is still in betslip
                randomHumanDelay(500, 1000);
                if (!isBetInBetslip(page)) {
                    log.warn("Bet disappeared from betslip (likely due to odds change). Retrying...");
                    continue;
                }

                BettingTask freshTask = getFreshTask(task, arbOutcomeService);
                if (freshTask != null) {
                    log.info("Using fresh betting task from DB");
                    task = freshTask;
                } else {
                    log.warn("Could not fetch fresh task, using current task");
                }

                // Step 3: Enter stake amount
                if (!enterStake(page, task.stakeAmount())) {
                    log.error("Failed to enter stake amount");
                    clearBetSlip(page);
                    return false;
                }

                // Step 4: Verify bet still in betslip after entering stake
                randomHumanDelay(500, 1000);
                if (!isBetInBetslip(page)) {
                    log.warn("Bet disappeared after entering stake. Retrying...");
                    continue;
                }

                // Step 5: Log possible win amount
                logPossibleWin(page);


                // Step 6: Click place bet button
                if (!clickPlaceBet(page,task, arbOutcomeService)) {
                    log.error("Failed to click place bet button");
                    clearBetSlip(page);
                    return false;
                }

                randomHumanDelay(1000, 2000);

                // Step 7: Handle placement response
                BetPlacementResult result = handlePlacementResponse(page, task);

                if (processPlacementResult(page, result)) {
                    return true;
                } else if (result == BetPlacementResult.ODDS_CHANGED) {
                    continue; // Retry
                } else {
                    return false; // Fatal error
                }
            }

            log.error("Failed to place bet after {} attempts", MAX_ATTEMPTS);
//            clearBetSlip(page);
            return false;

        } catch (Exception e) {
            log.error("Error during bet placement: {}", e.getMessage(), e);
            clearBetSlip(page);
            return false;
        }
    }

    /**
     * Select and verify the bet
     */
    private static boolean selectAndVerifyBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        boolean betSelected = OneWinMarketUtil.selectAndVerifyBetJS(page, task, arbOutcomeService);

        if (!betSelected) {
            log.warn("Bet selection or verification failed");
            return false;
        }

        return true;
    }

    /**
     * Check if timeout has been exceeded
     */
    private static boolean hasExceededTimeout(long elapsedTime) {
        return elapsedTime > TOTAL_WAIT_TIME_MS;
    }

    /**
     * Wait before retrying
     */
    private static void waitAndRetry(String reason) {
        log.warn("{}. Waiting {} seconds for outcome to appear...",
                reason, WAIT_BETWEEN_ATTEMPTS_MS / 1000);
        randomHumanDelay(WAIT_BETWEEN_ATTEMPTS_MS - 500, WAIT_BETWEEN_ATTEMPTS_MS + 500);
    }

    /**
     * Check if bet is in betslip
     */
    private static boolean isBetInBetslip(Page page) {
        int betslipCount = getBetslipCount(page);
        return betslipCount > 0;
    }

    /**
     * Get betslip count
     */

    private static int getBetslipCount(Page page) {
        try {
            Locator coupons = page.locator("div._coupon_4pzt1_2");
            return coupons.count();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Enter stake amount with verification
     */
    private static boolean enterStake(Page page, double amount) {
        log.info("Entering stake amount: {}", amount);

        try {
            // Format amount correctly
            String amountStr = String.format("%.2f", amount);

            // Use JavaScript to find, focus, clear, and enter stake
            String jsEnterStake = """
            (amount) => {
                // Find the stake input
                const input = document.querySelector("input[data-qa='amount']");
                
                if (!input) {
                    return { success: false, error: 'Stake input not found' };
                }
                
                // Scroll into view
                input.scrollIntoView({ behavior: 'smooth', block: 'center' });
                
                // Focus the input
                input.focus();
                
                // Clear existing value
                input.value = '';
                
                // Trigger input event to notify React/Vue
                input.dispatchEvent(new Event('input', { bubbles: true }));
                
                // Set new value
                input.value = amount;
                
                // Trigger change and input events
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                
                // Blur and focus again to ensure validation
                input.blur();
                input.focus();
                
                return {
                    success: true,
                    enteredValue: input.value,
                    isVisible: input.offsetParent !== null,
                    isDisabled: input.disabled
                };
            }
            """;

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsEnterStake, amountStr);

            Boolean success = (Boolean) result.get("success");

            if (Boolean.FALSE.equals(success)) {
                String error = (String) result.get("error");
                log.error("JS stake entry failed: {}", error);
                return false;
            }

            // Add small delay for UI to update
            randomHumanDelay(300, 600);

            // Verify the entered value
            String enteredValue = (String) result.get("enteredValue");
            Boolean isVisible = (Boolean) result.get("isVisible");
            Boolean isDisabled = (Boolean) result.get("isDisabled");

            log.debug("Stake entry result - Entered: {}, Visible: {}, Disabled: {}",
                    enteredValue, isVisible, isDisabled);

            // Verify amount matches
            try {
                double enteredAmount = Double.parseDouble(enteredValue);
                double difference = Math.abs(enteredAmount - amount);

                if (difference > AMOUNT_TOLERANCE) {
                    log.error("Stake amount mismatch - Expected: {}, Entered: {}", amount, enteredAmount);
                    return false;
                }

                log.info("Stake amount verified: {}", enteredAmount);
                return true;

            } catch (NumberFormatException e) {
                log.error("Failed to parse entered amount: {}", enteredValue);
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to enter stake amount: {}", e.getMessage());
            return false;
        }
    }

//    /**
//     * Verify stake amount was entered correctly
//     */
//    private static boolean verifyStakeAmount(Locator stakeInput, double expectedAmount) {
//        try {
//            String enteredValue = stakeInput.inputValue();
//            double enteredAmount = Double.parseDouble(enteredValue);
//
//            double difference = Math.abs(enteredAmount - expectedAmount);
//            if (difference > AMOUNT_TOLERANCE) {
//                log.error("Stake amount mismatch - Expected: {}, Entered: {}",
//                        expectedAmount, enteredAmount);
//                return false;
//            }
//
//            log.info("Stake amount verified: {}", enteredAmount);
//            return true;
//
//        } catch (NumberFormatException e) {
//            log.error("Failed to parse entered amount");
//            return false;
//        }
//    }

    /**
     * Log possible win amount
     */
    private static void logPossibleWin(Page page) {
        String possibleWin = getPossibleWinAmount(page);
        log.info("Possible win amount: {}", possibleWin);
    }

    /**
     * Get possible win amount from betslip
     */
    public static String getPossibleWinAmount(Page page) {
        try {
            Locator winAmountElement = page.locator(
                    "span[class*='_betAmount'], div[class*='_betAmount'] span"
            ).first();

            if (winAmountElement.count() > 0) {
                return winAmountElement.textContent().trim();
            }

            return "N/A";

        } catch (Exception e) {
            log.debug("Could not get possible win amount: {}", e.getMessage());
            return "N/A";
        }
    }

    /**
     * Click the place bet button
     */
//    private static boolean clickPlaceBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
//        log.info("Clicking 'Place a bet' button...");
//
//        try {
//            String[] selectors = {
//                    "button:has-text('Place a bet')",
//                    "button._root_9f102_8._variantAccent_9f102_143:has-text('Place')",
//                    "button[type='button']:has(span:has-text('Place a bet'))"
//            };
//
//            for (String selector : selectors) {
//                Locator placeBetButton = page.locator(selector);
//
//                if (placeBetButton.count() > 0) {
//                    if (placeBetButton.isDisabled()) {
//                        log.warn("Place bet button is disabled");
//                        return false;
//                    }
//
//                    randomHumanDelay(500, 1000);
//                    if (arbOutcomeService.isActiveByExternalIdAndBookmaker(task.taskId(), task.bookmakerId())){
//                        log.info("arb is still active, proceed to click");
//                        placeBetButton.click();
//                        log.info("Clicked 'Place a bet' button");
//                        return true;
//
//                    }
//
//                }
//            }
//
//            log.error("Place bet button not found");
//            return false;
//
//        } catch (Exception e) {
//            log.error("Failed to click place bet button: {}", e.getMessage());
//            return false;
//        }
//    }

    private static boolean clickPlaceBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        log.info("Clicking 'Place a bet' button...");

        String jsClickPlaceBet = """
    () => {
        // Try multiple strategies to find the button
        const strategies = [
            // Strategy 1: Find by exact button structure in footer
            () => {
                const footer = document.querySelector('div._footer_113dk_25');
                if (!footer) return null;
                
                const submitRow = footer.querySelector('div._submitRow_113dk_45');
                if (!submitRow) return null;
                
                const buttons = submitRow.querySelectorAll('button._root_9f102_8._variantAccent_9f102_143');
                for (const btn of buttons) {
                    const contentSpan = btn.querySelector('span._content_10cbr_2');
                    if (contentSpan && contentSpan.textContent.includes('Place a bet')) {
                        return btn;
                    }
                }
                return null;
            },
            
            // Strategy 2: Find by text content in accent button
            () => {
                const buttons = document.querySelectorAll('button._root_9f102_8._variantAccent_9f102_143');
                for (const btn of buttons) {
                    if (btn.textContent.includes('Place a bet')) {
                        return btn;
                    }
                }
                return null;
            },
            
            // Strategy 3: Find by content class and text
            () => {
                const contentSpans = document.querySelectorAll('span._content_10cbr_2');
                for (const span of contentSpans) {
                    if (span.textContent.includes('Place a bet')) {
                        const button = span.closest('button');
                        if (button) return button;
                    }
                }
                return null;
            },
            
            // Strategy 4: Generic text search as fallback
            () => {
                const buttons = document.querySelectorAll('button[type="button"]');
                for (const btn of buttons) {
                    if (btn.textContent.includes('Place a bet') && 
                        btn.classList.contains('_variantAccent_9f102_143')) {
                        return btn;
                    }
                }
                return null;
            }
        ];
        
        // Try each strategy
        for (let i = 0; i < strategies.length; i++) {
            try {
                const button = strategies[i]();
                if (button) {
                    console.log(`✓ Found place bet button using strategy ${i + 1}`);
                    
                    // Check if button is disabled
                    const isDisabled = button.disabled || 
                                      button.hasAttribute('disabled') ||
                                      button.getAttribute('aria-busy') === 'true' ||
                                      button.classList.contains('disabled');
                    
                    if (isDisabled) {
                        console.log('✗ Button is disabled');
                        return {
                            found: true,
                            disabled: true,
                            strategy: i + 1
                        };
                    }
                    
                    // Check if button is visible
                    const rect = button.getBoundingClientRect();
                    const isVisible = rect.width > 0 && 
                                     rect.height > 0 && 
                                     window.getComputedStyle(button).visibility !== 'hidden';
                    
                    if (!isVisible) {
                        console.log('✗ Button is not visible');
                        return {
                            found: true,
                            disabled: false,
                            visible: false,
                            strategy: i + 1
                        };
                    }
                    
                    // Mark button for clicking
                    button.setAttribute('data-place-bet-target', 'true');
                    button.style.outline = '2px solid green';
                    
                    return {
                        found: true,
                        disabled: false,
                        visible: true,
                        strategy: i + 1,
                        buttonText: button.textContent.trim()
                    };
                }
            } catch (err) {
                console.log(`Strategy ${i + 1} failed:`, err.message);
            }
        }
        
        console.log('✗ Place bet button not found with any strategy');
        
        // Return available buttons for debugging
        const allButtons = Array.from(document.querySelectorAll('button[type="button"]'))
            .map(btn => ({
                text: btn.textContent.trim().substring(0, 50),
                classes: Array.from(btn.classList).join(' ')
            }));
        
        return {
            found: false,
            availableButtons: allButtons
        };
    }
    """;

        try {
            // Execute JavaScript to find and mark the button
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsClickPlaceBet);

            boolean found = (Boolean) result.getOrDefault("found", false);

            if (!found) {
                log.error("Place bet button not found");
                if (result.containsKey("availableButtons")) {
                    log.debug("Available buttons: {}", result.get("availableButtons"));
                }
                return false;
            }

            // Check if button is disabled
            boolean disabled = (Boolean) result.getOrDefault("disabled", false);
            if (disabled) {
                log.warn("Place bet button is disabled");
                return false;
            }

            // Check if button is visible
            boolean visible = (Boolean) result.getOrDefault("visible", true);
            if (!visible) {
                log.warn("Place bet button is not visible");
                return false;
            }

            int strategy = ((Number) result.getOrDefault("strategy", 0)).intValue();
            String buttonText = (String) result.getOrDefault("buttonText", "Place a bet");
            log.info("✓ Found button using strategy {}: '{}'", strategy, buttonText);

            // Human-like delay
            randomHumanDelay(500, 1000);

            // Check if arb is still active before clicking
            if (!arbOutcomeService.isActiveByExternalIdAndBookmaker(task.taskId(), task.bookmakerId())) {
                log.warn("Arb is no longer active, aborting placement");
                cleanupPlaceBetMarker(page);
                return false;
            }

            log.info("Arb is still active, proceeding to click");

            // Click the marked button
            Locator targetButton = page.locator("button[data-place-bet-target='true']");

            if (targetButton.count() == 0) {
                log.error("Marked button disappeared");
                return false;
            }

            // Scroll into view and click
            targetButton.scrollIntoViewIfNeeded();

            try {
                targetButton.click(new Locator.ClickOptions().setTimeout(5000));
                log.info("✓ Clicked 'Place a bet' button");
            } catch (Exception e) {
                log.warn("Primary click failed, attempting JS click: {}", e.getMessage());
                targetButton.evaluate("el => el.click()");
                log.info("✓ Clicked 'Place a bet' button (JS fallback)");
            }

            // Cleanup
            cleanupPlaceBetMarker(page);

            return true;

        } catch (Exception e) {
            log.error("Failed to click place bet button: {}", e.getMessage(), e);
            cleanupPlaceBetMarker(page);
            return false;
        }
    }

    private static void cleanupPlaceBetMarker(Page page) {
        try {
            page.evaluate("""
            () => {
                const marked = document.querySelectorAll('[data-place-bet-target="true"]');
                marked.forEach(el => {
                    el.removeAttribute('data-place-bet-target');
                    el.style.outline = '';
                });
            }
            """);
        } catch (Exception e) {
            log.debug("Cleanup marker failed: {}", e.getMessage());
        }
    }



    /**
     * Handle bet placement response
     */
    private static BetPlacementResult handlePlacementResponse(Page page, BettingTask task) {
        log.debug("Waiting for bet placement response...");
        randomHumanDelay(2000, 3000);

        if (isPlacementSuccessful(page)) {
            return BetPlacementResult.SUCCESS;
        }

//        if (hasOddsChangePrompt(page)) {
//            return BetPlacementResult.ODDS_CHANGED;
//        }

//        if (hasInsufficientBalance(page)) {
//            return BetPlacementResult.INSUFFICIENT_BALANCE;
//        }

//        if (isBetRejected(page)) {
//            return BetPlacementResult.BET_REJECTED;
//        }

        // Check if betslip is empty (might indicate success)
        if (getBetslipCount(page) == 0) {
            log.debug("Betslip is empty - assuming success");
            return BetPlacementResult.SUCCESS;
        }

        log.warn("Unable to determine bet placement result");
        return BetPlacementResult.UNKNOWN;
    }

    /**
     * Process placement result and take appropriate action
     */
    private static boolean processPlacementResult(Page page, BetPlacementResult result) {
        switch (result) {
            case SUCCESS:
                log.info("✓ Bet placed successfully!");
                return true;

            case ODDS_CHANGED:
                log.warn("Odds changed - attempting to accept changes and retry...");
                boolean accepted = handleOddsChange(page);
                if (accepted) {
                    randomHumanDelay(1000, 1500);
                    return false; // Signal to retry
                } else {
                    log.error("Failed to handle odds change");
                    clearBetSlip(page);
                    return false;
                }

            case INSUFFICIENT_BALANCE:
                log.error("Insufficient balance to place bet");
                clearBetSlip(page);
                return false;

            case BET_REJECTED:
                log.error("Bet was rejected by bookmaker");
                clearBetSlip(page);
                return false;

            case TIMEOUT:
                log.warn("Timeout waiting for bet placement response. Retrying...");
                randomHumanDelay(2000, 3000);
                return false; // Signal to retry

            case UNKNOWN:
            default:
                log.error("Unknown bet placement result");
                clearBetSlip(page);
                return false;
        }
    }

    /**
     * Check if bet placement was successful
     */
    private static boolean isPlacementSuccessful(Page page) {
        log.debug("Checking for bet placement success...");

        try {
            String jsCheckSuccess = """
        () => {
            // Look for multiple success indicators
            
            // Check 1: Root container with bet placed confirmation
            const rootContainer = document.querySelector("div._root_1yhg0_2");
            if (rootContainer) {
                const heroSection = rootContainer.querySelector("div._hero_1yhg0_14");
                if (heroSection) {
                    const title = heroSection.querySelector("p._title_1yhg0_7");
                    if (title && title.textContent.trim() === 'Bet placed') {
                        return {
                            success: true,
                            strategy: 'Root container with Bet placed',
                            details: extractBetDetails(rootContainer)
                        };
                    }
                }
            }
            
            // Check 2: Look for "Bet placed" anywhere (fallback)
            const betPlacedTexts = document.querySelectorAll("p._title_1yhg0_7");
            for (const el of betPlacedTexts) {
                if (el.textContent.trim() === 'Bet placed') {
                    const container = el.closest('div._root_1yhg0_2') || document;
                    return {
                        success: true,
                        strategy: 'Bet placed text found',
                        details: extractBetDetails(container)
                    };
                }
            }
            
            // Check 3: Continue betting button
            const continueBtn = document.querySelector("button._goBet_1yhg0_41");
            if (continueBtn && continueBtn.textContent.includes('Continue betting')) {
                return {
                    success: true,
                    strategy: 'Continue betting button',
                    details: extractBetDetails(document)
                };
            }
            
            // Check 4: Check icon (green checkmark)
            const checkIcon = document.querySelector("span._checkIcon_zbiwv_52");
            if (checkIcon) {
                return {
                    success: true,
                    strategy: 'Success check icon',
                    details: extractBetDetails(document)
                };
            }
            
            // Check 5: Bet list with details (even without root container)
            const betList = document.querySelector("div._list_1yhg0_18 div._root_zbiwv_2");
            if (betList) {
                const hasOdds = betList.querySelector("span._root_1lnnj_2._primary_1lnnj_15");
                const hasBetAmount = betList.querySelector("div._betAmount_zbiwv_82");
                if (hasOdds && hasBetAmount) {
                    return {
                        success: true,
                        strategy: 'Bet details found',
                        details: extractBetDetails(document)
                    };
                }
            }
            
            return { 
                success: false, 
                reason: 'No success indicators found'
            };
            
            function extractBetDetails(container) {
                return {
                    selection: container.querySelector("div._title_zbiwv_31")?.textContent.trim() || '',
                    match: container.querySelector("div._subtitle_zbiwv_39")?.textContent.trim() || '',
                    odds: container.querySelector("span._root_1lnnj_2._primary_1lnnj_15")?.textContent.trim() || '',
                    betAmount: container.querySelector("div._betAmount_zbiwv_82 span")?.textContent.trim() || '',
                    possibleWin: container.querySelector("div._profitAmount_zbiwv_91")?.textContent.trim() || '',
                    hasCheckIcon: !!container.querySelector("span._checkIcon_zbiwv_52")
                };
            }
        }
        """;

            // Wait up to 10 seconds for success indicators
            long startTime = System.currentTimeMillis();
            long timeout = 10000; // 10 seconds

            while (System.currentTimeMillis() - startTime < timeout) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) page.evaluate(jsCheckSuccess);

                Boolean success = (Boolean) result.get("success");

                if (Boolean.TRUE.equals(success)) {
                    String strategy = (String) result.get("strategy");
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("✓ Bet placement successful after {}ms - {}", elapsed, strategy);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> details = (Map<String, Object>) result.get("details");
                    if (details != null && !details.isEmpty()) {
                        logBetDetailsFromJS(details);
                    }

                    return true;
                }

                // Wait before next check
                Thread.sleep(300);
            }

            log.warn("Bet placement check timed out after {}ms", timeout);
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while checking placement success");
            return false;
        } catch (Exception e) {
            log.error("Error checking placement success: {}", e.getMessage());
            return false;
        }
    }

    private static void logBetDetailsFromJS(Map<String, Object> details) {
        if (details.containsKey("selection")) {
            log.info("  Selection: {}", details.get("selection"));
        }
        if (details.containsKey("match")) {
            log.info("  Match: {}", details.get("match"));
        }
        if (details.containsKey("odds")) {
            log.info("  Odds: {}", details.get("odds"));
        }
        if (details.containsKey("betAmount")) {
            log.info("  Bet amount: {}", details.get("betAmount"));
        }
        if (details.containsKey("possibleWin")) {
            log.info("  Possible win: {}", details.get("possibleWin"));
        }
        if (Boolean.TRUE.equals(details.get("hasCheckIcon"))) {
            log.info("  ✓ Check icon verified");
        }
        if (details.containsKey("extractionError")) {
            log.warn("  ⚠ Detail extraction had errors: {}", details.get("extractionError"));
        }
    }


    /**
     * Log element text if present
     */
    private static void logIfPresent(Page page, String selector, String label) {
        try {
            Locator element = page.locator(selector);
            if (element.count() > 0) {
                String text = element.textContent().trim();
                log.info("{}: {}", label, text);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Check for odds change prompt
     */
//    private static boolean hasOddsChangePrompt(Page page) {
//        try {
//            // Look for odds change modal or message
//            String[] selectors = {
//                    "div:has-text('Odds changed')",
//                    "div:has-text('odds have changed')",
//                    "button:has-text('Accept changes')",
//                    "button:has-text('Accept')"
//            };
//
//            for (String selector : selectors) {
//                if (hasElement(page, selector)) {
//                    log.debug("Found odds change indicator: {}", selector);
//                    return true;
//                }
//            }
//
//            return false;
//
//        } catch (Exception e) {
//            log.debug("Error checking odds change: {}", e.getMessage());
//            return false;
//        }
//    }
//
//    /**
//     * Check for insufficient balance message
//     */
//    private static boolean hasInsufficientBalance(Page page) {
//        try {
//            String[] selectors = {
//                    "div:has-text('Insufficient')",
//                    "div:has-text('insufficient balance')",
//                    "div:has-text('Not enough')",
//                    "div:has-text('Low balance')"
//            };
//
//            for (String selector : selectors) {
//                if (hasElement(page, selector)) {
//                    log.debug("Found insufficient balance indicator");
//                    return true;
//                }
//            }
//
//            return false;
//
//        } catch (Exception e) {
//            return false;
//        }
//    }
//
//    /**
//     * Check if bet was rejected
//     */
//    private static boolean isBetRejected(Page page) {
//        try {
//            String[] selectors = {
//                    "div:has-text('Bet rejected')",
//                    "div:has-text('rejected')",
//                    "div:has-text('not accepted')",
//                    "div:has-text('declined')"
//            };
//
//            for (String selector : selectors) {
//                if (hasElement(page, selector)) {
//                    log.debug("Found bet rejection indicator");
//                    return true;
//                }
//            }
//
//            return false;
//
//        } catch (Exception e) {
//            return false;
//        }
//    }

    /**
     * Handle odds change by accepting the new odds
     */
    private static boolean handleOddsChange(Page page) {
        try {
            log.info("Handling odds change...");

            String[] acceptSelectors = {
                    "button:has-text('Accept changes')",
                    "button:has-text('Accept')",
                    "button:has-text('OK')"
            };

            for (String selector : acceptSelectors) {
                Locator acceptButton = page.locator(selector);
                if (acceptButton.count() > 0 && acceptButton.isVisible()) {
                    randomHumanDelay(500, 1000);
                    acceptButton.click();
                    log.info("Accepted odds change");
                    return true;
                }
            }

            log.warn("Could not find accept button for odds change");
            return false;

        } catch (Exception e) {
            log.error("Error handling odds change: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Clear the betslip
     */
    public static void clearBetSlip(Page page) {
        try {
            log.info("Clearing betslip...");

            // Try multiple selectors for clear/trash button
            String[] clearSelectors = {
                    "button[class*='_iconOnly']:has(span[class*='trash'])",
                    "button:has(span[style*='trash.svg'])",
                    "svg[class*='_removeSelection']",
                    "button[aria-label='Clear betslip']"
            };

            for (String selector : clearSelectors) {
                Locator clearButton = page.locator(selector);
                if (clearButton.count() > 0) {
                    clearButton.first().click();
                    randomHumanDelay(500, 1000);
                    log.info("Betslip cleared");
                    return;
                }
            }

            log.warn("Could not find clear betslip button");

        } catch (Exception e) {
            log.error("Error clearing betslip: {}", e.getMessage());
        }
    }

    /**
     * Click continue betting button
     */
    public static void clickContinueBetting(Page page) {
        try {
            log.info("Clicking 'Continue betting' button...");

            Locator continueButton = page.locator("button:has-text('Continue betting')");

            if (continueButton.count() > 0 && continueButton.isVisible()) {
                randomHumanDelay(500, 1000);

                try {
                    continueButton.click();
                    log.info("Clicked 'Continue betting'");
                } catch (Exception e) {
                    log.warn("Regular click failed, trying force click...");
                    continueButton.click(new Locator.ClickOptions().setForce(true));
                    log.info("Clicked 'Continue betting' with force");
                }

                randomHumanDelay(800, 1200);
            } else {
                log.warn("'Continue betting' button not found");
            }

        } catch (Exception e) {
            log.error("Error clicking continue betting: {}", e.getMessage());
        }
    }

    /**
     * Enum for bet placement results
     */
    public enum BetPlacementResult {
        SUCCESS,
        ODDS_CHANGED,
        INSUFFICIENT_BALANCE,
        BET_REJECTED,
        TIMEOUT,
        UNKNOWN
    }

    /**
     * Parse amount from text (handles currency symbols)
     */
    public static double parseAmount(String amountText) {
        try {
            String cleaned = amountText.replaceAll("[^0-9.]", "");
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse amount: {}", amountText);
            return 0.0;
        }
    }

    /**
     * Parse odds from text
     */
    public static double parseOdds(String oddsText) {
        try {
            String cleaned = oddsText.replaceAll("[^0-9.]", "");
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse odds: {}", oddsText);
            return 0.0;
        }
    }


    /**
     * Random human-like delay
     */
    private static void randomHumanDelay(int minMs, int maxMs) {
        try {
            int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs + 1);
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