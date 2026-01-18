package com.mouse.bet.util.sporty;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.exception.NavigationException;
import com.mouse.bet.interfaces.BettingTask;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;


@Slf4j
public class SportyNavigationUtil {


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

    private static final int RETRY_MAX_ATTEMPTS = 3;
    private static final long RETRY_TIMEOUT_MS = 10_000;
    private static final long RETRY_DELAY_MS = 1000;


    public static void waitForPageReady(Page page) {
    }

    public static void navigateToBookmaker(Page page, String baseUrl){
        log.info("{} {} Navigating to SportyBet...", " ", EMOJI_BET);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Check if page is still valid
                if (page.isClosed()) {
                    log.error("{} {} Page is closed, cannot navigate", EMOJI_ERROR, EMOJI_BET);
                    throw new RuntimeException("Page is closed");
                }

                // Navigate with more lenient options
                page.navigate(baseUrl, new Page.NavigateOptions()
                        .setTimeout(120000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));// More lenient than LOAD

//                // Wait for network to be idle (more stable)
//                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions()
//                        .setTimeout(30000));

                log.info("{} {} Successfully navigated to SportyBet", EMOJI_SUCCESS, EMOJI_BET);
                return; // SUCCESS - exit method

            } catch (PlaywrightException e) {
                String errorMsg = e.getMessage();

                if (errorMsg.contains("Object doesn't exist") ||
                        errorMsg.contains("frame was detached") ||
                        errorMsg.contains("ERR_ABORTED") ||
                        errorMsg.contains("Timeout")) {

                    log.warn("{} {} Navigation attempt {}/{} failed: {}",
                            EMOJI_WARNING, EMOJI_BET, attempt, maxAttempts, errorMsg);

                    if (attempt < maxAttempts) {
                        try {
                            Thread.sleep(2000 * attempt); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Navigation interrupted", ie);
                        }
                        continue; // Retry
                    }
                }

                // Either not a recoverable error, or we're out of attempts
                log.error("{} {} Failed to navigate after {} attempts: {}",
                        EMOJI_ERROR, EMOJI_BET, maxAttempts, errorMsg);
                throw new RuntimeException("Navigation failed after retries", e);
            }
        }
    }




    /**
     * Navigate to live betting page
     */
    public static void navigateToLiveEvents(Page page) {
        final long timeout = 20_000;

        try {
            Locator liveBettingLink = withLocatorRetry(
                    page, "#header_nav_liveBetting",
                    loc -> {
                        if (loc.isVisible(new Locator.IsVisibleOptions().setTimeout(120000))) {
                            return loc;
                        }
                        throw new RuntimeException("ID locator not visible");
                    },
                    RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
            );

            if (liveBettingLink != null) {
                liveBettingLink.click(new Locator.ClickOptions().setTimeout(50000));
                log.info("Clicked 'Live Betting' using ID selector");
            } else {
                throw new Exception("ID locator not visible");
            }
        } catch (Exception e) {
            log.info("ID selector failed, trying fallback...");

            try {
                Locator liveBettingLink = withLocatorRetry(
                        page, "a:has-text('Live Betting')",
                        loc -> loc,
                        RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
                );

                if (liveBettingLink != null) {
                    liveBettingLink.click(new Locator.ClickOptions().setTimeout(50000));
                    log.info("Clicked 'Live Betting' using text selector");
                } else {
                    throw new Exception("Text selector failed");
                }
            } catch (Exception e2) {
                log.info("Text selector failed, trying accessibility role...");

                try {
                    page.getByRole(AriaRole.LINK,
                                    new Page.GetByRoleOptions().setName("Live Betting").setExact(true))
                            .click(new Locator.ClickOptions().setTimeout(50000));
                    log.info("Clicked 'Live Betting' using getByRole (accessibility)");
                } catch (Exception e3) {
                    throw new NavigationException("Failed to click 'Live Betting' tab using all fallback strategies", e3);
                }
            }
        }

        try {
            page.waitForURL(url -> url.toString().contains("/sport/live/"),
                    new Page.WaitForURLOptions().setTimeout(timeout));

            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(50000));

            log.info("Successfully navigated to Live Betting page: " + page.url());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Live Betting page after clicking. Current URL: " + page.url(), e);
        }
    }


    /**
     * Main method: Enter Multi View then switch to the correct live sport
     */
    public static void navigateToSportPage(Page page, Sport configuredSport) throws InterruptedException {
        log.info("Attempting to click Multi View...");

        String[] selectors = {
                "a[href='/ng/sport/football/live_list/']",
                "span[data-cms-key='multi_view']",
                "text=Multi View"
        };

        boolean clicked = false;
        for (String selector : selectors) {
            try {
                Locator element = withLocatorRetry(
                        page, selector,
                        loc -> {
                            if (loc.count() > 0 && loc.first().isVisible()) {
                                return loc;
                            }
                            return null;
                        },
                        RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
                );

                if (element != null && element.count() > 0 && element.first().isVisible()) {
                    element.first().scrollIntoViewIfNeeded();
                    randomHumanDelay(1500, 3000);
                    element.first().click(new Locator.ClickOptions().setTimeout(120_000));
                    clicked = true;
                    log.info("✅ Clicked Multi View");
                    break;
                }
            } catch (Exception e) {
                log.debug("Selector failed: {}", selector);
            }
        }

        if (!clicked) {
            throw new RuntimeException("Could not click Multi View");
        }

        String currentUrl = page.url();
        if (!currentUrl.contains("/football/live_list")) {
            throw new RuntimeException("Failed to navigate to Multi View. URL: " + currentUrl);
        }

        log.info("✅ Multi View loaded: {}", currentUrl);
        randomHumanDelay(2500, 3000);

            switch (configuredSport) {
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
     * Universal sport switcher – works for EVERY sport on SportyBet
     */
    private static void switchToLiveSport(Page page, String sportInput) throws InterruptedException {
        String sport = sportInput.trim();
        String displayName;
        String urlSegment;

        switch (sport.toLowerCase()) {
            case "football" -> {
                displayName = "Football";
                urlSegment = "football";
            }
            case "basketball" -> {
                displayName = "Basketball";
                urlSegment = "basketball";
            }
            case "table tennis", "table-tennis", "tt", "tabletennis" -> {
                displayName = "Table Tennis";
                urlSegment = "tableTennis";
            }
            case "tennis" -> {
                displayName = "Tennis";
                urlSegment = "tennis";
            }
            default -> {
                displayName = sport.substring(0, 1).toUpperCase() + sport.substring(1).toLowerCase();
                urlSegment = displayName.toLowerCase().replace(" ", "-");
            }
        }

        log.info("Switching to live sport: {} → {}", displayName, urlSegment);

        boolean isVisibleSport = Set.of("Football", "Basketball", "Tennis", "vFootball", "eFootball")
                .contains(displayName);

        if (isVisibleSport) {
            clickVisibleSportTab(page, displayName, urlSegment);
        } else {
            clickSportViaMoreSportsDropdown(page, displayName, urlSegment);
        }

        // Final confirmation
        page.waitForSelector(".match-row, .m-content-row, .live-match",
                new Page.WaitForSelectorOptions().setTimeout(12_000));

        randomHumanDelay(2200, 4200);
        log.info("{} Multi View ready! URL: {}", displayName, page.url());
    }



    /**
     * Click visible sport tab (Football, Basketball, Tennis, etc.)
     */
    private static void clickVisibleSportTab(Page page, String displayName, String urlSegment) throws InterruptedException {
        log.info("Clicking visible sport tab: {}", displayName);

        String selector = """
    div.sport-name-item:has(div.text:has-text("%s"))
    """.formatted(displayName);

        Locator sportTab = withLocatorRetry(
                page, selector,
                loc -> {
                    loc.first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(120000));
                    return loc.first();
                },
                RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
        );

        if (sportTab == null) {
            throw new RuntimeException("Could not find sport tab: " + displayName);
        }

        sportTab.scrollIntoViewIfNeeded();
        randomHumanDelay(2000, 3000);

        sportTab.click(new Locator.ClickOptions()
                .setTimeout(12000)
                .setForce(false)
        );

        page.waitForURL(url -> {
            String urlStr = url.toString().toLowerCase();
            return urlStr.contains("/" + urlSegment.toLowerCase() + "/")
                    && urlStr.contains("live_list");
        }, new Page.WaitForURLOptions().setTimeout(20000));

        log.info("Successfully switched to {} → {}", displayName, page.url());
    }


    /**
     * Click sport via More Sports dropdown (Table Tennis, etc.)
     */
    /**
     * Click sport via More Sports dropdown (Table Tennis, Basketball, etc.)
     */
    private static void clickSportViaMoreSportsDropdown(Page page, String displayName, String urlSegment) throws InterruptedException {
        log.info("Opening 'More Sports' dropdown to select: {}", displayName);

        // === 1. Click to open dropdown ===
        boolean opened = false;
        String[] dropdownSelectors = {
                ".select-title",
                ".sport-simple-select .select-title",
                ".simple-select-wrap .select-title",
                "p.select-title__label",
                "p:has-text('More Sports')"
        };

        for (String selector : dropdownSelectors) {
            try {
                Locator dropdown = withLocatorRetry(
                        page, selector,
                        loc -> {
                            if (loc.count() > 0 && loc.first().isVisible()) {
                                return loc;
                            }
                            return null;
                        },
                        RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
                );

                if (dropdown != null && dropdown.count() > 0 && dropdown.first().isVisible()) {
                    log.info("Found dropdown using: {}", selector);
                    dropdown.first().scrollIntoViewIfNeeded();
                    randomHumanDelay(2000, 3500);
                    dropdown.first().click(new Locator.ClickOptions().setTimeout(10_000));
                    opened = true;
                    log.info("✅ Opened 'More Sports' dropdown");
                    break;
                }
            } catch (PlaywrightException e) {
                log.debug("Dropdown selector '{}' failed: {}", selector, e.getMessage());
            }
        }

        if (!opened) {
            throw new RuntimeException("Failed to open 'More Sports' dropdown - element not found");
        }

        randomHumanDelay(800, 1600);

        // === 2. Wait for dropdown list to become visible ===
        try {
            withLocatorRetry(
                    page, ".select-list",
                    loc -> {
                        page.waitForSelector(".select-list",
                                new Page.WaitForSelectorOptions()
                                        .setTimeout(8000)
                                        .setState(WaitForSelectorState.VISIBLE));
                        return loc;
                    },
                    RETRY_MAX_ATTEMPTS, 8000, RETRY_DELAY_MS
            );
            log.info("✅ Dropdown list is now visible");
        } catch (PlaywrightException e) {
            log.error("❌ Dropdown list did not appear within 8 seconds");
            throw new RuntimeException("Dropdown list failed to open", e);
        }

        // === 3. Find and click the specific sport ===
        try {
            Locator allSports = withLocatorRetry(
                    page, ".select-list .select-item",
                    loc -> loc,
                    RETRY_MAX_ATTEMPTS, RETRY_TIMEOUT_MS, RETRY_DELAY_MS
            );

            if (allSports == null) {
                throw new RuntimeException("Could not locate sport items in dropdown");
            }

            int totalSports = allSports.count();
            log.info("Found {} sports in dropdown", totalSports);

            Locator sportItem = allSports.filter(new Locator.FilterOptions()
                    .setHasText(displayName));

            if (sportItem.count() == 0) {
                log.error("❌ Sport '{}' not found in dropdown", displayName);

                log.warn("Available sports in dropdown:");
                for (int i = 0; i < Math.min(totalSports, 25); i++) {
                    String text = allSports.nth(i).textContent().trim();
                    log.warn("  [{}] '{}'", i, text);
                }

                throw new RuntimeException("Sport not found in dropdown: " + displayName);
            }

            log.info("✅ Found sport: '{}'", displayName);

            sportItem.first().scrollIntoViewIfNeeded();
            randomHumanDelay(2000, 3000);

            sportItem.first().click(new Locator.ClickOptions()
                    .setTimeout(10_000)
                    .setForce(true));

            log.info("✅ Clicked on: {}", displayName);

        } catch (PlaywrightException e) {
            log.error("❌ Failed to click sport '{}': {}", displayName, e.getMessage());
            throw new RuntimeException("Failed to select sport from dropdown", e);
        }

        // === 4. Wait for navigation ===
        try {
            page.waitForURL(
                    url -> url.contains("/" + urlSegment + "/live_list"),
                    new Page.WaitForURLOptions().setTimeout(15_000)
            );
            log.info("✅ Navigated to {} Multi View: {}", displayName, page.url());

        } catch (PlaywrightException e) {
            String currentUrl = page.url();
            log.warn("⚠️ URL wait timed out. Current URL: {}", currentUrl);

            if (currentUrl.contains("/" + urlSegment + "/live_list")) {
                log.info("✅ Already on {} page (URL check passed)", displayName);
            } else {
                log.error("❌ Navigation failed. Expected: '{}', Current URL: {}",
                        urlSegment, currentUrl);
                throw new RuntimeException("Failed to navigate to " + displayName + " page", e);
            }
        }

        randomHumanDelay(1000, 2000);
    }



    public static void returnToSportPage(Page page, Sport configuredSport) {
        page.goBack(new Page.GoBackOptions().setTimeout(15000));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        log.info("{} Returned to previous page {}", EMOJI_NAVIGATION, configuredSport);

    }

    public static boolean navigateToGame(Page page, BettingTask task) {
        String home = task.homeTeam().trim();
        String away = task.awayTeam().trim();
        String fullMatch = home + "vs" + away;

        log.info("{} Navigating to: {} | EventId: {}", EMOJI_BET, fullMatch, task.taskId());

        try {
            randomHumanDelay(100, 150);

            if (tryDirectNavigation(page, task)) return true;

        } catch (Exception e) {
            log.error("{} Navigation crashed: {}", EMOJI_ERROR, e.toString());
        }

        log.warn("{} All navigation methods failed for: {}", EMOJI_WARNING, fullMatch);
        return false;
    }


//     / * METHOD 1: Direct click in Multi View / Live List (FASTEST & MOST RELIABLE)
//     * Uses multiple strategies to find and click the correct match
//     */
private static boolean tryDirectNavigation(Page page, BettingTask task) {
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
                .setWaitUntil(WaitUntilState.NETWORKIDLE));

        log.info("✅ Successfully navigated to match page...");
        return true;

    } catch (Exception e) {
        log.error("❌ Direct navigation failed: {}", e.getMessage(), e);
        return false;
    }
}

