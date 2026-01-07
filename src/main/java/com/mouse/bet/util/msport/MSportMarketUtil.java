package com.mouse.bet.util.msport;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;

import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.enums.MarketType;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.model.MarketBlockResult;
import com.mouse.bet.service.ArbOutcomeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.mouse.bet.util.msport.MSportMarketSearchUtils.*;


/**
 * MSport Market Utility - Handles all market operations
 * Extracted from MSportWindow for better code organization
 */
@Slf4j
public class MSportMarketUtil {

    private static final String EMOJI_MARKET = "📈";
    private static final String EMOJI_TARGET = "🎯";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_SEARCH = "🔍";
    private static final String EMOJI_CART = "🛒";
    private static final String EMOJI_MONEY = "💰";
    private static final String EMOJI_TRASH = "🗑️";
    private static final String EMOJI_BET = "🎯";

    private static final double TOLERANCE_PERCENT = 0.03; // 3% tolerance
    private static final BookMaker BOOK_MAKER = BookMaker.MSPORT;

    /**
     * Find and expand market by title
     * Uses MSportMarketSearchUtils for robust market finding
     */
    public static boolean findMarket(Page page, BettingTask task) throws Exception {
        log.info("{} {} Searching for market: {}", EMOJI_MARKET, EMOJI_SEARCH, task.getMarketType());

        try {
            // Use the utility class to find and expand markets
            List<MarketBlockResult> marketBlocks = findAndExpandMarkets(
                    page, task.getMarketType()
            );

            if (marketBlocks == null || marketBlocks.isEmpty()) {
                log.warn("{} {} Market '{}' not found",
                        EMOJI_WARNING, EMOJI_MARKET, task.getMarketType());
                return false;
            }

            log.info("{} {} Market '{}' found and expanded successfully ({} block(s))",
                    EMOJI_SUCCESS, EMOJI_MARKET, task.getMarketType(), marketBlocks.size());
            return true;

        } catch (Exception e) {
            log.error("{} {} Error finding market '{}': {}",
                    EMOJI_ERROR, EMOJI_MARKET, task.getMarketType(), e.getMessage());
            throw e;
        }
    }

    /**
     * Select outcome within the market
     */
    public static void selectOutcome(Page page, BettingTask task) throws Exception {
        log.info("{} {} Selecting outcome: {} with odds: {}",
                EMOJI_TARGET, EMOJI_BET, task.getOutcome(), task.getExpectedOdds());

        try {
            String market = task.getMarketType();
            String outcome = task.getOutcome();

            // Find and expand markets
            List<MarketBlockResult> marketBlocks = findAndExpandMarkets(
                    page, market
            );

            if (marketBlocks == null || marketBlocks.isEmpty()) {
                throw new Exception("Market not found: " + market);
            }

            // Detect market type
            MarketType marketType = detectMarketType(market, outcome);
            log.info("Detected market type: {}", marketType);

            // Select outcome based on market type
            Locator outcomeCell = selectOutcomeByType(
                    marketBlocks, marketType, outcome, page
            );

            if (outcomeCell == null) {
                throw new Exception("Outcome not found: " + outcome);
            }

            // Verify outcome is not disabled
            if (isOutcomeDisabled(outcomeCell)) {
                throw new Exception("Outcome '" + outcome + "' is currently disabled/locked");
            }

            // Extract and verify odds
            String displayedOdds = extractOdds(outcomeCell, marketType);
            if (displayedOdds == null) {
                throw new Exception("No odds found for outcome: " + outcome);
            }

            log.info("FOUND: {} → {} @ {}", market, outcome, displayedOdds);

            // Verify odds are acceptable
            if (!isOddsAcceptable(task.getExpectedOdds(), displayedOdds)) {
                log.warn("Odds drifted: expected {} → got {}", task.getExpectedOdds(), displayedOdds);
                // Continue anyway - odds check can be done in placeBet
            }

            // Click outcome with human-like behavior
            clickOutcome(outcomeCell, market, outcome, displayedOdds);

            log.info("{} {} Outcome selected successfully", EMOJI_SUCCESS, EMOJI_TARGET);

        } catch (Exception e) {
            log.error("{} {} Failed to select outcome: {}", EMOJI_ERROR, EMOJI_TARGET, e.getMessage());
            throw e;
        }
    }

