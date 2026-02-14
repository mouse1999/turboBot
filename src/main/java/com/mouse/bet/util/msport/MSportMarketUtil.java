package com.mouse.bet.util.msport;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;

import com.mouse.bet.converter.ModelConverter;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.enums.MarketType;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.model.MarketBlockResult;
import com.mouse.bet.service.ArbOutcomeService;
import com.mouse.bet.transformation.BookMakerMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    private static final double UPPER_TOLERANCE_PERCENT = 0.4;
    private static final double LOWER__TOLERANCE_PERCENT = 0.1;
    private static final BookMaker BOOK_MAKER = BookMaker.MSPORT;

    /**
     * Find and expand market by title
     * Uses MSportMarketSearchUtils for robust market finding
     */
    public static boolean findMarket(Page page, BettingTask task) throws Exception {
        log.info("{} {} Searching for market: {}", EMOJI_MARKET, EMOJI_SEARCH, task.marketType());

        try {
            // Use the utility class to find and expand markets
            List<MarketBlockResult> marketBlocks = findAndExpandMarkets(
                    page, task.marketType()
            );

            if (marketBlocks == null || marketBlocks.isEmpty()) {
                log.warn("{} {} Market '{}' not found",
                        EMOJI_WARNING, EMOJI_MARKET, task.marketType());
                return false;
            }

            log.info("{} {} Market '{}' found and expanded successfully ({} block(s))",
                    EMOJI_SUCCESS, EMOJI_MARKET, task.marketType(), marketBlocks.size());
            return true;

        } catch (Exception e) {
            log.error("{} {} Error finding market '{}': {}",
                    EMOJI_ERROR, EMOJI_MARKET, task.marketType(), e.getMessage());
            throw e;
        }
    }

    /**
     * Select outcome within the market
     */
    public static void selectOutcome(Page page, BettingTask task) throws Exception {
        log.info("{} {} Selecting outcome: {} with odds: {}",
                EMOJI_TARGET, EMOJI_BET, task.outcome(), task.expectedOdds());

        try {
            String market = task.marketType();
            String outcome = task.outcome();

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
            if (!isOddsAcceptable(task.expectedOdds(), displayedOdds)) {
                log.warn("Odds drifted: expected {} → got {}", task.expectedOdds(), displayedOdds);
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

            String normalizedMarket = normalizeText(task.marketType());
            String normalizedOutcome = normalizeText(task.outcome());

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
                    EMOJI_WARNING, EMOJI_CART, task.outcome(), task.marketType());
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
                bettingTask.marketType(),
                bettingTask.outcome(),
                bettingTask,
                bettingTask.stakeAmount(),
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

            double lowerBound = expectedOdds * (1 - LOWER__TOLERANCE_PERCENT);
            double upperBound = expectedOdds * (1 + UPPER_TOLERANCE_PERCENT);

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
        String market = task.marketType().trim();
        String outcome = task.outcome().trim();

        log.info("Selecting: {} → {}", market, outcome);

        try {
            // Ensure correct market tab is active (MSport has Main, Half, Points, Quarters, etc.)
            if (!ensureCorrectGameTab(page, task)) {
                log.error("Failed to navigate to correct market tab");
                return false;
            }

            // Get fresh task for latest odds from database
            BettingTask freshTask = getFreshTask(task, arbOutcomeService);
            if (freshTask != null) {
                log.info("Using fresh betting task from DB");
                task = freshTask;
            } else {
                log.warn("Could not fetch fresh task, using current task");
            }

            double expectedOdds = task.expectedOdds();

            // ⚡ Use optimized MSport finder with automatic waiting
            MsportMarketOutcomeFinder.OutcomeResult result =
                    MsportMarketOutcomeFinder.findAndClickOutcome(page, market, outcome, expectedOdds);



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
                    log.warn("Odds drifted:- expected {} → got {}", expectedOdds, result.odds);

                    // Strict odds validation - re-fetch fresh task and verify again
                    BettingTask revalidatedTask = getFreshTask(task, arbOutcomeService);
                    if (revalidatedTask != null) {
                        task = revalidatedTask;
                        double latestExpectedOdds = task.expectedOdds();

                        // Check if odds are acceptable with latest expected odds
                        if (!isOddsWithinTolerance(latestExpectedOdds, result.odds)) {
                            log.error("Odds still not acceptable after revalidation: expected {} → got {}",
                                    latestExpectedOdds, result.odds);
                            takeMarketScreenshot(page, "odds-rejected-" + safeFileName(market + "-" + outcome));
                            // TODO: Uncomment to enable strict odds rejection
                             return false;
                        } else {
                            log.info("Odds acceptable after revalidation: expected {} → got {}",
                                    latestExpectedOdds, result.odds);
                        }
                    }
                }

                if ("Click failed".equals(result.errorMessage)) {
                    log.error("Failed to click outcome element");
                    takeMarketScreenshot(page, "click-failed-" + safeFileName(market + "-" + outcome));
                    return false;
                }

                takeMarketScreenshot(page, "failed-" + safeFileName(market + "-" + outcome));
                return false;
            }

//            Thread.sleep(15000);

            // Verify outcome match (sanity check)
            if (!isOutcomeMatchValid(result.outcomeText, outcome)) {
                log.warn("Outcome mismatch: expected '{}' → got '{}'", outcome, result.outcomeText);
                takeMarketScreenshot(page, "mismatch-" + safeFileName(market + "-" + outcome));
                return false;
            }

            log.info("FOUND:- {} → {} @ {}", result.marketTitle, result.outcomeText, result.odds);

            randomHumanDelay(200, 400);


            // Verify bet slip
            if (!verifyBetInBetslip(page,market, outcome)) {
                log.error("Bet slip verification failed");
                takeMarketScreenshot(page, "betslip-failed-" + safeFileName(market + "-" + outcome));
                return false;
            }

            log.info("✅ CLICKED: {} → {} @ {}", result.marketTitle, result.outcomeText, result.odds);
            return true;

        } catch (Exception e) {
            log.error("FATAL: Failed to select {} → {} | Error: {}", market, outcome, e.getMessage(), e);
            takeMarketScreenshot(page, "error-" + safeFileName(market + "-" + outcome));
            return false;
        }
    }

    /**
     * Ensure the correct market tab is active for MSport
     * MSport has tabs like: Main, Bet Builder, Half, Points, Quarters, Specials, Game
     *
     * Tab selection logic:
     * - "1st Half", "2nd Half" -> Half tab
     * - "1st Quarter", "2nd Quarter", "3rd Quarter", "4th Quarter" -> Quarters tab
     * - "1st Game", "2nd Game" -> Game tab
     * - "Points", "1st Points", "Total Points" -> Points tab
     * - If no match -> remain on Main tab
     */

