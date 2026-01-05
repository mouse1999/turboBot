package com.mouse.bet.util.msport;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import com.microsoft.playwright.options.WaitUntilState;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.interfaces.BettingTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * MSport Navigation Utility - Handles all page navigation operations
 * Extracted from MSportWindow for better code organization
 */
@Slf4j
@Component
public class MSportNavigationUtil {

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

    @Value("${msport.base.url:https://www.msport.com/ng/web}")
    private static String baseUrl;

    @Value("${msport.login.url:https://www.msport.com/ng/web}")
    private static String loginUrl;

    @Value("${msport.live.events.url:https://www.msport.com/ng/web/live_matches}")
    private static String liveEventsUrl;

    /**
     * Navigate to MSport bookmaker's homepage
     */
    public static void navigateToBookmaker(Page page) throws Exception {
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
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));

                page.evaluate("() => { document.body.style.zoom = '0.95'; window.scrollTo(0,0); }");

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

    /**
     * Navigate to live events page
     */
    public static void navigateToLiveEvents(Page page) throws Exception {
        log.info("{} {} Navigating to live events page", EMOJI_NAVIGATION, EMOJI_BET);

        try {
            // Click 'Live Betting' link
            Locator liveBettingLink = page.locator("#header_nav_liveBetting");

            if (liveBettingLink.count() > 0 && liveBettingLink.isVisible()) {
                liveBettingLink.click(new Locator.ClickOptions().setTimeout(10000));
                log.info("Clicked 'Live Betting' using ID selector");
            } else {
                // Fallback to text selector
                page.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                                new Page.GetByRoleOptions().setName("Live Betting").setExact(true))
                        .click(new Locator.ClickOptions().setTimeout(10000));
                log.info("Clicked 'Live Betting' using accessibility role");
            }

            // Wait for navigation
            page.waitForURL(url -> url.toString().contains("/live_matches"),
                    new Page.WaitForURLOptions().setTimeout(20000));
            page.waitForLoadState(LoadState.LOAD,
                    new Page.WaitForLoadStateOptions().setTimeout(15000));

            log.info("{} Successfully navigated to Live Betting page: {}", EMOJI_SUCCESS, page.url());

        } catch (Exception e) {
            throw new RuntimeException("Failed to navigate to live events: " + e.getMessage(), e);
        }
    }

    /**
     * Navigate to a specific sport page
     */
    public static void navigateToSportPage(Page page, Sport Sport) throws Exception {
        log.info("{} {} Navigating to sport page: {}", EMOJI_NAVIGATION, EMOJI_SEARCH, Sport);

        String currentUrl = page.url();
        if (!currentUrl.contains("/default/live_matches")) {
            throw new RuntimeException("Not on default Live Sport page. URL: " + currentUrl);
        }

        log.info("✅ Sport Page loaded: {}", currentUrl);
        randomHumanDelay(1500, 3000);

        // Navigate to target sport
        switch (Sport) {
            case BASKETBALL:
                switchToLiveSport(page, "Basketball");
                break;
            case TABLE_TENNIS:
                switchToLiveSport(page, "Table Tennis");
                break;
            case FOOTBALL:
                switchToLiveSport(page, "Soccer");
                break;
            default:
                log.info("Staying on the default live sport page");
                randomHumanDelay(2000, 4000);
        }
    }

    /**
     * Universal sport switcher for MSport
     */
    private static void switchToLiveSport(Page page, String sportInput) throws InterruptedException {
        String sport = sportInput.trim();
        String displayName;
        String urlSegment;

        switch (sport.toLowerCase()) {
            case "soccer":
                displayName = "Soccer";
                urlSegment = "Soccer";
                break;
            case "basketball":
                displayName = "Basketball";
                urlSegment = "Basketball";
                break;
            case "table tennis":
            case "table-tennis":
            case "tt":
            case "tabletennis":
                displayName = "Table Tennis";
                urlSegment = "Table%20Tennis";
                break;
            case "tennis":
                displayName = "Tennis";
                urlSegment = "Tennis";
                break;
            default:
                displayName = sport.substring(0, 1).toUpperCase() + sport.substring(1).toLowerCase();
                urlSegment = displayName.toLowerCase().replace(" ", "-");
        }

        log.info("Switching to live sport: {} → {}", displayName, urlSegment);

        // Click visible sport tab
        clickVisibleSportTab(page, displayName, urlSegment);
        randomHumanDelay(2200, 4200);
    }

    /**
     * Click visible sport tab
     */
    private static void clickVisibleSportTab(Page page, String sportName, String expectedUrlSegment)
            throws InterruptedException {

        String selector = String.format(".m-nav-item:has(.m-label:text-is('%s'))", sportName);
        Locator sportTab = page.locator(selector);

        sportTab.click(new Locator.ClickOptions()
                .setTimeout(12000)
                .setForce(true));

        if (!"Soccer".equals(sportName) && expectedUrlSegment != null && !expectedUrlSegment.isEmpty()) {
            page.waitForURL("**/" + expectedUrlSegment + "/**",
                    new Page.WaitForURLOptions().setTimeout(15000));
        }
    }

    /**
     * Navigate to a specific game/match
     */
    public static void navigateToGame(Page page, BettingTask task) throws Exception {
        String home = task.getHomeTeam().trim();
        String away = task.getAwayTeam().trim();
        String fullMatch = home + " vs " + away;

        log.info("{} Navigating to: {}", EMOJI_BET, fullMatch);

        randomHumanDelay(500, 1500);

        if (!tryDirectNavigation(page, home, away, task)) {
            throw new Exception("Failed to navigate to game: " + fullMatch);
        }
    }

    /**
     * Direct click in MSport event list (uses multiple strategies)
     */
    private static boolean tryDirectNavigation(Page page, String home, String away, BettingTask task) {
        log.info("🎯 Searching for match: {} vs {}", home, away);

        try {
            if (tryClickByAriaLabel(page, home, away)) return true;
            if (tryClickByHref(page, home, away)) return true;
            if (tryClickByTeamText(page, home, away)) return true;
            if (tryClickByPartialMatch(page, home, away)) return true;
            if (tryClickByFuzzyMatch(page, home, away)) return true;

            log.warn("❌ Could not find match with any strategy");
            logAvailableMatches(page);

        } catch (Exception e) {
            log.error("❌ Navigation error: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * Strategy 1: Click using aria-label attribute
     */
    private static boolean tryClickByAriaLabel(Page page, String home, String away) {
        log.info("Strategy 1: Searching by aria-label attribute");

        String[] labelPatterns = {
                String.format("%s vs %s", home, away),
                String.format("%s - %s", home, away),
                String.format("%s v %s", home, away)
        };

        for (String labelPattern : labelPatterns) {
            try {
                Locator match = page.locator(String.format(".m-teams a[aria-label='%s']", labelPattern));

                if (match.count() > 0 && match.first().isVisible()) {
                    log.info("✅ Found by exact aria-label: '{}'", labelPattern);
                    return clickMatchElement(page, match.first());
                }

            } catch (PlaywrightException e) {
                log.debug("aria-label pattern '{}' failed: {}", labelPattern, e.getMessage());
            }
        }

        log.debug("❌ aria-label strategy failed");
        return false;
    }

    /**
     * Strategy 2: Click by matching href pattern
     */
    private static boolean tryClickByHref(Page page, String home, String away) {
        log.info("Strategy 2: Searching by href pattern");

        try {
            String homeUrl = home.replace(" ", "_").replace(",", "");
            String awayUrl = away.replace(" ", "_").replace(",", "");

            String pattern = String.format("%s_vs_%s", homeUrl, awayUrl);
            Locator match = page.locator(String.format(".m-teams a[href*='%s']", pattern));

            if (match.count() > 0 && match.first().isVisible()) {
                log.info("✅ Found by href pattern: '{}'", pattern);
                return clickMatchElement(page, match.first());
            }

        } catch (Exception e) {
            log.debug("href strategy error: {}", e.getMessage());
        }

        log.debug("❌ href strategy failed");
        return false;
    }

    /**
     * Strategy 3: Click by finding team text in m-teams--info
     */
    private static boolean tryClickByTeamText(Page page, String home, String away) {
        log.info("Strategy 3: Searching by team text");

        try {
            Locator allTeamsContainers = page.locator(".m-teams");
            int count = allTeamsContainers.count();

            for (int i = 0; i < count; i++) {
                try {
                    Locator teamWrappers = page.locator(
                            ".m-teams:nth-of-type(" + (i + 1) + ") .m-server-name-wrapper"
                    );

                    if (teamWrappers.count() >= 2) {
                        String homeText = page.locator(
                                ".m-teams:nth-of-type(" + (i + 1) +
                                        ") .m-server-name-wrapper:nth-of-type(1) .tw-w-full.tw-truncate"
                        ).textContent().trim();

                        String awayText = page.locator(
                                ".m-teams:nth-of-type(" + (i + 1) +
                                        ") .m-server-name-wrapper:nth-of-type(2) .tw-w-full.tw-truncate"
                        ).textContent().trim();

                        if (homeText.equalsIgnoreCase(home) && awayText.equalsIgnoreCase(away)) {
                            log.info("✅ Found by team text match");
                            Locator clickTarget = page.locator(
                                    ".m-teams:nth-of-type(" + (i + 1) + ") a"
                            ).first();
                            return clickMatchElement(page, clickTarget);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error checking container {}: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.debug("Team text strategy error: {}", e.getMessage());
        }

        log.debug("❌ Team text strategy failed");
        return false;
    }

    /**
     * Strategy 4: Partial matching (case-insensitive)
     */
    private static boolean tryClickByPartialMatch(Page page, String home, String away) {
        log.info("Strategy 4: Partial matching");

        try {
            String homeNorm = normalizeTeamName(home);
            String awayNorm = normalizeTeamName(away);

            Locator allTeamsContainers = page.locator(".m-teams");
            int count = allTeamsContainers.count();

            for (int i = 0; i < count; i++) {
                Locator teamsContainer = allTeamsContainers.nth(i);

                try {
                    String fullText = teamsContainer.textContent().trim();
                    String fullTextNorm = normalizeTeamName(fullText);

                    if (fullTextNorm.contains(homeNorm) && fullTextNorm.contains(awayNorm)) {
                        log.info("✅ Found by partial match");
                        Locator clickTarget = teamsContainer.locator("a").first();
                        return clickMatchElement(page, clickTarget);
                    }

                } catch (Exception e) {
                    log.debug("Error checking container {}: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.debug("Partial match strategy error: {}", e.getMessage());
        }

        log.debug("❌ Partial match strategy failed");
        return false;
    }

    /**
     * Strategy 5: Fuzzy matching (handles name variations)
     */
    private static boolean tryClickByFuzzyMatch(Page page, String home, String away) {
        log.info("Strategy 5: Fuzzy matching");

        try {
            String homeKey = extractKeyName(home);
            String awayKey = extractKeyName(away);

            Locator allTeamsContainers = page.locator(".m-teams");
            int count = allTeamsContainers.count();

            for (int i = 0; i < count; i++) {
                Locator teamsContainer = allTeamsContainers.nth(i);

                try {
                    String fullText = teamsContainer.textContent().toLowerCase().trim();

                    if (fullText.contains(homeKey.toLowerCase()) &&
                            fullText.contains(awayKey.toLowerCase())) {
                        log.info("✅ Found by fuzzy match");
                        Locator clickTarget = teamsContainer.locator("a").first();
                        return clickMatchElement(page, clickTarget);
                    }

                } catch (Exception e) {
                    log.debug("Error checking container {}: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.debug("Fuzzy match strategy error: {}", e.getMessage());
        }

        log.debug("❌ Fuzzy match strategy failed");
        return false;
    }

    /**
     * Click match element and navigate to event page
     */
    private static boolean clickMatchElement(Page page, Locator matchElement) {
        try {
            String matchInfo = "unknown";
            try {
                String ariaLabel = matchElement.getAttribute("aria-label");
                matchInfo = (ariaLabel != null && !ariaLabel.isEmpty())
                        ? ariaLabel.trim().replaceAll("\\s+", " ")
                        : matchElement.textContent().trim().replaceAll("\\s+", " ");
            } catch (Exception ignored) {}

            log.info("Clicking match: {}", matchInfo);

            matchElement.evaluate("""
                el => {
                    el.scrollIntoView({ block: 'center', behavior: 'instant' });
                    el.click();
                }
                """);

            randomHumanDelay(200, 400);

            try {
                page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(12000));

                page.locator(".m-event--main, .m-teams, .m-market-box, .match-scores")
                        .first()
                        .waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(5000));

                log.info("Navigation successful → {}", page.url());
                return true;

            } catch (PlaywrightException e) {
                String currentUrl = page.url();
                boolean isMatchPage = currentUrl.contains("_vs_") ||
                        currentUrl.contains("/sr:match:") ||
                        (currentUrl.contains("/live/") && currentUrl.split("/").length > 7);

                if (isMatchPage) {
                    log.info("On match page despite timeout → {}", currentUrl);
                    return true;
                } else {
                    log.warn("Navigation failed. Still on: {}", currentUrl);
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("Unexpected error clicking match: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Return to sport page after bet placement
     */
    public static void returnToSportPage(Page page, Sport Sport) throws Exception {
        log.info("{} {} Returning to {} live page...", EMOJI_NAVIGATION, EMOJI_SEARCH, Sport);

        switchToLiveSport(page, Sport.name());
    }

    /**
     * Wait for page to be fully loaded and ready
     */
    public static void waitForPageReady(Page page) throws Exception {
        log.info("{} {} Waiting for page to be ready...", EMOJI_CLOCK, EMOJI_HEALTH);

        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("document.readyState === 'complete'");
        page.waitForSelector("body", new Page.WaitForSelectorOptions().setTimeout(10000));
    }

    // Helper methods
    private static String normalizeTeamName(String name) {
        return name.toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[,.]", "")
                .trim();
    }

    private static String extractKeyName(String fullName) {
        if (fullName.contains(",")) {
            return fullName.split(",")[0].trim();
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }

    private static void logAvailableMatches(Page page) {
        try {
            log.info("====📋 Available matches on page:====");
            Locator matches = page.locator(".m-teams a[aria-label]");
            int count = Math.min(matches.count(), 10);

            for (int i = 0; i < count; i++) {
                String ariaLabel = matches.nth(i).getAttribute("aria-label");
                log.info("  {}. {}", i + 1, ariaLabel);
            }
        } catch (Exception e) {
            log.debug("Could not log available matches: {}", e.getMessage());
        }
    }

    private static void randomHumanDelay(long minMs, long maxMs) {
        try {
            long delay = minMs + ThreadLocalRandom.current().nextLong(maxMs - minMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }




}