    /**
     * Verify that the bet appears correctly in the betslip
     */
    public static boolean verifyBetslip(Page page, BettingTask task) throws Exception {
        log.info("{} {} Verifying bet in betslip...", EMOJI_CART, EMOJI_SEARCH);

        try {
            // Wait for betslip to update
            page.waitForTimeout(500);

            // Check bet count
            String countText = page.locator("#target-betslip .m-count-ball")
                    .first()
                    .textContent()
                    .trim();

            if ("0".equals(countText) || countText.isEmpty()) {
                log.warn("{} {} Betslip is empty", EMOJI_WARNING, EMOJI_CART);
                return false;
            }

            // Get all selections in betslip
            List<ElementHandle> selections = page.locator("div.m-bet-selection")
                    .elementHandles();

            if (selections.isEmpty()) {
                log.warn("{} {} No selections found in betslip", EMOJI_WARNING, EMOJI_CART);
                return false;
            }

            String normalizedMarket = normalizeText(task.getMarketType());
            String normalizedOutcome = normalizeText(task.getOutcome());

            // Check each selection
            for (ElementHandle selectionHandle : selections) {
                try {
                    String marketTitle = selectionHandle.querySelector("span.market-title")
                            .textContent().trim();
                    String selectionMarket = selectionHandle.querySelector("div.selection-market")
                            .textContent().trim();
                    String odds = selectionHandle.querySelector("span.m-betslip-odds span")
                            .textContent().trim();

                    String normalizedActualMarket = normalizeText(selectionMarket);
                    String normalizedActualOutcome = normalizeText(marketTitle);

                    boolean marketMatches = normalizedActualMarket.contains(normalizedMarket)
                            || normalizedMarket.contains(normalizedActualMarket);
                    boolean outcomeMatches = normalizedActualOutcome.contains(normalizedOutcome)
                            || normalizedOutcome.contains(normalizedActualOutcome);

                    if (marketMatches && outcomeMatches) {
                        log.info("{} {} Bet verified in betslip: {} → {} @ {}",
                                EMOJI_SUCCESS, EMOJI_CART, selectionMarket, marketTitle, odds);
                        return true;
                    }

                } catch (Exception innerEx) {
                    log.debug("Error reading selection details: {}", innerEx.getMessage());
                }
            }

            log.warn("{} {} Bet NOT found in betslip | Expected: {} | Market: {}",
                    EMOJI_WARNING, EMOJI_CART, task.getOutcome(), task.getMarketType());
            return false;

        } catch (Exception e) {
            log.error("{} {} Failed to verify betslip: {}", EMOJI_ERROR, EMOJI_CART, e.getMessage());
            return false;
        }
    }