//    Sport sport = Sport.fromDisplayName(task.sport());
//            log.info("sport found: {}", sport);
    private static boolean ensureCorrectGameTab(Page page, BettingTask task) {
        try {
            String market = task.marketType().trim();
            Sport sport = Sport.fromDisplayName(task.sport());
            log.info("sport found: {}", sport);
            String targetTab = determineTabFromMarket(market, sport);

            log.info("Market: '{}' → Target tab: '{}'", market, targetTab);

            // WAIT for the tab navigation to be present before executing JavaScript
            try {
                page.waitForSelector(".m-sub-navs-wrapper ul.snap-nav",
                        new Page.WaitForSelectorOptions().setTimeout(1000));
                randomHumanDelay(200, 400); // Give it a moment to fully render
            } catch (Exception e) {
                log.warn("Tab navigation not found after waiting: {}", e.getMessage());
//                return true; // Continue anyway
            }

            String jsEnsureTab = """
(targetTabName) => {
    // Try multiple selectors to find the tab navigation
    let tabContainer = document.querySelector('.m-sub-navs-wrapper ul.snap-nav');
    
    if (!tabContainer) {
        tabContainer = document.querySelector('.snap-nav-wrap ul.snap-nav');
    }
    
    if (!tabContainer) {
        tabContainer = document.querySelector('ul.snap-nav');
    }
    
    if (!tabContainer) {
        // Try to find any ul with snap-nav class
        tabContainer = document.querySelector('.m-detail-markets ul[class*="snap-nav"]');
    }
    
    if (!tabContainer) {
        return { 
            success: false, 
            reason: 'Tab navigation not found',
            debug: {
                hasDetailMarkets: !!document.querySelector('.m-detail-markets'),
                hasSubNavsWrapper: !!document.querySelector('.m-sub-navs-wrapper'),
                hasSnapNavWrap: !!document.querySelector('.snap-nav-wrap')
            }
        };
    }
    
    // Find all tab items
    const tabItems = tabContainer.querySelectorAll('li.m-sub-nav-item');
    
    if (tabItems.length === 0) {
        return {
            success: false,
            reason: 'No tab items found',
            debug: {
                containerFound: true,
                tabItemsCount: 0
            }
        };
    }
    
    const availableTabs = [];
    let foundTab = null;
    let currentActiveTab = null;
    
    for (const tab of tabItems) {
        const tabText = tab.querySelector('span.m-group');
        if (tabText) {
            // Clean the text: remove HTML comments, extra whitespace, newlines
            const cleanText = tabText.textContent
                .replace(/<!---->/g, '')
                .replace(/\\s+/g, ' ')
                .trim();
            
            availableTabs.push(cleanText);
            
            // Track current active tab
            if (tab.classList.contains('active')) {
                currentActiveTab = tab;
            }
            
            // Find target tab
            if (cleanText === targetTabName) {
                foundTab = tab;
            }
        }
    }
    
    if (!foundTab) {
        return { 
            success: false, 
            reason: 'Tab not found: ' + targetTabName,
            availableTabs: availableTabs
        };
    }
    
    // Check if already active
    if (foundTab.classList.contains('active')) {
        return { success: true, alreadyActive: true, tabName: targetTabName };
    }
    
    // Remove active from current tab and add to target tab
    if (currentActiveTab) {
        currentActiveTab.classList.remove('active');
    }
    
    foundTab.classList.add('active');
    
    // Click the tab to trigger any event listeners
    foundTab.click();
    
    return { 
        success: true, 
        alreadyActive: false, 
        tabName: targetTabName,
        previousTab: currentActiveTab ? currentActiveTab.querySelector('span.m-group').textContent.trim() : null
    };
}
""";

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsEnsureTab, targetTab);

            if (result == null) {
                log.error("JavaScript execution returned null");
                return false;
            }

            boolean success = (Boolean) result.getOrDefault("success", false);

            if (!success) {
                String reason = (String) result.get("reason");
                @SuppressWarnings("unchecked")
                List<String> availableTabs = (List<String>) result.get("availableTabs");
                @SuppressWarnings("unchecked")
                Map<String, Object> debug = (Map<String, Object>) result.get("debug");

                log.warn("{}", reason);
                if (debug != null) {
                    log.warn("Debug info: {}", debug);
                }
                if (availableTabs != null && !availableTabs.isEmpty()) {
                    log.warn("Available tabs: {}", availableTabs);
                    log.warn("Looking for: '{}'", targetTab);
                }
                return true; // Continue even if tab not found
            }

            boolean alreadyActive = (Boolean) result.getOrDefault("alreadyActive", false);
            String tabName = (String) result.get("tabName");

            if (alreadyActive) {
                log.info("Tab '{}' is already active", tabName);
            } else {
                String previousTab = (String) result.get("previousTab");
                log.info("Switched from '{}' to '{}' tab", previousTab, tabName);
                randomHumanDelay(300, 600);

                // Wait for markets to load
                page.waitForSelector(".m-market-item", new Page.WaitForSelectorOptions().setTimeout(5000));
                randomHumanDelay(200, 400);
            }

            return true;

        } catch (Exception e) {
            log.error("Error ensuring correct game tab: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Determine which tab to use based on market name
     *
     * @param market The market type (e.g., "1st Half Winner", "Total Points O/U", "2nd Quarter Handicap")
     * @return Tab name (Main, Half, Points, Quarters, Game, Specials)
     */
    private static String determineTabFromMarket(String market, Sport sport) {
        String marketLower = market.toLowerCase();

        switch (sport) {
            case BASKETBALL:
                // Basketball tabs: Main, Bet Builder, Half, Points, Quarters, Specials

                // Check for "Half" markets
                if (marketLower.contains("half")) {
                    return "Half";
                }

                // Check for "Quarter" markets
                if (marketLower.contains("quarter")) {
                    return "Quarters";
                }

                // Check for "Points" markets
                // This handles: "Points O/U", "1st Points", "Total Points", "Home O/U", "Away O/U", etc.
                if (marketLower.contains("point") ||
                        marketLower.contains("home o/u") ||
                        marketLower.contains("away o/u") ||
                        marketLower.contains("home total") ||
                        marketLower.contains("away total")) {
                    return "Points";
                }

                // Check for "Specials" markets
                if (marketLower.contains("special") || marketLower.contains("prop")) {
                    return "Specials";
                }

                // Default to Main tab
                return "Main";

            case FOOTBALL:
            case SOCCER:
                // Football tabs: Main, Bet Builder, Goals, Half, Specials, Bookings, Corners, Player, Minutes

                // Check for "Goals" markets
                if (marketLower.contains("goal")) {
                    return "Goals";
                }

                // Check for "Half" markets
                if (marketLower.contains("half")) {
                    return "Half";
                }

                // Check for "Corners" markets
                if (marketLower.contains("corner")) {
                    return "Corners";
                }

                // Check for "Bookings" or "Cards" markets
                if (marketLower.contains("booking") || marketLower.contains("card") ||
                        marketLower.contains("yellow") || marketLower.contains("red")) {
                    return "Bookings";
                }

                // Check for "Player" markets
                if (marketLower.contains("player")) {
                    return "Player";
                }

                // Check for "Minutes" markets
                if (marketLower.contains("minute")) {
                    return "Minutes";
                }

                // Check for "Specials" markets
                if (marketLower.contains("special") || marketLower.contains("prop")) {
                    return "Specials";
                }

                // Default to Main tab
                return "Main";

            case TABLE_TENNIS:
                // Table Tennis tabs: Main, Game

                // Check for "Game" markets
                if (marketLower.contains("game")) {
                    return "Game";
                }

                // Default to Main tab
                return "Main";

            case TENNIS:
                // Tennis tabs: Main, Game (similar to Table Tennis)

                // Check for "Set" markets
                if (marketLower.contains("set")) {
                    return "Set";
                }

                // Check for "Game" markets
                if (marketLower.contains("game")) {
                    return "Game";
                }

                // Default to Main tab
                return "Main";

            default:
                // For any other sport, use generic logic

                // Check for "Half" markets
                if (marketLower.contains("half")) {
                    return "Half";
                }

                // Check for "Quarter" markets
                if (marketLower.contains("quarter")) {
                    return "Quarters";
                }

                // Check for "Game" markets
                if (marketLower.contains("game")) {
                    return "Game";
                }

                // Check for "Points" markets
                if (marketLower.contains("point")) {
                    return "Points";
                }

                // Check for "Specials" markets
                if (marketLower.contains("special") || marketLower.contains("prop")) {
                    return "Specials";
                }

                // Default to Main tab
                return "Main";
        }
    }


    /**
     * Validate outcome match with flexible matching for handicaps
     * Handles variations like "Home (-12.5)" vs "Home -12.5"
     */
    private static boolean isOutcomeMatchValid(String actualOutcome, String expectedOutcome) {
        log.debug("🔍 Validating outcome match:");
        log.debug("   Expected: '{}'", expectedOutcome);
        log.debug("   Actual:   '{}'", actualOutcome);

        if (actualOutcome == null || expectedOutcome == null) {
            log.warn("❌ Outcome match validation failed: null value detected");
            log.warn("   Expected: {}", expectedOutcome);
            log.warn("   Actual:   {}", actualOutcome);
            return false;
        }

        // Direct match
        if (actualOutcome.equalsIgnoreCase(expectedOutcome)) {
            log.info("✅ Outcome match: DIRECT match");
            return true;
        }

        // Normalized match (remove spaces around parentheses and operators)
        String normalizedActual = normalizeOutcome(actualOutcome);
        String normalizedExpected = normalizeOutcome(expectedOutcome);

        log.debug("   Normalized Expected: '{}'", normalizedExpected);
        log.debug("   Normalized Actual:   '{}'", normalizedActual);

        boolean matches = normalizedActual.equalsIgnoreCase(normalizedExpected);

        if (matches) {
            log.info("✅ Outcome match: NORMALIZED match");
        } else {
            log.warn("❌ Outcome match validation FAILED");
            log.warn("   Expected (original):    '{}'", expectedOutcome);
            log.warn("   Actual (original):      '{}'", actualOutcome);
            log.warn("   Expected (normalized):  '{}'", normalizedExpected);
            log.warn("   Actual (normalized):    '{}'", normalizedActual);
        }

        return matches;
    }

    /**
     * Normalize outcome string for comparison
     * Examples:
     *   "Home (-12.5)" -> "home-12.5"
     *   "Over 76.5" -> "over76.5"
     *   "+2.5" -> "+2.5"
     */
    private static String normalizeOutcome(String outcome) {
        return outcome.trim()
                .toLowerCase()
                .replaceAll("[\\s()]+", "")  // Remove spaces and parentheses
                .replaceAll("\\s+", "");      // Remove any remaining whitespace
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

            return ModelConverter.convertFromArbOutcome(
                    arbOutcomeService.findByExternalIdAndBookmaker(
                            currentTask.taskId(),
                            currentTask.bookmakerId()
                    ).orElse(null)
            );
        } catch (Exception e) {
            log.warn("Could not fetch fresh task from DB: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if odds are within acceptable tolerance
     */
    private static boolean isOddsWithinTolerance(double expectedOdds, Double actualOdds) {
        if (actualOdds == null || expectedOdds <= 0) {
            return false;
        }

        double tolerance = 0.003; // 0.3% tolerance
        double lowerBound = expectedOdds * (1 - tolerance);
        double upperBound = expectedOdds * (1 + tolerance);

        return actualOdds >= lowerBound && actualOdds <= upperBound;
    }

    /**
     * Verify bet was added to betslip
     */
    private static boolean verifyBetSlip(Page page, BettingTask task) {
        try {
            // Wait for betslip to update
            randomHumanDelay(300, 500);

            // Check if betslip has items
            Locator betslipItems = page.locator(".betslip-item, .m-betslip-item");

            if (betslipItems.count() == 0) {
                log.error("Betslip is empty after selection");
                return false;
            }

            // Verify the correct bet is in the betslip
            String market = task.marketType().trim();
            String outcome = task.outcome().trim();

            // Look for market or outcome text in betslip
            Locator betslipContent = page.locator(".betslip-content, .m-betslip-content");
            String betslipText = betslipContent.textContent();

            if (betslipText == null || betslipText.isEmpty()) {
                log.warn("Could not read betslip content");
                return true; // Continue anyway
            }

            // Flexible matching
            boolean hasMarket = betslipText.toLowerCase().contains(market.toLowerCase());
            boolean hasOutcome = betslipText.toLowerCase().contains(outcome.toLowerCase());

            if (!hasMarket && !hasOutcome) {
                log.warn("Betslip may not contain expected bet: {} → {}", market, outcome);
                log.debug("Betslip content: {}", betslipText);
                return false;
            }

            log.info("✅ Bet verified in betslip");
            return true;

        } catch (Exception e) {
            log.error("Error verifying betslip: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Take screenshot for debugging
     */
    private static void takeMarketScreenshot(Page page, String filename) {
        try {
            String screenshotDir = "screenshots/msport/";
            Files.createDirectories(Paths.get(screenshotDir));

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fullPath = screenshotDir + timestamp + "_" + filename + ".png";

            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(fullPath)));
            log.info("Screenshot saved: {}", fullPath);
        } catch (Exception e) {
            log.warn("Could not save screenshot: {}", e.getMessage());
        }
    }

    /**
     * Generate safe filename from market and outcome
     */
    private static String safeFileName(String text) {
        return text.replaceAll("[^a-zA-Z0-9.-]", "_");
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
    /**
     * Verify that the selected bet appears in the betslip (fast JS version)
     */
    private static boolean verifyBetInBetslip(Page page, String market, String outcome) {
        String jsVerify = """
    (args) => {
        const { market, outcome } = args;
        
        // Normalize text helper
        const normalize = (text) => {
            return text.toLowerCase()
                .trim()
                .replace(/\\s+/g, ' ')
                .replace(/[^a-z0-9.\\s]/g, '');
        };
        
        const normalizedMarket = normalize(market);
        const normalizedOutcome = normalize(outcome);
        
        // Check betslip count
        const countEl = document.querySelector('#target-betslip .m-count-ball, .m-count-ball-wrap .m-count-ball');
        if (!countEl) {
            return { found: false, error: 'Betslip count element not found' };
        }
        
        const countText = countEl.textContent.trim();
        if (countText === '0' || countText === '') {
            return { found: false, error: 'Betslip is empty (count = ' + countText + ')' };
        }
        
        // Get all selections
        const selections = document.querySelectorAll('div.m-bet-selection');
        if (!selections || selections.length === 0) {
            return { found: false, error: 'No selections found in betslip' };
        }
        
        // Search through selections
        for (let i = 0; i < selections.length; i++) {
            const selection = selections[i];
            
            try {
                const teamsEl = selection.querySelector('.m-teams');
                const marketTitleEl = selection.querySelector('span.market-title');
                const selectionMarketEl = selection.querySelector('div.selection-market');
                const oddsEl = selection.querySelector('span.m-betslip-odds span');
                
                if (!marketTitleEl || !selectionMarketEl) continue;
                
                const teams = teamsEl ? teamsEl.textContent.trim() : '';
                const marketTitle = marketTitleEl.textContent.trim();
                const selectionMarket = selectionMarketEl.textContent.trim();
                const odds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
                
                const normalizedActualMarket = normalize(selectionMarket);
                const normalizedActualOutcome = normalize(marketTitle);
                
                // Check if market matches
                const marketMatches = normalizedActualMarket.includes(normalizedMarket) ||
                                    normalizedMarket.includes(normalizedActualMarket);
                
                // Check if outcome matches
                const outcomeMatches = normalizedActualOutcome.includes(normalizedOutcome) ||
                                     normalizedOutcome.includes(normalizedActualOutcome);
                
                if (marketMatches && outcomeMatches) {
                    return {
                        found: true,
                        teams: teams,
                        market: selectionMarket,
                        outcome: marketTitle,
                        odds: odds
                    };
                }
                
            } catch (err) {
                continue;
            }
        }
        
        return { 
            found: false, 
            error: 'Bet not found in betslip',
            expectedMarket: market,
            expectedOutcome: outcome
        };
    }
    """;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(
                    jsVerify,
                    Map.of("market", market, "outcome", outcome)
            );

            if (result == null) {
                log.error("{} Failed to verify bet: null result", EMOJI_ERROR);
                return false;
            }

            Boolean found = (Boolean) result.get("found");

            if (Boolean.TRUE.equals(found)) {
                log.info("{} ✅ Bet verified in betslip: {} → {} @ {}",
                        EMOJI_SUCCESS,
                        result.get("market"),
                        result.get("outcome"),
                        result.get("odds"));
                return true;
            } else {
                String error = (String) result.get("error");
                log.warn("{} ❌ Bet NOT found in betslip | {}",
                        EMOJI_WARNING,
                        error != null ? error : "Unknown error");
                return false;
            }

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


    public static  <T> T withLocatorRetry(Page page, String selector, java.util.function.Function<Locator, T> action,
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