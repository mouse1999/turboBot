package com.mouse.bet.util.bet9ja;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.orchestrator.model.BetLeg;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

@Slf4j
public class Bet9jaNavigationUtil {


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
    private static final String EMOJI_SCROLL = "";
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
    public static boolean navigateToGame(Page page, BetLeg betLeg) {
        return false;

    }

    public static void returnToSportPage(Page page, Sport configuredSport) {
    }


    public static void navigateToLiveEvents(Page page) {
        log.info("{} {} Navigating to Bet9ja live events page", EMOJI_NAVIGATION, EMOJI_CLOCK);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (page.isClosed()) {
                    log.error("{} {} Page is closed, cannot navigate to live events", EMOJI_ERROR, EMOJI_CLOCK);
                    throw new RuntimeException("Page is closed");
                }

                // Scroll before attempting navigation (mimic human behavior)
                log.info("{} Scrolling page before navigation attempt", EMOJI_SCROLL);
                scrollPage(page);
                randomDelay(1000, 2000);

                // Primary strategy: Click the Live navigation element
                try {
                    log.info("{} {} Attempting to click Live navigation element", EMOJI_SEARCH, EMOJI_CLOCK);

                    // Multiple selectors for Bet9ja Live link
                    String[] liveSelectors = {
                            "a.h-ml__nav-link[href='/liveCompetitions']",
                            "a#header_staticlink_live_betting",
                            "li.h-ml__nav-item a[href='/liveCompetitions']",
                            "a.h-ml__nav-link:has-text('Live')",
                            "a[id='header_staticlink_live_betting']"
                    };

                    Locator liveLink = null;

                    // Try each selector
                    for (String selector : liveSelectors) {
                        try {
                            log.debug("Trying selector: {}", selector);
                            Locator tempLink = page.locator(selector).first();

                            if (tempLink.count() > 0 && tempLink.isVisible(new Locator.IsVisibleOptions().setTimeout(5000))) {
                                liveLink = tempLink;
                                log.debug("✅ Found Live link with selector: {}", selector);
                                break;
                            }
                        } catch (Exception e) {
                            log.debug("Selector '{}' failed: {}", selector, e.getMessage());
                        }
                    }

                    if (liveLink != null && liveLink.isVisible(new Locator.IsVisibleOptions().setTimeout(20000))) {
                        // Scroll to element before clicking
                        log.debug("Scrolling to Live link element");
                        liveLink.scrollIntoViewIfNeeded();
                        Thread.sleep(randomDelay(500, 1000));

                        // Click the link
                        liveLink.click(new Locator.ClickOptions().setTimeout(10000));

                        // Wait for navigation to complete
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                                new Page.WaitForLoadStateOptions().setTimeout(30000));

                        log.info("{} {} Successfully clicked Live navigation element", EMOJI_SUCCESS, EMOJI_CLOCK);

                        // Scroll after successful navigation
                        log.info("{} Scrolling page after navigation", EMOJI_SCROLL);
                        Thread.sleep(randomDelay(1500, 2500));
                        scrollPage(page);

                        return;
                    } else {
                        log.warn("{} {} Live navigation element not visible after 20 seconds", EMOJI_WARNING, EMOJI_CLOCK);
                    }

                } catch (PlaywrightException e) {
                    log.warn("{} {} Failed to click Live navigation element: {}", EMOJI_WARNING, EMOJI_CLOCK, e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("{} {} Thread interrupted during navigation", EMOJI_WARNING, EMOJI_CLOCK);
                }

                // Fallback strategy: Direct navigation to live URL
                log.info("{} {} Using fallback: Direct navigation to live events", EMOJI_NAVIGATION, EMOJI_CLOCK);

                String liveUrl = "https://www.bet9ja.com/liveCompetitions";

                page.navigate(liveUrl, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                // Wait for live events to load
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(30000));

                log.info("{} {} Successfully navigated to Bet9ja live events page via direct URL", EMOJI_SUCCESS, EMOJI_CLOCK);

                // Scroll after successful navigation
                log.info("{} Scrolling page after direct navigation", EMOJI_SCROLL);
                try {
                    Thread.sleep(randomDelay(2000, 3000));
                    scrollPage(page);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("{} Thread interrupted during post-navigation scroll", EMOJI_WARNING);
                }

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
                            long waitTime = 2000 * attempt;
                            log.info("Waiting {}ms before retry attempt {}", waitTime, attempt + 1);
                            Thread.sleep(waitTime);
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


    public static void navigateToSportPage(Page page, Sport configuredSport) {
    }


    /**
     * Scroll the page with random probability-based behavior
     * Mimics human scrolling patterns with varying directions and speeds
     */
    private static void scrollPage(Page page) {
        try {
            int viewportHeight = (int) page.evaluate("window.innerHeight");
            int totalHeight = (int) page.evaluate("document.body.scrollHeight");
            int currentPosition = (int) page.evaluate("window.pageYOffset");

            Random random = new Random();
            int scrollActions = random.nextInt(8) + 6; // 6-13 scroll actions

            log.debug("Starting probability-based scrolling. Actions: {}, Total height: {}px",
                    scrollActions, totalHeight);

            for (int i = 0; i < scrollActions; i++) {
                int probability = random.nextInt(100);
                int scrollAmount;

                // Probability-based scroll direction and amount
                if (probability < 60) {
                    // 60% chance: Scroll down moderately
                    scrollAmount = random.nextInt(viewportHeight / 2) + viewportHeight / 4;
                    currentPosition += scrollAmount;
                    currentPosition = Math.min(currentPosition, totalHeight - viewportHeight);
                    log.debug("Action {}: Moderate DOWN {}px to {}px (prob: {})",
                            i + 1, scrollAmount, currentPosition, probability);

                } else if (probability < 80) {
                    // 20% chance: Scroll down aggressively
                    scrollAmount = random.nextInt(viewportHeight) + viewportHeight / 2;
                    currentPosition += scrollAmount;
                    currentPosition = Math.min(currentPosition, totalHeight - viewportHeight);
                    log.debug("Action {}: Aggressive DOWN {}px to {}px (prob: {})",
                            i + 1, scrollAmount, currentPosition, probability);

                } else if (probability < 92) {
                    // 12% chance: Scroll up a little (re-reading)
                    scrollAmount = random.nextInt(viewportHeight / 3) + 100;
                    currentPosition -= scrollAmount;
                    currentPosition = Math.max(0, currentPosition);
                    log.debug("Action {}: Small UP {}px to {}px (prob: {})",
                            i + 1, scrollAmount, currentPosition, probability);

                } else {
                    // 8% chance: Scroll up more (going back)
                    scrollAmount = random.nextInt(viewportHeight / 2) + viewportHeight / 3;
                    currentPosition -= scrollAmount;
                    currentPosition = Math.max(0, currentPosition);
                    log.debug("Action {}: Large UP {}px to {}px (prob: {})",
                            i + 1, scrollAmount, currentPosition, probability);
                }

                // Smooth scroll to position
                page.evaluate(String.format("window.scrollTo({top: %d, behavior: 'smooth'})", currentPosition));

                // Variable pause based on probability
                int pauseProbability = random.nextInt(100);
                long pause;

                if (pauseProbability < 50) {
                    // 50% chance: Quick scroll (scanning)
                    pause = randomDelay(400, 1000);
                } else if (pauseProbability < 85) {
                    // 35% chance: Medium pause (reading)
                    pause = randomDelay(1200, 2500);
                } else {
                    // 15% chance: Long pause (reading carefully)
                    pause = randomDelay(2500, 4500);
                }

                Thread.sleep(pause);
            }

            log.debug("✅ Probability-based scrolling completed at position: {}px", currentPosition);

        } catch (Exception e) {
            log.error("Error during probability-based scrolling: {}", e.getMessage());
        }
    }

    /**
     * Alternative: More aggressive scrolling pattern
     * Use this when you want to reach bottom faster but still look natural
     */
    private static void scrollPageAggressive(Page page) {
        try {
            int viewportHeight = (int) page.evaluate("window.innerHeight");
            int totalHeight = (int) page.evaluate("document.body.scrollHeight");
            int currentPosition = (int) page.evaluate("window.pageYOffset");

            Random random = new Random();
            int scrollActions = random.nextInt(6) + 4; // 4-9 actions (fewer than normal)

            log.debug("Starting aggressive probability-based scrolling. Actions: {}", scrollActions);

            for (int i = 0; i < scrollActions; i++) {
                int probability = random.nextInt(100);
                int scrollAmount;

                if (probability < 75) {
                    // 75% chance: Large scroll down
                    scrollAmount = random.nextInt(viewportHeight) + viewportHeight / 2;
                    currentPosition += scrollAmount;
                    currentPosition = Math.min(currentPosition, totalHeight - viewportHeight);
                    log.debug("Action {}: Large DOWN {}px to {}px", i + 1, scrollAmount, currentPosition);

                } else if (probability < 95) {
                    // 20% chance: Medium scroll down
                    scrollAmount = random.nextInt(viewportHeight / 2) + viewportHeight / 4;
                    currentPosition += scrollAmount;
                    currentPosition = Math.min(currentPosition, totalHeight - viewportHeight);
                    log.debug("Action {}: Medium DOWN {}px to {}px", i + 1, scrollAmount, currentPosition);

                } else {
                    // 5% chance: Small scroll up
                    scrollAmount = random.nextInt(viewportHeight / 4) + 50;
                    currentPosition -= scrollAmount;
                    currentPosition = Math.max(0, currentPosition);
                    log.debug("Action {}: Small UP {}px to {}px", i + 1, scrollAmount, currentPosition);
                }

                page.evaluate(String.format("window.scrollTo({top: %d, behavior: 'smooth'})", currentPosition));

                // Shorter pauses for aggressive scrolling
                long pause = randomDelay(300, 1200);
                Thread.sleep(pause);
            }

            log.debug("✅ Aggressive scrolling completed at position: {}px", currentPosition);

        } catch (Exception e) {
            log.error("Error during aggressive scrolling: {}", e.getMessage());
        }
    }

    /**
     * Alternative: Cautious scrolling pattern
     * Use this when you want slower, more careful scrolling
     */
    private static void scrollPageCautious(Page page) {
        try {
            int viewportHeight = (int) page.evaluate("window.innerHeight");
            int totalHeight = (int) page.evaluate("document.body.scrollHeight");
            int currentPosition = (int) page.evaluate("window.pageYOffset");

            Random random = new Random();
            int scrollActions = random.nextInt(10) + 8; // 8-17 actions (more than normal)

            log.debug("Starting cautious probability-based scrolling. Actions: {}", scrollActions);

            for (int i = 0; i < scrollActions; i++) {
                int probability = random.nextInt(100);
                int scrollAmount;

                if (probability < 45) {
                    // 45% chance: Small scroll down
                    scrollAmount = random.nextInt(viewportHeight / 3) + 100;
                    currentPosition += scrollAmount;
                    currentPosition = Math.min(currentPosition, totalHeight - viewportHeight);
                    log.debug("Action {}: Small DOWN {}px to {}px", i + 1, scrollAmount, currentPosition);

                } else if (probability < 70) {
                    // 25% chance: Medium scroll down
                    scrollAmount = random.nextInt(viewportHeight / 2) + viewportHeight / 4;
                    currentPosition += scrollAmount;
                    currentPosition = Math.min(currentPosition, totalHeight - viewportHeight);
                    log.debug("Action {}: Medium DOWN {}px to {}px", i + 1, scrollAmount, currentPosition);

                } else if (probability < 85) {
                    // 15% chance: Small scroll up
                    scrollAmount = random.nextInt(viewportHeight / 4) + 80;
                    currentPosition -= scrollAmount;
                    currentPosition = Math.max(0, currentPosition);
                    log.debug("Action {}: Small UP {}px to {}px", i + 1, scrollAmount, currentPosition);

                } else {
                    // 15% chance: Medium scroll up
                    scrollAmount = random.nextInt(viewportHeight / 3) + viewportHeight / 5;
                    currentPosition -= scrollAmount;
                    currentPosition = Math.max(0, currentPosition);
                    log.debug("Action {}: Medium UP {}px to {}px", i + 1, scrollAmount, currentPosition);
                }

                page.evaluate(String.format("window.scrollTo({top: %d, behavior: 'smooth'})", currentPosition));

                // Longer pauses for cautious scrolling
                int pauseProbability = random.nextInt(100);
                long pause;

                if (pauseProbability < 30) {
                    pause = randomDelay(800, 1500);
                } else if (pauseProbability < 70) {
                    pause = randomDelay(1500, 3000);
                } else {
                    pause = randomDelay(3000, 5000);
                }

                Thread.sleep(pause);
            }

            log.debug("✅ Cautious scrolling completed at position: {}px", currentPosition);

        } catch (Exception e) {
            log.error("Error during cautious scrolling: {}", e.getMessage());
        }
    }

    /**
     * Generate random delay in milliseconds
     */
    private static long randomDelay(int minMs, int maxMs) {
        Random random = new Random();
        return minMs + random.nextInt(maxMs - minMs);
    }
}
