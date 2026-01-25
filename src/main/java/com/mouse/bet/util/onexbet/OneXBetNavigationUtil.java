package com.mouse.bet.util.onexbet;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.orchestrator.model.BetLeg;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OneXBetNavigationUtil {
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

    public static void navigateToBookmaker(Page page, String baseUrl) throws Exception {
        log.info("{} {} Navigating to MSport homepage: {}", EMOJI_NAVIGATION, EMOJI_START, baseUrl);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (page.isClosed()) {
                    log.error("{} {} Page is closed, cannot navigate", EMOJI_ERROR, EMOJI_BET);
                    throw new RuntimeException("Page is closed");
                }

                page.navigate(baseUrl, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

//                page.evaluate("() => { document.body.style.zoom = '0.95'; window.scrollTo(0,0); }");

                log.info("{} {} Successfully navigated to MSport", EMOJI_SUCCESS, EMOJI_BET);
                return;

            } catch (PlaywrightException e) {
                String errorMsg = e.getMessage();

                if (errorMsg.contains("Object doesn't exist") ||
                        errorMsg.contains("frame was detached") ||
                        errorMsg.contains("ERR_ABORTED") ||
                        errorMsg.contains("Timeout")) {

                    log.warn("{} {} Navigation attempt {}/{} failed: {}",
                            EMOJI_WARNING, EMOJI_BET, attempt, maxAttempts, errorMsg);

                    if (attempt < maxAttempts) {
                        Thread.sleep(2000 * attempt);
                        continue;
                    }
                }

                log.error("{} {} Failed to navigate after {} attempts: {}",
                        EMOJI_ERROR, EMOJI_BET, maxAttempts, errorMsg);
                throw new RuntimeException("Navigation failed after retries", e);
            }
        }
    }

    public static void returnToSportPage(Page page, Sport configuredSport) {
    }

    public static void navigateToLiveEvents(Page page) {
        log.info("{} {} Navigating to 1xBet live events page", EMOJI_NAVIGATION, EMOJI_CLOCK);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (page.isClosed()) {
                    log.error("{} {} Page is closed, cannot navigate to live events", EMOJI_ERROR, EMOJI_CLOCK);
                    throw new RuntimeException("Page is closed");
                }

                // Primary strategy: Click the Live navigation element
                try {
                    log.info("{} {} Attempting to click Live navigation element", EMOJI_SEARCH, EMOJI_CLOCK);

                    var liveLink = page.locator("a.header-navigation-section-link[href='/en/live']");

                    // Wait up to 20 seconds for the element to be visible
                    if (liveLink.isVisible(new Locator.IsVisibleOptions().setTimeout(20000))) {
                        liveLink.click(new Locator.ClickOptions().setTimeout(10000));

                        // Wait for navigation to complete
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                                new Page.WaitForLoadStateOptions().setTimeout(30000));

                        log.info("{} {} Successfully clicked Live navigation element", EMOJI_SUCCESS, EMOJI_CLOCK);
                        return;
                    } else {
                        log.warn("{} {} Live navigation element not visible after 20 seconds", EMOJI_WARNING, EMOJI_CLOCK);
                    }

                } catch (PlaywrightException e) {
                    log.warn("{} {} Failed to click Live navigation element: {}", EMOJI_WARNING, EMOJI_CLOCK, e.getMessage());
                }

                // Fallback strategy: Direct navigation to live URL
                log.info("{} {} Using fallback: Direct navigation to live events", EMOJI_NAVIGATION, EMOJI_CLOCK);

                String liveUrl = "https://www.1xbet.ng/en/live";

                page.navigate(liveUrl, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                // Wait for live events to load
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(30000));

                log.info("{} {} Successfully navigated to 1xBet live events page via direct URL", EMOJI_SUCCESS, EMOJI_CLOCK);
                return;

            } catch (PlaywrightException e) {
                String errorMsg = e.getMessage();

                if (errorMsg.contains("Object doesn't exist") ||
                        errorMsg.contains("frame was detached") ||
                        errorMsg.contains("ERR_ABORTED") ||
                        errorMsg.contains("Timeout")) {

                    log.warn("{} {} Live events navigation attempt {}/{} failed: {}",
                            EMOJI_WARNING, EMOJI_CLOCK, attempt, maxAttempts, errorMsg);

                    if (attempt < maxAttempts) {
                        try {
                            Thread.sleep(2000 * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Navigation interrupted", ie);
                        }
                        continue;
                    }
                }

                log.error("{} {} Failed to navigate to live events after {} attempts: {}",
                        EMOJI_ERROR, EMOJI_CLOCK, maxAttempts, errorMsg);
                throw new RuntimeException("Live events navigation failed after retries", e);
            }
        }
    }

//    public static void navigateToLiveEvents(Page page) {
//    }

    public static void navigateToSportPage(Page page, Sport configuredSport) {
    }

    public static boolean navigateToGame(Page page, BettingTask task) {
        log.info("🎯 Direct navigation to match using bookmaker URL");

        try {
            // Get the bookmaker URL from the task
            String bookmakerUrl = task.bookmakerUrl();

            if (bookmakerUrl == null || bookmakerUrl.isEmpty()) {
                log.warn("⚠️ No bookmaker URL available in task");
                return false;
            }

            log.info("📎 Navigating to: {}", bookmakerUrl);

            // Navigate directly to the URL
            page.navigate(bookmakerUrl, new Page.NavigateOptions()
                    .setTimeout(15000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            log.info("✅ Successfully navigated to match page");
            return true;

        } catch (Exception e) {
            log.error("❌ Direct navigation failed: {}", e.getMessage(), e);
            return false;
        }
    }
}
