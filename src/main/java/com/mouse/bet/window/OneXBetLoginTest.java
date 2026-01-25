package com.mouse.bet.window;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class OneXBetLoginTest {

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


    public static void detectOutcomeCoordinates(Page page, String gameUrl) {
        log.info("{} {} === OUTCOME COORDINATE DETECTOR ===", EMOJI_GAME, EMOJI_SEARCH);

        try {
            // Navigate to the specific game page
            log.info("{} {} Navigating to: {}", EMOJI_NAVIGATION, EMOJI_GAME, gameUrl);
            page.navigate(gameUrl, new Page.NavigateOptions()
                    .setTimeout(60000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            Thread.sleep(3000); // Wait for page to fully load

            // Find the canvas
            var canvas = page.locator("canvas.market-grid-canvas__canvas").first();

            if (!canvas.isVisible()) {
                log.error("{} {} Canvas not found!", EMOJI_ERROR, EMOJI_BET);
                return;
            }

            var canvasBox = canvas.boundingBox();
            log.info("=== CANVAS DIMENSIONS ===");
            log.info("Canvas X: {}", canvasBox.x);
            log.info("Canvas Y: {}", canvasBox.y);
            log.info("Canvas Width: {}", canvasBox.width);
            log.info("Canvas Height: {}", canvasBox.height);

            // Find market search input and search for common markets
            String[] testMarkets = {"Match Winner", "1X2", "Total", "Handicap", "Double Chance"};

            for (String marketName : testMarkets) {
                log.info("\n{} {} === Testing Market: '{}' ===", EMOJI_SEARCH, EMOJI_BET, marketName);

                try {
                    // Search for market
                    var searchInput = page.locator("input.ui-search-default__input[placeholder='Search by market']");
                    if (searchInput.isVisible()) {
                        searchInput.click();
                        Thread.sleep(300);
                        searchInput.fill(marketName);
                        Thread.sleep(1500);

                        // Find the market element
                        var marketElement = findMarketElementAfterSearch(page, marketName);

                        if (marketElement != null && marketElement.isVisible()) {
                            marketElement.scrollIntoViewIfNeeded();
                            Thread.sleep(500);

                            var marketBox = marketElement.boundingBox();
                            log.info("Market '{}' found at:", marketName);
                            log.info("  Market X: {}", marketBox.x);
                            log.info("  Market Y: {}", marketBox.y);
                            log.info("  Market Width: {}", marketBox.width);
                            log.info("  Market Height: {}", marketBox.height);

                            double rowY = marketBox.y + (marketBox.height / 2);

                            // Test different X positions across the row
                            log.info("\n  Testing outcome positions (Y = {}):", rowY);

                            // Scan across the canvas width to find clickable areas
                            detectClickableAreas(page, canvasBox, rowY);

                        } else {
                            log.warn("Market '{}' not found", marketName);
                        }

                        // Clear search
                        searchInput.fill("");
                        Thread.sleep(500);
                    }

                } catch (Exception e) {
                    log.error("Error testing market '{}': {}", marketName, e.getMessage());
                }
            }

            // Take a screenshot for reference
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("outcome_detection.png"))
                    .setFullPage(true));
            log.info("{} {} Screenshot saved to: outcome_detection.png", EMOJI_SUCCESS, EMOJI_GAME);

        } catch (Exception e) {
            log.error("{} {} Error during coordinate detection: {}", EMOJI_ERROR, EMOJI_GAME, e.getMessage(), e);
        }
    }

    private static void detectClickableAreas(Page page, com.microsoft.playwright.options.BoundingBox canvasBox, double rowY) {
        // Scan across canvas in 5% increments
        for (int percentile = 50; percentile <= 100; percentile += 5) {
            double xPosition = canvasBox.x + (canvasBox.width * (percentile / 100.0));

            try {
                // Move mouse to position
                page.mouse().move(xPosition, rowY);
                Thread.sleep(100);

                // Check cursor style (pointer = clickable)
                String cursorStyle = (String) page.evaluate(
                        String.format("() => window.getComputedStyle(document.elementFromPoint(%f, %f)).cursor",
                                xPosition, rowY)
                );

                // Get element at position
                String elementInfo = (String) page.evaluate(
                        String.format("() => { " +
                                "const el = document.elementFromPoint(%f, %f); " +
                                "return el ? el.tagName + ' ' + (el.className || '') : 'none'; " +
                                "}", xPosition, rowY)
                );

                // Check if background color changes (might indicate hover state)
                String bgColor = (String) page.evaluate(
                        String.format("() => window.getComputedStyle(document.elementFromPoint(%f, %f)).backgroundColor",
                                xPosition, rowY)
                );

                boolean isClickable = "pointer".equals(cursorStyle);

                if (isClickable) {
                    log.info("  {}% ({}) - CLICKABLE! Cursor: {}, Element: {}, BG: {}",
                            percentile, xPosition, cursorStyle, elementInfo, bgColor);
                } else {
                    log.debug("  {}% ({}) - Cursor: {}, Element: {}",
                            percentile, xPosition, cursorStyle, elementInfo);
                }

            } catch (Exception e) {
                log.debug("  Error at {}%: {}", percentile, e.getMessage());
            }
        }
    }

    // Enhanced version that clicks and shows what happens
    public static void interactiveOutcomeDetection(Page page, String gameUrl) {
        log.info("{} {} === INTERACTIVE OUTCOME DETECTOR ===", EMOJI_GAME, EMOJI_SEARCH);

        try {
            page.navigate(gameUrl, new Page.NavigateOptions()
                    .setTimeout(60000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            Thread.sleep(3000);

            var canvas = page.locator("canvas.market-grid-canvas__canvas").first();
            var canvasBox = canvas.boundingBox();

            // Search for "Match Winner" or "1X2"
            var searchInput = page.locator("input.ui-search-default__input[placeholder='Search by market']");
            searchInput.click();
            Thread.sleep(300);
            searchInput.fill("Match Winner");
            Thread.sleep(1500);

            var marketElement = findMarketElementAfterSearch(page, "Both Teams To Score");

            if (marketElement == null) {
                // Try alternate market name
                searchInput.fill("1X2");
                Thread.sleep(1500);
                marketElement = findMarketElementAfterSearch(page, "1X2");
            }

            if (marketElement != null) {
                marketElement.scrollIntoViewIfNeeded();
                Thread.sleep(500);

                var marketBox = marketElement.boundingBox();
                double rowY = marketBox.y + (marketBox.height / 2);

                log.info("\n=== TESTING OUTCOME CLICKS ===");
                log.info("Market Y position: {}", rowY);
                log.info("Canvas: X={}, Width={}", canvasBox.x, canvasBox.width);

                // Test positions for outcomes 1, X, 2
                double[] testOffsets = {0.70, 0.75, 0.80, 0.85, 0.90, 0.95};
                String[] outcomeLabels = {"Potential 1", "Potential X", "Potential 2"};

                for (int i = 0; i < testOffsets.length; i++) {
                    double xPosition = canvasBox.x + (canvasBox.width * testOffsets[i]);

                    log.info("\n--- Testing {}% offset (X={}) ---", (int)(testOffsets[i] * 100), xPosition);

                    // Count current betslip items before click
                    int betslipCountBefore = getBetslipCount(page);

                    // Move and click
                    page.mouse().move(xPosition, rowY);
                    Thread.sleep(300);
                    page.mouse().click(xPosition, rowY);
                    Thread.sleep(1500);

                    // Check if betslip changed
                    int betslipCountAfter = getBetslipCount(page);

                    if (betslipCountAfter > betslipCountBefore) {
                        log.info("✅ SUCCESS! Bet added at {}% offset!", (int)(testOffsets[i] * 100));
                        log.info("   Betslip count: {} -> {}", betslipCountBefore, betslipCountAfter);

                        // Try to get the bet details from betslip
                        String betDetails = getBetslipDetails(page);
                        log.info("   Bet details: {}", betDetails);

                        // Remove bet to continue testing
                        removeBetFromSlip(page);
                        Thread.sleep(1000);
                    } else {
                        log.info("❌ No bet added at {}% offset", (int)(testOffsets[i] * 100));
                    }
                }
            }

            searchInput.fill("");

            // Take final screenshot
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("interactive_outcome_detection.png"))
                    .setFullPage(true));

            log.info("\n{} {} Detection complete! Check interactive_outcome_detection.png",
                    EMOJI_SUCCESS, EMOJI_GAME);

        } catch (Exception e) {
            log.error("{} {} Error: {}", EMOJI_ERROR, EMOJI_GAME, e.getMessage(), e);
        }
    }

    private static int getBetslipCount(Page page) {
        try {
            var countSelectors = new String[]{
                    "[class*='betslip-count']",
                    "[class*='coupon-count']",
                    "[class*='bet-count']",
                    "[class*='cart-count']"
            };

            for (String selector : countSelectors) {
                var element = page.locator(selector);
                if (element.count() > 0 && element.first().isVisible()) {
                    String text = element.first().textContent().trim();
                    if (!text.isEmpty() && text.matches("\\d+")) {
                        return Integer.parseInt(text);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }

    private static String getBetslipDetails(Page page) {
        try {
            var betslip = page.locator("[class*='betslip'], [class*='coupon']").first();
            if (betslip.isVisible()) {
                return betslip.textContent().substring(0, Math.min(100, betslip.textContent().length()));
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Could not retrieve betslip details";
    }

    private static void removeBetFromSlip(Page page) {
        try {
            // Try to find and click remove/close button
            var removeButtons = page.locator(
                    "[class*='remove'], [class*='delete'], [class*='close'], " +
                            "[aria-label*='remove'], [aria-label*='delete']"
            );

            for (int i = 0; i < Math.min(5, removeButtons.count()); i++) {
                var btn = removeButtons.nth(i);
                if (btn.isVisible()) {
                    btn.click();
                    Thread.sleep(500);
                    return;
                }
            }

            // Alternative: clear all bets button
            var clearAll = page.locator("text=/clear all/i, text=/remove all/i");
            if (clearAll.count() > 0) {
                clearAll.first().click();
            }

        } catch (Exception e) {
            log.debug("Could not remove bet: {}", e.getMessage());
        }
    }


    public static boolean clickOutcomeByMarketSearch(Page page, String marketName, String outcome) {
        log.info("{} {} Searching for market: '{}' with outcome: '{}'",
                EMOJI_SEARCH, EMOJI_BET, marketName, outcome);

        try {
            // Step 1: Find and click the market search input
            var searchInput = page.locator("input.ui-search-default__input[placeholder='Search by market']");

            if (!searchInput.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(5000))) {
                log.error("{} {} Market search input not visible", EMOJI_ERROR, EMOJI_SEARCH);
                return false;
            }

            // Step 2: Clear any existing search text
            searchInput.click();
            Thread.sleep(300);
            searchInput.fill("");
            Thread.sleep(300);

            // Step 3: Type the market name
            log.info("{} {} Typing market name: '{}'", EMOJI_SEARCH, EMOJI_BET, marketName);
            searchInput.fill(marketName);
            Thread.sleep(1000); // Wait for search results to filter

            // Step 4: Find the market element (it should now be visible/highlighted)
            var marketElement = findMarketElementAfterSearch(page, marketName);

            if (marketElement == null) {
                log.error("{} {} Market '{}' not found after search", EMOJI_ERROR, EMOJI_BET, marketName);
                // Clear search to restore view
                searchInput.fill("");
                return false;
            }

            // Step 5: Get market position
            marketElement.scrollIntoViewIfNeeded();
            Thread.sleep(500);

            var marketBox = marketElement.boundingBox();
            if (marketBox == null) {
                log.error("{} {} Could not get market bounding box", EMOJI_ERROR, EMOJI_BET);
                searchInput.fill("");
                return false;
            }

            log.info("{} {} Found market at position: ({}, {})",
                    EMOJI_SUCCESS, EMOJI_SEARCH, marketBox.x, marketBox.y);

            // Step 6: Get canvas and calculate click position
            var canvas = page.locator("canvas.market-grid-canvas__canvas").first();
            var canvasBox = canvas.boundingBox();

            if (canvasBox == null) {
                log.error("{} {} Canvas not found", EMOJI_ERROR, EMOJI_BET);
                searchInput.fill("");
                return false;
            }

            // Calculate outcome position
            double clickX = calculateOutcomeXPosition(canvasBox, outcome);
            double clickY = marketBox.y + (marketBox.height / 2);

            log.info("{} {} Clicking outcome '{}' at coordinates: ({}, {})",
                    EMOJI_BET, EMOJI_GAME, outcome, clickX, clickY);

            // Step 7: Hover first to see if it highlights (optional but helpful)
            page.mouse().move(clickX, clickY);
            Thread.sleep(300);

            // Step 8: Click the outcome
            page.mouse().click(clickX, clickY);
            Thread.sleep(1000);

            // Step 9: Clear the search to restore full market view
            searchInput.fill("");
            Thread.sleep(500);

            // Step 10: Verify bet was added
            if (verifyBetAddedToSlip(page, marketName, outcome)) {
                log.info("{} {} Successfully added '{}' from '{}' to betslip",
                        EMOJI_SUCCESS, EMOJI_BET, outcome, marketName);
                return true;
            } else {
                log.warn("{} {} Clicked but bet may not have been added to betslip",
                        EMOJI_WARNING, EMOJI_BET);
                return false;
            }

        } catch (Exception e) {
            log.error("{} {} Failed to click outcome via market search: {}",
                    EMOJI_ERROR, EMOJI_BET, e.getMessage(), e);

            // Clean up: clear search on error
            try {
                page.locator("input.ui-search-default__input[placeholder='Search by market']").fill("");
            } catch (Exception cleanupError) {
                // Ignore cleanup errors
            }

            return false;
        }
    }

    private static Locator findMarketElementAfterSearch(Page page, String marketName) {
        try {
            // After searching, the market should be visible
            // Try to find it by exact text match first
            var exactMatch = page.locator(String.format("text='%s'", marketName)).first();
            if (exactMatch.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(2000))) {
                return exactMatch;
            }

            // Try partial match
            var partialMatch = page.locator(String.format("text=/%s/i", marketName)).first();
            if (partialMatch.isVisible(new com.microsoft.playwright.Locator.IsVisibleOptions().setTimeout(2000))) {
                return partialMatch;
            }

            // Try finding in common market name containers
            var marketContainers = page.locator("[class*='market'], [class*='caption'], span, div").all();
            for (var container : marketContainers) {
                try {
                    String text = container.textContent().trim();
                    if (text.equalsIgnoreCase(marketName) || text.contains(marketName)) {
                        if (container.isVisible()) {
                            return container;
                        }
                    }
                } catch (Exception e) {
                    // Skip this element
                }
            }

        } catch (Exception e) {
            log.error("{} {} Error finding market after search: {}",
                    EMOJI_ERROR, EMOJI_SEARCH, e.getMessage());
        }

        return null;
    }

    private static double calculateOutcomeXPosition(com.microsoft.playwright.options.BoundingBox canvasBox, String outcome) {
        // 1xBet typically displays outcomes in columns on the right side of the canvas
        // Typical layout from left to right: [Market Names] [Outcome 1] [Outcome X] [Outcome 2]

        double canvasLeft = canvasBox.x;
        double canvasWidth = canvasBox.width;

        // Outcomes typically start around 65-70% of canvas width
        // Each outcome column is roughly 10% of canvas width
        double outcomeAreaStart = canvasLeft + (canvasWidth * 0.68);
        double columnWidth = canvasWidth * 0.10;

        String outcomeUpper = outcome.toUpperCase();

        // First outcome column (Home/1/Over/Yes)
        if (outcomeUpper.equals("1") ||
                outcomeUpper.equals("HOME") ||
                outcomeUpper.equals("OVER") ||
                outcomeUpper.equals("YES")) {
            return outcomeAreaStart + (columnWidth * 0.5);
        }

        // Second outcome column (Draw/X)
        else if (outcomeUpper.equals("X") ||
                outcomeUpper.equals("DRAW")) {
            return outcomeAreaStart + columnWidth + (columnWidth * 0.5);
        }

        // Third outcome column (Away/2/Under/No)
        else if (outcomeUpper.equals("2") ||
                outcomeUpper.equals("AWAY") ||
                outcomeUpper.equals("UNDER") ||
                outcomeUpper.equals("NO")) {
            return outcomeAreaStart + (columnWidth * 2) + (columnWidth * 0.5);
        }

        // For handicap values like "1.5", "2.5", etc.
        else if (outcome.matches("\\d+\\.\\d+")) {
            // These are typically in the first or second column
            return outcomeAreaStart + (columnWidth * 0.5);
        }

        // Default to first outcome column
        log.warn("{} {} Unknown outcome type '{}', using default position",
                EMOJI_WARNING, EMOJI_BET, outcome);
        return outcomeAreaStart + (columnWidth * 0.5);
    }

    private static boolean verifyBetAddedToSlip(Page page, String marketName, String outcome) {
        try {
            Thread.sleep(800); // Wait for betslip animation

            // Check various betslip indicators
            var betslipIndicators = new String[]{
                    "[class*='betslip-count']:visible",
                    "[class*='coupon-count']:visible",
                    "[class*='bet-count']:visible",
                    "[class*='cart-count']:visible"
            };

            for (String selector : betslipIndicators) {
                try {
                    var indicator = page.locator(selector);
                    if (indicator.count() > 0) {
                        String count = indicator.first().textContent();
                        if (count != null && !count.trim().isEmpty() && !count.equals("0")) {
                            log.info("{} {} Betslip count detected: {}",
                                    EMOJI_SUCCESS, EMOJI_BET, count);
                            return true;
                        }
                    }
                } catch (Exception e) {
                    // Try next indicator
                }
            }

            // Check if betslip panel/drawer is visible
            var betslipPanel = page.locator("[class*='betslip'], [class*='coupon'], [class*='bet-slip']");
            if (betslipPanel.count() > 0) {
                var firstPanel = betslipPanel.first();
                if (firstPanel.isVisible()) {
                    log.info("{} {} Betslip panel is visible", EMOJI_SUCCESS, EMOJI_BET);
                    return true;
                }
            }

            log.warn("{} {} Could not verify bet addition", EMOJI_WARNING, EMOJI_BET);
            return false;

        } catch (Exception e) {
            log.error("{} {} Error verifying bet addition: {}",
                    EMOJI_ERROR, EMOJI_BET, e.getMessage());
            return false;
        }
    }


    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false)  // Set to false to see what's happening
                    .setSlowMo(500));    // Slow down actions

            Page page = browser.newPage();

            // Run the interactive detector
            interactiveOutcomeDetection(page,
                    "https://1xbet.ng/en/live/football/12821-france-ligue-1/690026914-paris-angers-sco");

            // Keep browser open to review results
            Thread.sleep(10000);

            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}