//    /**
//     * Strategy 1: Click using title attribute (most reliable)
//     * Title format: "Team1 vs Team2" or "Team1 - Team2"
//     */
//    private static boolean tryClickByTitle(Page page, String home, String away) {
//        log.info("Strategy 1: Searching by title attribute");
//
//        // Try different title formats
//        String[] titlePatterns = {
//                String.format("%s vs %s", home, away),           // "Baron, Mariusz vs Urban, Wojciech"
//                String.format("%s - %s", home, away),            // Alternative separator
//                String.format("%s v %s", home, away),            // Short form
//                String.format("%s Vs %s", home, away)            // Capital Vs
//        };
//
//        for (String titlePattern : titlePatterns) {
//            try {
//                // Exact title match
//                Locator match = page.locator(String.format(".teams[title='%s']", titlePattern));
//
//                if (match.count() > 0 && match.first().isVisible()) {
//                    log.info("✅ Found by exact title: '{}'", titlePattern);
//                    return clickMatchElement(page, match.first());
//                }
//
//                // Case-insensitive title match
//                match = page.locator(String.format(".teams[title='%s' i]", titlePattern));
//
//                if (match.count() > 0 && match.first().isVisible()) {
//                    log.info("✅ Found by case-insensitive title: '{}'", titlePattern);
//                    return clickMatchElement(page, match.first());
//                }
//
//            } catch (PlaywrightException e) {
//                log.debug("Title pattern '{}' failed: {}", titlePattern, e.getMessage());
//            }
//        }
//
//        // Try partial title matching (contains both teams)
//        try {
//            String selector = String.format(
//                    ".teams[title*='%s' i][title*='%s' i]",
//                    escapeForSelector(home),
//                    escapeForSelector(away)
//            );
//
//            Locator match = page.locator(selector);
//
//            if (match.count() > 0 && match.first().isVisible()) {
//                log.info("✅ Found by partial title match");
//                return clickMatchElement(page, match.first());
//            }
//        } catch (PlaywrightException e) {
//            log.error("Partial title matching failed: {}", e.getMessage());
//        }
//
//        log.error("❌ Title strategy failed");
//        return false;
//    }
//
//    /**
//     * Strategy 2: Click by finding home and away team text
//     */
//    private static boolean tryClickByTeamText(Page page, String home, String away) {
//        log.debug("Strategy 2: Searching by team text");
//
//        try {
//            // Find all teams containers
//            Locator allTeams = page.locator(".teams");
//            int count = allTeams.count();
//
//            log.debug("Found {} team containers to check", count);
//
//            // Check each teams container
//            for (int i = 0; i < count; i++) {
//                Locator teamsContainer = allTeams.nth(i);
//
//                try {
//                    // Get home and away team text
//                    Locator homeTeam = teamsContainer.locator(".home-team");
//                    Locator awayTeam = teamsContainer.locator(".away-team");
//
//                    if (homeTeam.count() > 0 && awayTeam.count() > 0) {
//                        String homeText = homeTeam.first().textContent().trim();
//                        String awayText = awayTeam.first().textContent().trim();
//
//                        // Exact match
//                        if (homeText.equals(home) && awayText.equals(away)) {
//                            log.info("✅ Found by exact team text match");
//                            return clickMatchElement(page, teamsContainer);
//                        }
//
//                        // Case-insensitive match
//                        if (homeText.equalsIgnoreCase(home) && awayText.equalsIgnoreCase(away)) {
//                            log.info("✅ Found by case-insensitive team text match");
//                            return clickMatchElement(page, teamsContainer);
//                        }
//                    }
//                } catch (Exception e) {
//                    log.debug("Error checking container {}: {}", i, e.getMessage());
//                }
//            }
//
//        } catch (Exception e) {
//            log.debug("Team text strategy error: {}", e.getMessage());
//        }
//
//        log.debug("❌ Team text strategy failed");
//        return false;
//    }
//
//    /**
//     * Strategy 3: Partial matching (case-insensitive, ignores extra spaces)
//     */
//    private static boolean tryClickByPartialMatch(Page page, String home, String away) {
//        log.info("Strategy 3: Partial matching");
//
//        try {
//            // Normalize team names (remove extra spaces, lowercase)
//            String homeNorm = normalizeTeamName(home);
//            String awayNorm = normalizeTeamName(away);
//
//            Locator allTeams = page.locator(".teams");
//            int count = allTeams.count();
//
//            for (int i = 0; i < count; i++) {
//                Locator teamsContainer = allTeams.nth(i);
//
//                try {
//                    String fullText = teamsContainer.textContent().trim();
//                    String fullTextNorm = normalizeTeamName(fullText);
//
//                    // Check if both teams are present in the text
//                    if (fullTextNorm.contains(homeNorm) && fullTextNorm.contains(awayNorm)) {
//                        log.info("✅ Found by partial match in text: '{}'", fullText);
//                        return clickMatchElement(page, teamsContainer);
//                    }
//
//                } catch (Exception e) {
//                    log.error("Error checking container {}: {}", i, e.getMessage());
//                }
//            }
//
//        } catch (Exception e) {
//            log.error("Partial match strategy error: {}", e.getMessage());
//        }
//
//        log.error("❌ Partial match strategy failed");
//        return false;
//    }
//
//    /**
//     * Strategy 4: Fuzzy matching (handles name variations)
//     */
//    private static boolean tryClickByFuzzyMatch(Page page, String home, String away) {
//        log.debug("Strategy 4: Fuzzy matching");
//
//        try {
//            // Extract key parts of names (last names for players, main words for teams)
//            String homeKey = extractKeyName(home);
//            String awayKey = extractKeyName(away);
//
//            log.info("Fuzzy search - Home key: '{}', Away key: '{}'", homeKey, awayKey);
//
//            Locator allTeams = page.locator(".teams");
//            int count = allTeams.count();
//
//            for (int i = 0; i < count; i++) {
//                Locator teamsContainer = allTeams.nth(i);
//
//                try {
//                    String fullText = teamsContainer.textContent().toLowerCase().trim();
//
//                    // Check if key parts are present
//                    if (fullText.contains(homeKey.toLowerCase()) &&
//                            fullText.contains(awayKey.toLowerCase())) {
//                        log.info("✅ Found by fuzzy match (keys: '{}' + '{}')", homeKey, awayKey);
//                        return clickMatchElement(page, teamsContainer);
//                    }
//
//                } catch (Exception e) {
//                    log.debug("Error checking container {}: {}", i, e.getMessage());
//                }
//            }
//
//        } catch (Exception e) {
//            log.debug("Fuzzy match strategy error: {}", e.getMessage());
//        }
//
//        log.debug("❌ Fuzzy match strategy failed");
//        return false;
//    }
//
//    /**
//     * Actually click the match element and verify navigation
//     */
//    private static boolean clickMatchElement(Page page, Locator matchElement) {
//        try {
//            // Ensure element is in viewport
//            matchElement.scrollIntoViewIfNeeded();
//            randomHumanDelay(100, 150);
//
//            // Verify it's still visible
//            if (!matchElement.isVisible()) {
//                log.info("⚠️ Element not visible after scroll");
//                return false;
//            }
//
//            // Get match info for logging
//            String matchInfo = "unknown";
//            try {
//                matchInfo = matchElement.textContent().trim().replaceAll("\\s+", " ");
//            } catch (Exception e) {
//                // Ignore
//            }
//
//            log.info("Clicking match: {}", matchInfo);
//
//            // Click with retry logic
//            int maxAttempts = 3;
//            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
//                try {
//                    matchElement.click(new Locator.ClickOptions()
//                            .setTimeout(10_000)
//                            .setForce(attempt > 1)); // Force on retry
//
//                    break; // Success
//
//                } catch (PlaywrightException e) {
//                    if (attempt == maxAttempts) {
//                        throw e;
//                    }
//                    log.error("Click attempt {} failed, retrying...", attempt);
//                    randomHumanDelay(500, 1000);
//                }
//            }
//
//            // Wait for navigation with multiple possible URL patterns
//            try {
//                page.waitForURL(url ->
//                                url.contains("_vs_") ||
//                                        url.contains("/sr:match:") ||
//                                        url.contains("/game/") ||
//                                        url.contains("/live/") && url.length() > page.url().length(),
//                        new Page.WaitForURLOptions()
//                                .setTimeout(15000)
//                                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
//                );
//
//                log.info("✅ Navigation successful: {}", page.url());
//
//                // Extra verification - wait for match content to load
//                try {
//                    // Primary: Wait for the main wrapper that contains ALL odds tables
//                    page.waitForSelector(".m-detail-wrapper", new Page.WaitForSelectorOptions()
//                            .setTimeout(3000));
//
//                    log.info("✅ Match content loaded - .m-detail-wrapper detected");
//
//                } catch (TimeoutError e) {
//                    // Fallback 1: Look for any odds table (even if wrapper changed)
//                    try {
//                        page.waitForSelector(".m-table__wrapper, .m-table-row.m-outcome",
//                                new Page.WaitForSelectorOptions().setTimeout(5000));
//                        log.info("✅ Match content detected via fallback (odds tables present)");
//                    } catch (TimeoutError e2) {
//                        // Fallback 2: Look for navigation tabs (All, Main, Game)
//                        try {
//                            page.waitForSelector(".m-nav-item", new Page.WaitForSelectorOptions()
//                                    .setTimeout(3000));
//                            log.info("✅ Match content detected - navigation tabs present");
//                        } catch (TimeoutError e3) {
//                            log.warn("⚠️ Match content not detected - page may have changed or is slow");
//                            // Continue anyway — sometimes odds load later
//                        }
//                    }
//                }
//
//                return true;
//
//            } catch (PlaywrightException e) {
//                log.error("⚠️ Navigation timeout, but checking if we're on match page anyway");
//
//                // Check if URL changed at all
//                String currentUrl = page.url();
//                if (currentUrl.contains("_vs_") ||
//                        currentUrl.contains("/sr:match:") ||
//                        currentUrl.contains("/live/")) {
//                    log.info("✅ On match page despite timeout: {}", currentUrl);
//                    return true;
//                }
//
//                log.error("❌ Navigation failed. Current URL: {}", currentUrl);
//                return false;
//            }
//
//        } catch (Exception e) {
//            log.error("❌ Click error: {}", e.getMessage());
//            String currentUrl = page.url();
//            if (currentUrl.contains("_vs_") ||
//                    currentUrl.contains("/sr:match:") ||
//                    currentUrl.contains("/live/")) {
//                log.info("✅ On match page despite timeout:- {}", currentUrl);
//                return true;
//            }
//            return false;
//        }
//    }
//
//    /**
//     * Helper: Normalize team name (lowercase, remove extra spaces)
//     */
//    private static String normalizeTeamName(String name) {
//        if (name == null) return "";
//        return name.toLowerCase()
//                .replaceAll("\\s+", " ")
//                .trim();
//    }
//
//    /**
//     * Helper: Extract key part of name (last name for players, main word for teams)
//     */
//    private static String extractKeyName(String fullName) {
//        if (fullName == null || fullName.isEmpty()) {
//            return "";
//        }
//
//        // For player names like "Baron, Mariusz" - take the part before comma
//        if (fullName.contains(",")) {
//            return fullName.split(",")[0].trim();
//        }
//
//        // For team names - take the last significant word
//        String[] parts = fullName.trim().split("\\s+");
//        if (parts.length > 0) {
//            return parts[parts.length - 1];
//        }
//
//        return fullName;
//    }
//
//
//    /**
//     * Helper: Escape special characters for CSS selector
//     */
//    private static String escapeForSelector(String text) {
//        if (text == null) return "";
//
//        // Escape characters that have special meaning in CSS selectors
//        return text.replace("'", "\\'")
//                .replace("\"", "\\\"")
//                .replace("[", "\\[")
//                .replace("]", "\\]");
//    }
//
//    /**
//     * Debug helper: Log all available matches on the page
//     */
//    private static void logAvailableMatches(Page page) {
//        try {
//            log.info("=== Available Matches on Page ===");
//
//            Locator allMatches = page.locator(".teams");
//            int count = allMatches.count();
//
//            log.info("Found {} match containers", count);
//
//            for (int i = 0; i < Math.min(count, 20); i++) { // Limit to first 20
//                try {
//                    Locator match = allMatches.nth(i);
//
//                    String title = "";
//                    try {
//                        title = match.getAttribute("title");
//                    } catch (Exception e) {
//                        title = "(no title)";
//                    }
//
//                    String homeTeam = "";
//                    String awayTeam = "";
//                    try {
//                        homeTeam = match.locator(".home-team").first().textContent().trim();
//                        awayTeam = match.locator(".away-team").first().textContent().trim();
//                    } catch (Exception e) {
//                        // Ignore
//                    }
//
//                    log.info("[{}] Title: '{}' | Home: '{}' | Away: '{}'",
//                            i, title, homeTeam, awayTeam);
//
//                } catch (Exception e) {
//                    log.debug("Error reading match {}: {}", i, e.getMessage());
//                }
//            }
//
//            if (count > 20) {
//                log.info("... and {} more matches", count - 20);
//            }
//
//            log.info("===================================");
//
//        } catch (Exception e) {
//            log.warn("Could not log available matches: {}", e.getMessage());
//        }
//    }


    private static  <T> T withLocatorRetry(Page page, String selector, Function<Locator, T> action,
                                   int maxRetries, long timeoutPerAttemptMs, long delayMs) {
        Locator locator = page.locator(selector);
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return action.apply(locator);  // e.g., locator::click, locator::textContent, etc.
            } catch (TimeoutError te) {
                log.warn("Timeout attempt {} on '{}'", attempt, selector);
//                if (attempt == maxRetries) throw te;
//                page.waitForTimeout(delayMs);
            }
        }
        log.info("returning null for selector {}", selector);
        return null;  // Never reached
    }


    public static void randomHumanDelay(long minMs, long maxMs) {
        try {
            long delay = minMs + ThreadLocalRandom.current().nextLong(maxMs - minMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
