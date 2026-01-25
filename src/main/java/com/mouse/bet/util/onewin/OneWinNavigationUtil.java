package com.mouse.bet.util.onewin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.exception.GameNotFoundException;
import com.mouse.bet.interfaces.BettingTask;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class OneWinNavigationUtil {

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

    /**
     * Wait for page to be fully loaded and ready
     */
    public static void waitForPageReady(Page page) throws Exception {
        log.info("{} {} Waiting for page to be ready...", EMOJI_CLOCK, EMOJI_HEALTH);

        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
//        page.waitForFunction("document.readyState === 'complete'");
        page.waitForSelector("body", new Page.WaitForSelectorOptions().setTimeout(10000));
    }

//    public static void performLogin(Page page, String oneUsername, String onePassword) {
//    }
//
//    public static boolean checkIfLoggedIn(Page page) {
//        return false;
//    }

    public static void navigateToLiveEvents(Page page) {
    }
    public static void navigateToSportPage(Page page, Sport configuredSport) {
        try {
            log.info("Navigating to sport page: {}", configuredSport);

            // Map Sport enum to the data-w attribute values
            String sportSelector = getSportSelector(configuredSport);

            if (sportSelector == null) {
                log.error("Unsupported sport: {}", configuredSport);
                throw new IllegalArgumentException("Sport not supported: " + configuredSport);
            }

            // Locate the sport button
            Locator sportButton = page.locator(sportSelector);

            // Wait for the button to be visible
            sportButton.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));

            // Human-like pause before clicking (simulate looking for the sport)
            sleepRandom(500, 1200);

            // Scroll to the element if needed (more human-like)
            sportButton.scrollIntoViewIfNeeded();
            sleepRandom(200, 400);

            // Click the sport button
            sportButton.click();

            // Wait for page to load after navigation
            sleepRandom(800, 1500);

            log.info("Successfully navigated to {} page", configuredSport);

        } catch (PlaywrightException e) {
            log.error("Failed to navigate to sport page: {}", configuredSport, e);
            throw e;
        }
    }

    /**
     * Maps Sport enum to the corresponding data-w selector
     */
    private static String getSportSelector(Sport sport) {
        switch (sport) {
            case TOP:
                return "button[data-w='__TOP-quab']";
            case SOCCER:
            case FOOTBALL:
                return "button[data-w='18-quab']";
            case TENNIS:
                return "button[data-w='33-quab']";
            case BASKETBALL:
                return "button[data-w='23-quab']";
            case ICE_HOCKEY:
                return "button[data-w='35-quab']";
            case TABLE_TENNIS:
                return "button[data-w='24-quab']";
            case CS2:
            case COUNTER_STRIKE:
                return "button[data-w='142-quab']";
            case DOTA_2:
                return "button[data-w='47-quab']";
            case VOLLEYBALL:
                return "button[data-w='27-quab']";
            case BASEBALL:
                return "button[data-w='29-quab']";
            case CRICKET:
                return "button[data-w='25-quab']";
            case HANDBALL:
                return "button[data-w='34-quab']";
            default:
                return null;
        }
    }

    private static void sleepRandom(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

//    /**
//     * Navigate to the specified league
//     */
//    private static void navigateToLeague(Page page, String leagueName) {
//        try {
//            log.debug("Searching for league: {}", leagueName);
//
//            // Strategy 1: Try exact match with text content
//            String exactSelector = String.format("button[data-w*='-quab']:has-text('%s')", leagueName);
//            Locator leagueButton = page.locator(exactSelector).first();
//
//            // Check if league button is visible
//            try {
//                leagueButton.waitFor(new Locator.WaitForOptions()
//                        .setState(WaitForSelectorState.VISIBLE)
//                        .setTimeout(3000));
//
//                // Human-like behavior: pause before clicking
////                sleepRandom(5, 1000);
//
//                // Scroll to element if needed
//                leagueButton.scrollIntoViewIfNeeded();
//                sleepRandom(200, 400);
//
//                // Click the league button
//                leagueButton.click();
//
//                log.info("Successfully navigated to league: {}", leagueName);
//                return;
//
//            } catch (PlaywrightException e) {
//                log.debug("Exact match not found, trying partial match for: {}", leagueName);
//            }
//
//            // Strategy 2: Try partial match (case-insensitive)
//            Locator allLeagueButtons = page.locator("button[data-w*='-quab']");
//            int count = allLeagueButtons.count();
//
//            for (int i = 0; i < count; i++) {
//                Locator button = allLeagueButtons.nth(i);
//                String buttonText = button.textContent().trim();
//
//                // Case-insensitive partial match
//                if (buttonText.toLowerCase().contains(leagueName.toLowerCase())) {
//                    log.debug("Found league button with text: {}", buttonText);
//
//                    sleepRandom(100, 400);
//                    button.scrollIntoViewIfNeeded();
//                    sleepRandom(200, 400);
//                    button.click();
//
//                    log.info("Successfully navigated to league: {} (matched: {})",
//                            leagueName, buttonText);
//                    return;
//                }
//            }
//
//            // If we get here, league was not found
//            throw new IllegalStateException("League not found: " + leagueName);
//
//        } catch (PlaywrightException e) {
//            log.error("Failed to navigate to league: {}", leagueName, e);
//            throw e;
//        }
//    }
//
//    /**
//     * Find and click on the specific game based on home and away teams
//     * This will be implemented once you provide the game elements
//     */
//    /**
//     * Find and click game using Playwright locators (MORE RELIABLE)
//     */
//    private static void findAndClickGame(Page page, String homeTeam, String awayTeam) {
//        try {
//            log.debug("Searching for game with locators: {} vs {}", homeTeam, awayTeam);
//
//            // Normalize team names
//            String normalizedHome = normalizeText(homeTeam);
//            String normalizedAway = normalizeText(awayTeam);
//
//            // Get all match cards
//            Locator matchCards = page.locator("[data-qa='match-card']");
//
//            // Wait for at least one match card to be visible
//            matchCards.first().waitFor(new Locator.WaitForOptions()
//                    .setState(WaitForSelectorState.VISIBLE)
//                    .setTimeout(5000));
//
//            int totalCards = matchCards.count();
//            log.debug("Found {} match cards", totalCards);
//
//            // Iterate through all match cards
//            for (int i = 0; i < totalCards; i++) {
//                Locator card = matchCards.nth(i);
//
//                // Get team names from this card
//                Locator teamNames = card.locator("span._name_1b83e_7");
//                int teamCount = teamNames.count();
//
//                if (teamCount >= 2) {
//                    String team1Text = normalizeText(teamNames.nth(0).textContent());
//                    String team2Text = normalizeText(teamNames.nth(1).textContent());
//
//                    // Check if teams match (consider both orders)
//                    boolean matchFound = false;
//
//                    // Order 1: home vs away
//                    if (fuzzyMatch(team1Text, normalizedHome, 80) &&
//                            fuzzyMatch(team2Text, normalizedAway, 80)) {
//                        matchFound = true;
//                    }
//
//                    // Order 2: away vs home
//                    if (fuzzyMatch(team1Text, normalizedAway, 80) &&
//                            fuzzyMatch(team2Text, normalizedHome, 80)) {
//                        matchFound = true;
//                    }
//
//                    if (matchFound) {
//                        log.info("Found matching game: {} vs {}", team1Text, team2Text);
//
//                        // Human-like behavior before clicking
//                        sleepRandom(400, 500);
//
//                        // Scroll to the card
//                        card.scrollIntoViewIfNeeded();
//                        sleepRandom(200, 400);
//
//                        // Click the match card
//                        card.click();
//
//                        log.info("Successfully clicked game: {} vs {}", homeTeam, awayTeam);
//                        sleepRandom(200, 500);
//                        return;
//                    }
//                }
//            }
//
//            // If we get here, no match was found
//            throw new GameNotFoundException(
//                    String.format("Game not found: %s vs %s", homeTeam, awayTeam));
//
//        } catch (PlaywrightException e) {
//            log.error("Failed to find game: {} vs {}", homeTeam, awayTeam, e);
//            throw e;
//        }
//    }
//
//    /**
//     * Helper method to normalize text for comparison
//     */
//    private static String normalizeText(String text) {
//        if (text == null) return "";
//
//        return text.trim()
//                .toLowerCase()
//                .replaceAll("\\s+", " ")
//                .replaceAll("[^a-z0-9\\s]", "");
//    }
//
//    /**
//     * Fuzzy match with threshold (percentage match)
//     */
//    private static boolean fuzzyMatch(String text1, String text2, int threshold) {
//        String normalized1 = normalizeText(text1);
//        String normalized2 = normalizeText(text2);
//
//        // Exact match
//        if (normalized1.equals(normalized2)) {
//            return true;
//        }
//
//        // Contains match
//        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
//            return true;
//        }
//
//        // Calculate similarity (simple approach)
//        String[] words1 = normalized1.split(" ");
//        String[] words2 = normalized2.split(" ");
//
//        int matchingWords = 0;
//        for (String word1 : words1) {
//            for (String word2 : words2) {
//                if (word1.equals(word2) || word1.contains(word2) || word2.contains(word1)) {
//                    matchingWords++;
//                    break;
//                }
//            }
//        }
//
//        int totalWords = Math.max(words1.length, words2.length);
//        int similarityPercent = (matchingWords * 100) / totalWords;
//
//        return similarityPercent >= threshold;
//    }
//
//
//
//    /**
//     * Find and click game using JavaScript execution (FASTER)
//     */
//    private static void findAndClickGameJS(Page page, String homeTeam, String awayTeam) {
//        try {
//            log.debug("Searching for game using JS: {} vs {}", homeTeam, awayTeam);
//
//            // Normalize team names for comparison
//            String normalizedHome = normalizeText(homeTeam);
//            String normalizedAway = normalizeText(awayTeam);
//
//            // JavaScript to find the matching game card
//            String jsScript = String.format("""
//            (function() {
//                const homeTeam = '%s';
//                const awayTeam = '%s';
//
//                function normalize(text) {
//                    return text.toLowerCase()
//                        .trim()
//                        .replace(/\\s+/g, ' ')
//                        .replace(/[^a-z0-9\\s]/g, '');
//                }
//
//                const normalizedHome = normalize(homeTeam);
//                const normalizedAway = normalize(awayTeam);
//
//                // Get all match cards
//                const matchCards = document.querySelectorAll('[data-qa="match-card"]');
//
//                for (const card of matchCards) {
//                    // Get all team name spans
//                    const teamNames = card.querySelectorAll('span._name_1b83e_7');
//
//                    if (teamNames.length >= 2) {
//                        const team1 = normalize(teamNames[0].textContent);
//                        const team2 = normalize(teamNames[1].textContent);
//
//                        // Check if teams match (in any order)
//                        const match1 = (team1.includes(normalizedHome) || normalizedHome.includes(team1)) &&
//                                      (team2.includes(normalizedAway) || normalizedAway.includes(team2));
//                        const match2 = (team1.includes(normalizedAway) || normalizedAway.includes(team1)) &&
//                                      (team2.includes(normalizedHome) || normalizedHome.includes(team2));
//
//                        if (match1 || match2) {
//                            return card;
//                        }
//                    }
//                }
//
//                return null;
//            })();
//            """, normalizedHome, normalizedAway);
//
//            // Execute JavaScript and get the element
//            Object result = page.evaluate(jsScript);
//
//            if (result == null) {
//                throw new IllegalStateException(
//                        String.format("Game not found: %s vs %s", homeTeam, awayTeam));
//            }
//
//            // Click the found match card
//            page.locator("[data-qa='match-card']").evaluateAll(jsScript + """
//            .then(card => {
//                if (card) card.click();
//            });
//            """);
//
//            // Alternative: Use the element handle to click
//            Locator matchCards = page.locator("[data-qa='match-card']");
//            int count = matchCards.count();
//
//            for (int i = 0; i < count; i++) {
//                Locator card = matchCards.nth(i);
//                String cardText = card.textContent().toLowerCase();
//
//                if (cardText.contains(normalizedHome) && cardText.contains(normalizedAway)) {
//                    sleepRandom(300, 600);
//                    card.scrollIntoViewIfNeeded();
//                    sleepRandom(200, 400);
//                    card.click();
//
//                    log.info("Clicked on game: {} vs {}", homeTeam, awayTeam);
//                    return;
//                }
//            }
//
//            throw new GameNotFoundException(
//                    String.format("Game not found: %s vs %s", homeTeam, awayTeam));
//
//        } catch (PlaywrightException e) {
//            log.error("Failed to find game: {} vs {}", homeTeam, awayTeam, e);
//            throw e;
//        }
//    }



    public static void returnToSportPage(Page page, Sport configuredSport) {
        try {
            log.info("Returning to sport page: {}", configuredSport);

            // Locate the back button using multiple strategies
            Locator backButton = findBackButton(page);

            if (backButton == null || backButton.count() == 0) {
                log.warn("Back button not found, attempting alternative navigation...");
                // Alternative: Navigate directly to sport page
                navigateToSportPage(page, configuredSport);
                return;
            }

            // Human-like delay before clicking
            sleepRandom(400, 800);

            // Scroll to button if needed
            backButton.scrollIntoViewIfNeeded();
            sleepRandom(200, 400);

            // Click the back button
            try {
                backButton.click();
                log.info("Clicked back button successfully");
            } catch (PlaywrightException e) {
                log.warn("Regular click failed, trying force click...");
                backButton.click(new Locator.ClickOptions().setForce(true));
                log.info("Back button clicked with force");
            }

            // Wait for navigation to complete
            sleepRandom(1000, 1500);

            // Verify we're back on the sport page
            if (isOnSportPage(page, configuredSport)) {
                log.info("Successfully returned to {} page", configuredSport);
            } else {
                log.warn("May not be on correct sport page after clicking back");
            }

        } catch (Exception e) {
            log.error("Failed to return to sport page: {}", e.getMessage(), e);
            // Fallback: navigate directly
            try {
                log.info("Attempting direct navigation as fallback...");
                navigateToSportPage(page, configuredSport);
            } catch (Exception e2) {
                log.error("Fallback navigation also failed: {}", e2.getMessage(), e2);
            }
        }
    }

    /**
     * Find the back button using multiple selectors
     */
    private static Locator findBackButton(Page page) {
        // Try multiple selectors in order of specificity
        String[] selectors = {
                // Most specific: button with back text and arrow icon
                "button:has-text('Back'):has(svg[data-mirror-rtl])",

                // Button with exact classes and back text
                "button._root_9f102_8._variantNeutral_9f102_136:has-text('Back')",

                // Any button with "Back" text
                "button:has-text('Back')",

                // Button with back arrow SVG (path matches the specific path)
                "button:has(svg path[d*='M10.166 13.091'])",

                // Generic back button by aria or role
                "button[aria-label*='back' i]",
                "button[aria-label*='return' i]"
        };

        for (String selector : selectors) {
            try {
                Locator button = page.locator(selector).first();
                if (button.count() > 0 && button.isVisible()) {
                    log.debug("Found back button using selector: {}", selector);
                    return button;
                }
            } catch (Exception e) {
                // Continue to next selector
                continue;
            }
        }

        log.warn("Back button not found with any selector");
        return null;
    }

    /**
     * Check if currently on the sport page
     */
    private static boolean isOnSportPage(Page page, Sport configuredSport) {
        try {
            // Check if we can see the sport navigation or game listings
            String sportSelector = getSportSelector(configuredSport);

            if (sportSelector != null) {
                Locator sportButton = page.locator(sportSelector);
                if (sportButton.count() > 0) {
                    // Check if the sport button is selected/active
                    String classes = sportButton.getAttribute("class");
                    boolean isSelected = classes != null && classes.contains("selected");
                    return isSelected;
                }
            }

            // Alternative: Check if match cards are visible (indicates we're on sport page)
            Locator matchCards = page.locator("[data-qa='match-card']");
            return matchCards.count() > 0;

        } catch (Exception e) {
            log.debug("Error checking if on sport page: {}", e.getMessage());
            return false;
        }
    }




}