    /**
     * Place the bet from betslip
     * This is a simplified version - you should implement the full logic from your MSportWindow
     */
    public static boolean placeBet(Page page, BettingTask bettingTask, ArbOutcomeService arbOutcomeService) {

        long startTime = System.currentTimeMillis();

        // ── CONFIGURABLE MAX DURATION ──
        final long MAX_DURATION_MS = 10 * 60 * 1000L; // 12 minutes — change as needed
        final long deadline = startTime + MAX_DURATION_MS;

        log.info("─────────────────────────────────────────────────────────────");
        log.info("START placeBet → {} → {} @ {} | Stake: {} | {}",
                bettingTask.getMarketType(),
                bettingTask.getOutcome(),
                bettingTask,
                bettingTask.getStakeAmount(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));

        try {
            // ── 1. ENTER STAKE ──
            log.info("{} [1/5] Entering stake...", EMOJI_BET);
            if (!enterStakeWithOverflowHandling(page, Objects.requireNonNull(arbOutcomeService.findByExternalIdAndBookmaker("", BOOK_MAKER.getBreakingBetId()).orElse(null)).getStake())) {
                log.error("{} Failed to enter stake", EMOJI_ERROR);
                return false;
            }
            randomHumanDelay(150, 300);
            log.info("[OK] Stake entered");

            // ── 2. WAIT FOR BET IN SLIP ──
            page.locator("#target-betslip .m-selections-list .m-bet-selection")
                    .first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(8000));
            log.info("[OK] Bet appeared in slip");

            // ── 3. DISABLE AUTO-ACCEPT ODDS CHANGES ──
            log.info("[3/5] Disabling auto-accept odds changes...");
            disableAcceptOddsChanges(page);

            // ── 4. MAIN PLACEMENT LOOP (NOW TIME-BASED) ──
            log.info("[4/5] Starting optimized placement loop...");

            boolean success = false;
            final long MAX_WAIT_FOR_RECOVERY = 30_000; // 30 seconds
            long waitStartTime = 0;

            // Your robust JS monitor (kept exactly as-is)
            String jsMonitor = """
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

            while (!success && System.currentTimeMillis() < deadline) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                log.info("[Elapsed: {}s / {}s max] Checking state...", elapsedMs / 1000, MAX_DURATION_MS / 1000);

                @SuppressWarnings("unchecked")
                Map<String, Object> state = (Map<String, Object>) page.evaluate(jsMonitor);

                String status = (String) state.getOrDefault("status", "NO_SELECTION");

                if ("NO_SELECTION".equals(status)) {
                    log.warn("Bet selection disappeared from slip");
                    return false;
                }
                if ("UNAVAILABLE".equals(status)) {

                    log.error("Market UNAVAILABLE - match likely over");
                    clearBetSlip(page);
                    return false;
                }
                if ("SUSPENDED".equals(status)) {
                    if (waitStartTime == 0) {
                        waitStartTime = System.currentTimeMillis();
                        log.warn("Market SUSPENDED - waiting for recovery...");
                    }
                    long waited = System.currentTimeMillis() - waitStartTime;
                    if (waited > MAX_WAIT_FOR_RECOVERY) {
                        log.error("Suspended too long ({}ms) - aborting", waited);
                        clearBetSlip(page);
                        return false;
                    }
                    log.info("Suspended... waiting ({}ms / {}ms)", waited, MAX_WAIT_FOR_RECOVERY);
                    randomHumanDelay(500, 900);
                    continue;
                }

                String currentOddsText = (String) state.get("oddsText");
                String buttonText = (String) state.getOrDefault("buttonText", "");
                boolean buttonDisabled = Boolean.TRUE.equals(state.get("buttonDisabled"));
                double expectedOdds = Objects.requireNonNull(arbOutcomeService.findByExternalIdAndBookmaker("", BOOK_MAKER.getBreakingBetId())
                        .orElse(null)).getOdds().doubleValue();

                log.info("Button: \"{}\" | Disabled: {} | Current-Odds: {} | expected-Odds: {} |", buttonText, buttonDisabled, currentOddsText, expectedOdds);

                // ── ODDS CHECK ──
                if (currentOddsText == null || !isOddsAcceptable(expectedOdds, currentOddsText)) {
                    if (waitStartTime == 0) {
                        waitStartTime = System.currentTimeMillis();
                        log.warn("Odds UNFAVORABLE: {} (need ≥ {}) - waiting...", currentOddsText, expectedOdds);
                    }
                    long waited = System.currentTimeMillis() - waitStartTime;
                    if (waited > MAX_WAIT_FOR_RECOVERY) {
                        log.error("Odds not recovering after {}ms - aborting", waited);
                        clearBetSlip(page);
                        return false;
                    }
                    randomHumanDelay(200, 500);
                    continue;
                }
                if (waitStartTime > 0) {
                    log.info("✓ Odds RECOVERED → {} ✓", currentOddsText);
                    waitStartTime = 0;
                }

                // ── BUTTON DISABLED ──
                if (buttonDisabled) {
                    log.info("Place bet button disabled → waiting...");
                    randomHumanDelay(200, 500);
                    continue;
                }

                String btnLower = buttonText.toLowerCase();

                // ── STEP 1: HANDLE "ACCEPT CHANGES" FIRST ──
                if (btnLower.contains("accept changes")) {
                    log.info("→ CLICKING 'Accept Changes' @ {}", currentOddsText);
                    clickPlaceButton(page);
                    randomHumanDelay(200, 500);
                    disableAcceptOddsChanges(page); // re-disable after manual accept
                    continue;
                }

                // ── STEP 2: RE-ENTER STAKE BEFORE FINAL PLACE BET ──
                if (!enterStakeWithOverflowHandling(page, Objects.requireNonNull(arbOutcomeService.findByExternalIdAndBookmaker("", BOOK_MAKER.getBreakingBetId()).
                        orElse(null)).getStake())){
                    log.warn("Failed to re-enter stake before Place Bet → will retry");
                    randomHumanDelay(500, 800);
                    continue;
                }
                randomHumanDelay(200, 400);

                // ── STEP 3: NOW CLICK PLACE/SUBMIT ──
                if (btnLower.contains("place bet") || btnLower.contains("place") || btnLower.contains("submit")) {
                    log.info("→ CLICKING '{}' button @ {} ✓", buttonText, currentOddsText);
                    clickPlaceButton(page);
                    randomHumanDelay(200, 500);

//                    boolean postClickSuccess = waitForPostClickOutcome(page, 15_000);
//                    if (postClickSuccess) {
//                        log.info("{} BET PLACED SUCCESSFULLY!", EMOJI_SUCCESS);
//                        handleSuccessModal(page);
//                        success = true;
//                        break; todo: this actually delay time to meet the required odd
//                    }
                    continue; // retry loop
                }

                // ── UNKNOWN STATE ──
                log.warn("Unknown button text: '{}' - waiting...", buttonText);
                randomHumanDelay(400, 700);
            }

            if (!success) {
                log.error("{} FAILED to place bet → Timeout after {} minutes", EMOJI_ERROR, MAX_DURATION_MS / 60000);
                clearBetSlip(page);
                return false;
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[OK] BET PLACED SUCCESSFULLY | {}ms", duration);
            log.info("─────────────────────────────────────────────────────────────");
            return true;

        } catch (Exception e) {
            log.error("{} FATAL ERROR in placeBet(): {}", EMOJI_ERROR, e.toString());
            e.printStackTrace();
            closeSuccessModal(page);
            return false;
        }
    }

    // ── Helper: Click the main place button ──
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

    private static boolean enterStakeWithOverflowHandling(Page page, BigDecimal stakeAmount) {
        String stakeString = stakeAmount.toPlainString();
        int attempts = 0;
        final int MAX_ATTEMPTS = 3;

        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            log.info("Attempt {}/{} to enter stake: {}", attempts, MAX_ATTEMPTS, stakeString);

            try {
                // STRATEGY 1: Try the original selector first
                Locator stakeInput = findStakeInput(page, attempts);

                if (stakeInput != null && stakeInput.count() > 0) {
                    log.info("Found stake input using strategy {}", attempts);

                    // Scroll and focus with overflow handling
                    scrollAndFocusWithOverflowFix(stakeInput, page);

                    // Clear and enter stake
                    stakeInput.clear();
                    randomHumanDelay(150, 400);
                    MSportLoginUtil.typeFastHumanLike(stakeInput, stakeString);

                    // Verify entry
                    page.waitForTimeout(500);
                    String enteredValue = stakeInput.inputValue();
                    if (enteredValue.equals(stakeString)) {
                        log.info("Stake successfully entered: {}", enteredValue);

                        // Try to trigger update by pressing Enter
                        try {
                            stakeInput.press("Enter");
                        } catch (Exception e) {
                            // Press Tab instead if Enter fails
                            stakeInput.press("Tab");
                        }

                        return true;
                    } else {
                        log.warn("Stake mismatch. Expected: {}, Got: {}", stakeString, enteredValue);
                    }
                }

                // Wait before retry
                randomHumanDelay(100, 200);

            } catch (Exception e) {
                log.warn("Attempt {} failed: {}", attempts, e.getMessage());

                // Apply overflow fixes between attempts
                if (attempts < MAX_ATTEMPTS) {
                    applyOverflowFixes(page);
                    randomHumanDelay(1000, 1500);
                }
            }
        }

        log.error("All {} attempts to enter stake failed", MAX_ATTEMPTS);
        return false;
    }

    /**
     * Find stake input with multiple selector strategies
     */
    private static Locator findStakeInput(Page page, int attempt) {
        page.waitForSelector("aside.aside-betslip-cashout",
                new Page.WaitForSelectorOptions().setTimeout(10000));

        switch (attempt) {
            case 3:
                return withLocatorRetry(page,
                        "div.m-bet-selection >> div.m-single-input-wrap >> input[placeholder='min. 10']",
                        loc -> loc.first(),
                        2, 3000, 500);

            case 2:
                return withLocatorRetry(page,
                        "div.m-bet-selection .bet-input input[placeholder='min. 10']",
                        loc -> loc.first(),
                        2, 3000, 500);

            case 1:
                Locator singlesInput = withLocatorRetry(page,
                        "div.m-mutiple-edit .bet-input input",
                        loc -> loc.count() > 0 ? loc.first() : null,
                        2, 3000, 500);

                if (singlesInput != null) {
                    log.info("Using Singles section input as fallback");
                    return singlesInput;
                }
                return withLocatorRetry(page,
                        "input[placeholder*='min']",
                        loc -> loc.first(),
                        2, 3000, 500);

            default:
                return withLocatorRetry(page,
                        "input[placeholder*='min'], input[placeholder*='Min']",
                        loc -> loc.first(),
                        2, 3000, 500);
        }
    }


    /**
     * Scroll and focus with overflow handling
     */
    private static void scrollAndFocusWithOverflowFix(Locator element, Page page) {
        try {
            // First try normal scroll
            element.scrollIntoViewIfNeeded();
            page.waitForTimeout(300);

            // Check if element is actually visible
            boolean isVisible = element.isVisible(new Locator.IsVisibleOptions().setTimeout(1000));

            if (!isVisible) {
                log.warn("Element not visible after scroll, applying overflow fixes...");

                // Fix CSS overflow issues
                page.evaluate("""
                () => {
                    // Fix aside overflow
                    const aside = document.querySelector('aside.aside-right');
                    if (aside) {
                        aside.style.overflow = 'visible';
                        aside.style.position = 'relative';
                        aside.style.zIndex = '9999';
                    }
                    
                    // Fix betslip container
                    const betslip = document.querySelector('.aside-betslip-cashout');
                    if (betslip) {
                        betslip.style.overflow = 'visible';
                        betslip.style.maxHeight = 'none';
                    }
                    
                    // Fix scroll container
                    const scrollContainer = document.querySelector('.scroll-container--betslip');
                    if (scrollContainer) {
                        scrollContainer.style.overflow = 'visible';
                        scrollContainer.style.maxHeight = 'none';
                    }
                }
            """);

//                page.waitForTimeout(500);

                // Scroll again with force
                element.evaluate("el => el.scrollIntoView({ behavior: 'instant', block: 'center', inline: 'center' })");
//                page.waitForTimeout(300);
            }

            // Focus the element
            element.focus();
            page.waitForTimeout(200);

        } catch (Exception e) {
            log.debug("Scroll/focus failed, using force: {}", e.getMessage());
            // Use force options as last resort
            element.click(new Locator.ClickOptions().setForce(true));
        }
    }

    /**
     * Apply CSS fixes for overflow issues
     */
    private static void applyOverflowFixes(Page page) {
        try {
            page.evaluate("""
            () => {
                // Remove all overflow restrictions in betslip
                const selectors = [
                    'aside.aside-right',
                    '.aside-betslip-cashout',
                    '.scroll-container--betslip',
                    '.m-main-betslip--main-wrap',
                    '.m-bet-selection',
                    '.m-single-input-wrap'
                ];
                
                selectors.forEach(selector => {
                    document.querySelectorAll(selector).forEach(el => {
                        el.style.overflow = 'visible';
                        el.style.position = 'relative';
                        el.style.zIndex = '9999';
                        el.style.maxHeight = 'none';
                    });
                });
                
                // Ensure inputs are visible
                document.querySelectorAll('input').forEach(input => {
                    input.style.visibility = 'visible';
                    input.style.opacity = '1';
                    input.style.display = 'block';
                });
            }
        """);

            log.info("Applied CSS overflow fixes");

        } catch (Exception e) {
            log.debug("Could not apply overflow fixes: {}", e.getMessage());
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

            String betCountText = page.evaluate("""
    () => {
        const badge = document.querySelector('#target-betslip .m-count-ball');
        return badge ? badge.textContent.trim() : '0';
    }
    """).toString();

            boolean wasEmpty = "0".equals(betCountText) || betCountText.isEmpty();
            if (wasEmpty) {
                log.info("{} Betslip already empty", EMOJI_SUCCESS);
                return true;
            }

            log.info("{} Clearing {} selection(s)...", EMOJI_TRASH, betCountText);

            page.evaluate("""
        () => {
            const closeButtons = document.querySelectorAll('#target-betslip .m-bet-selection .m-close-btn');
            closeButtons.forEach(btn => btn.click());
        }
    """);
            page.waitForTimeout(800);

            String finalCount = page.evaluate("""
    () => {
        const badge = document.querySelector('#target-betslip .m-count-ball');
        return badge ? badge.textContent.trim() : '0';
    }
    """).toString();

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

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if outcome is disabled
     */
    private static boolean isOutcomeDisabled(Locator outcomeCell) {
        try {
            String className = outcomeCell.getAttribute("class");
            int disabledIconCount = outcomeCell.locator("i[aria-label='disabled']").count();

            return (className != null && className.contains("disabled")) || disabledIconCount > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract odds from outcome cell
     */
    private static String extractOdds(Locator outcomeCell, MarketType marketType) {
        try {
            Locator oddsElement = outcomeCell.locator(".odds");
            if (oddsElement.count() == 0) {
                return null;
            }
            return oddsElement.textContent().trim();
        } catch (Exception e) {
            log.error("Error extracting odds: {}", e.getMessage());
            return null;
        }
    }


    /**
     * Check if odds are acceptable within tolerance
     */
    private static boolean isOddsAcceptable(double expectedOdds, String displayedOddsStr) {
        if (displayedOddsStr == null || displayedOddsStr.trim().isEmpty()) {
            return false;
        }

        try {
            double displayedOdds = Double.parseDouble(displayedOddsStr.trim());

            if (expectedOdds <= 0) {
                return false;
            }

            double lowerBound = expectedOdds * (1 - TOLERANCE_PERCENT);
            double upperBound = expectedOdds * (1 + TOLERANCE_PERCENT);

            return displayedOdds >= lowerBound && displayedOdds <= upperBound;

        } catch (NumberFormatException e) {
            log.warn("Could not parse odds: '{}'", displayedOddsStr);
            return false;
        }
    }

    /**
     * Normalize text for flexible matching
     */
    private static String normalizeText(String text) {
        if (text == null) return "";

        String normalized = text.toLowerCase().trim();
        normalized = normalized.replaceAll("^\\d+(st|nd|rd|th)\\s+(game|set|map|period)\\s*-\\s*", "");
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized;
    }


    public static boolean selectAndVerifyBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        String market = task.getMarketType();    // e.g. "Winner", "O/U Total Points", "Point Handicap"
        String outcome = task.getOutcome();     // e.g. "Home", "Over 76.5", "+2.5"

        try {
            log.info("Selecting: {} → {}", market, outcome);

            // Find and expand market block
            List<MarketBlockResult> marketBlock = findAndExpandMarkets(page, market);
            if (marketBlock == null) {
                return false;
            }

            // Detect market type and use appropriate selection strategy
            MarketType marketType = detectMarketType(market, outcome);
            log.info("Detected market type: {}", marketType);

            // Select outcome based on market type
            Locator outcomeCell = selectOutcomeByType(marketBlock, marketType, outcome, page);
            if (outcomeCell == null) {
                logAvailableOutcomes(marketBlock, marketType, page);
                return false;
            }

            // Verify outcome is not disabled
            if (isOutcomeDisabled(outcomeCell)) {
                log.warn("Outcome '{}' is currently disabled/locked", outcome);
                return false;
            }

            // Extract and verify odds
            String displayedOdds = extractOdds(outcomeCell, marketType); //todo: get fresh betleg
            if (displayedOdds == null) {
                log.warn("No odds found for outcome '{}'", outcome);
                return false;
            }

            log.info("FOUND: {} → {} @ {}", market, outcome, displayedOdds);

            // Optional: verify odds tolerance
            if (!isOddsAcceptable(Objects.requireNonNull(arbOutcomeService.findByExternalIdAndBookmaker("", BOOK_MAKER.getBreakingBetId()).orElse(null)).getBookmakerId().doubleValue(), displayedOdds)) {
                log.warn("Odds drifted: expected {} → got {}", task.getExpectedOdds(), displayedOdds);
//                return false; todo: enable this
            }

            // Human-like interaction and click
            if (!clickOutcome(outcomeCell, market, outcome, displayedOdds)) {
                return false;
            }

            // Verify bet was added to betslip
            if (!verifyBetInBetslip(page, market, outcome)) {
                log.warn("Bet may not have been added to betslip");
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("FATAL: Failed to select {} → {}", market, outcome, e);
            return false;
        }
    }


    // Click outcome with human-like behavior
    private static boolean clickOutcome(Locator outcomeCell, String market, String outcome, String odds) {
        try {
            outcomeCell.scrollIntoViewIfNeeded();
            randomHumanDelay(100, 200);

            try {
                outcomeCell.click(new Locator.ClickOptions().setTimeout(10000));
            } catch (Exception e) {
                log.warn("Direct click failed, trying JS click");
                outcomeCell.evaluate("el => el.click()");
            }

            log.info("CLICKED: {} → {} @ {}", market, outcome, odds);
            randomHumanDelay(300, 500);
            return true;

        } catch (Exception e) {
            log.error("Failed to click outcome: {}", e.getMessage());
            return false;
        }
    }


    // Log available outcomes for debugging
    private static void logAvailableOutcomes(List<MarketBlockResult> marketBlock,
                                      MarketType marketType, Page page) {
        try {
            if (marketType == MarketType.OVER_UNDER || marketType == MarketType.POINT_HANDICAP) {
                logHandicapOrOverUnderOutcomes(marketBlock, marketType, page);
            } else {
//                logStandardOutcomes(marketBlock);
            }
        } catch (Exception e) {
            log.debug("Could not log available outcomes: {}", e.getMessage());
        }
    }

    // Log outcomes in tabular format for Handicap and Over/Under markets
    private static void logHandicapOrOverUnderOutcomes(List<MarketBlockResult> marketResults,
                                                MarketType marketType, Page page) {
        if (marketResults == null || marketResults.isEmpty()) {
            log.warn("No market blocks available to log {} outcomes", marketType);
            return;
        }

        log.warn("Available {} outcomes across {} market block(s):", marketType, marketResults.size());
        log.warn(" " + "=".repeat(80));

        for (int blockIdx = 0; blockIdx < marketResults.size(); blockIdx++) {
            MarketBlockResult result = marketResults.get(blockIdx);

            log.warn("Block {} of {}: '{}'", blockIdx + 1, marketResults.size(), result.title);

            try {
                // Refresh the block to avoid stale elements
                Locator freshBlock = result.refresh(page);

                if (freshBlock.count() == 0) {
                    log.warn(" Block {} disappeared (DOM refresh?) — skipping debug log", blockIdx + 1);
                    continue;
                }

                // Get column headers
                List<String> headers = freshBlock
                        .locator(".m-market-row .m-title")
                        .allTextContents()
                        .stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

                if (!headers.isEmpty()) {
                    log.warn("  Columns: {}", String.join(" | ", headers));
                    log.warn("  " + "-".repeat(70));
                }

                // Get data rows
                Locator rows = freshBlock.locator(".m-market-row.m-market-row");
                int rowCount = rows.count();

                if (rowCount == 0) {
                    log.warn("  No data rows found in this block");
                    continue;
                }

                for (int i = 0; i < rowCount; i++) {
                    Locator row = rows.nth(i);
                    Locator outcomes = row.locator(".m-outcome");
                    int outcomeCount = outcomes.count();

                    List<String> rowData = new ArrayList<>();

                    for (int j = 0; j < outcomeCount; j++) {
                        Locator outcome = outcomes.nth(j);

                        // Description (handicap value or line)
                        String desc = "";
                        Locator descEl = outcome.locator(".desc");
                        if (descEl.count() > 0) {
                            desc = descEl.textContent().trim();
                        }

                        // Odds
                        String odds = "";
                        Locator oddsEl = outcome.locator(".odds");
                        if (oddsEl.count() > 0) {
                            odds = oddsEl.textContent().trim();
                        }

                        // Disabled status
                        boolean disabled = outcome.getAttribute("class") != null &&
                                outcome.getAttribute("class").contains("disabled");
                        String status = disabled ? " [LOCKED]" : "";

                        // Format cell
                        if (!desc.isEmpty() && !odds.isEmpty()) {
                            rowData.add(String.format("%-10s @ %-6s%s", desc, odds, status));
                        } else if (!desc.isEmpty()) {
                            rowData.add(desc + status);
                        } else if (!odds.isEmpty()) {
                            rowData.add(odds + status);
                        }
                    }

                    if (!rowData.isEmpty()) {
                        log.warn("  Row {}: {}", i + 1, String.join(" | ", rowData));
                    }
                }

            } catch (Exception e) {
                log.warn("Error logging outcomes in block {} ('{}'): {}", blockIdx + 1, result.title, e.getMessage());
            }

            log.warn(" " + "=".repeat(80));
        }
    }

    /**
     * Enter stake amount
     */
    private static boolean enterStake(Page page, double stakeAmount) {
        try {
            String stakeString = String.valueOf(stakeAmount);

            Locator stakeInput = page.locator(
                    "div.m-bet-selection >> div.m-single-input-wrap >> input[placeholder='min. 10']"
            ).first();

            stakeInput.scrollIntoViewIfNeeded();
            randomHumanDelay(200, 400);

            stakeInput.clear();
            randomHumanDelay(150, 300);

            stakeInput.fill(stakeString);
            randomHumanDelay(200, 400);

            // Verify entry
            String enteredValue = stakeInput.inputValue();
            if (enteredValue.equals(stakeString)) {
                log.info("Stake entered successfully: {}", enteredValue);
                return true;
            } else {
                log.warn("Stake mismatch. Expected: {}, Got: {}", stakeString, enteredValue);
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to enter stake: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Disable auto-accept odds changes
     */
    private static void disableAcceptOddsChanges(Page page) {
        try {
            Locator checkedCheckbox = page.locator("div.checkbox-square:not(.nochecked)").first();

            if (checkedCheckbox.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                log.info("'Accept odds changes' checkbox is CHECKED → Disabling it");
                checkedCheckbox.click();
                randomHumanDelay(50, 100);
                log.info("'Accept odds changes' DISABLED");
            } else {
                log.info("'Accept odds changes' already disabled");
            }
        } catch (Exception e) {
            log.debug("Could not disable 'Accept odds changes': {}", e.getMessage());
        }
    }

    /**
     * Detect success modal
     */
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

    /**
     * Close success modal
     */
    private static void closeSuccessModal(Page page) {
        try {
            Locator okButton = page.locator(
                    "div.betslip-success--footer button.btn--cancel:has-text('OK'), " +
                            "div.m-betslip-success button:has-text('OK')"
            ).first();

            if (okButton.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                okButton.evaluate("el => el.click()");
                randomHumanDelay(500, 700);
                log.info("✅ Success modal closed");
            }
        } catch (Exception e) {
            log.debug("Could not close success modal: {}", e.getMessage());
        }
    }


    /**
     * Verify that the selected bet appears in the betslip
     */
    private static boolean verifyBetInBetslip(Page page, String market, String outcome) {
        try {
            String countText = withLocatorRetry(page,
                    "#target-betslip .m-count-ball, .m-count-ball-wrap .m-count-ball",
                    loc -> loc.first().textContent().trim(),
                    3, 5000, 1000);

            if ("0".equals(countText) || countText.isEmpty()) {
                log.warn("{} Betslip is empty (count = {})", EMOJI_WARNING, countText);
                return false;
            }

            List<ElementHandle> selections = withLocatorRetry(page, "div.m-bet-selection",
                    loc -> loc.elementHandles(),
                    3, 5000, 1000);

            if (selections == null || selections.isEmpty()) {
                log.warn("{} No selections found in betslip", EMOJI_WARNING);
                return false;
            }

            String normalizedMarket = normalizeText(market);
            String normalizedOutcome = normalizeText(outcome);

            log.debug("Searching betslip for: Market='{}' (normalized: '{}'), Outcome='{}' (normalized: '{}')",
                    market, normalizedMarket, outcome, normalizedOutcome);

            for (ElementHandle selectionHandle : selections) {
                try {
                    String teams = selectionHandle.querySelector(".m-teams").textContent().trim();
                    String marketTitle = selectionHandle.querySelector("span.market-title").textContent().trim();
                    String selectionMarket = selectionHandle.querySelector("div.selection-market").textContent().trim();
                    String odds = selectionHandle.querySelector("span.m-betslip-odds span").textContent().trim();

                    log.debug("Checking selection: Teams='{}' | Market='{}' | Outcome='{}' @ {}",
                            teams, selectionMarket, marketTitle, odds);

                    String normalizedActualMarket = normalizeText(selectionMarket);
                    String normalizedActualOutcome = normalizeText(marketTitle);

                    boolean marketMatches = normalizedActualMarket.contains(normalizedMarket)
                            || normalizedMarket.contains(normalizedActualMarket);
                    boolean outcomeMatches = normalizedActualOutcome.contains(normalizedOutcome)
                            || normalizedOutcome.contains(normalizedActualOutcome);

                    if (marketMatches && outcomeMatches) {
                        log.info("{} ✅ Bet verified in betslip: {} → {} @ {}",
                                EMOJI_SUCCESS, selectionMarket, marketTitle, odds);
                        return true;
                    }

                } catch (Exception innerEx) {
                    log.debug("Error reading selection details: {}", innerEx.getMessage());
                    continue;
                }
            }

            log.warn("{} ❌ Bet NOT found in betslip | Expected: {} | Market: {}",
                    EMOJI_WARNING, outcome, market);
            return false;

        } catch (Exception e) {
            log.error("{} Failed to verify bet in betslip: {}", EMOJI_ERROR, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Random human delay
     */
    private static void randomHumanDelay(long minMs, long maxMs) {
        try {
            long delay = minMs + ThreadLocalRandom.current().nextLong(maxMs - minMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    private static  <T> T withLocatorRetry(Page page, String selector, java.util.function.Function<Locator, T> action,
                                   int maxRetries, long timeoutPerAttemptMs, long delayMs) {
        Locator locator = page.locator(selector);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return action.apply(locator);  // e.g., locator::click, locator::textContent, etc.
            } catch (TimeoutError te) {
                log.warn("Timeout attempt {} on '{}'", attempt, selector);
                if (attempt == maxRetries) throw te;
                page.waitForTimeout(delayMs);
            }
        }
        return null;  // Never reached
    }


}