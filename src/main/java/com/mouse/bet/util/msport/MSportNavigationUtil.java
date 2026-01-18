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

import java.util.List;
import java.util.Map;
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


    @Value("${msport.login.url:https://www.msport.com/ng/web}")
    private static String loginUrl;

    @Value("${msport.live.events.url:https://www.msport.com/ng/web/live_matches}")
    private static String liveEventsUrl;

    /**
     * Navigate to MSport bookmaker's homepage
     */
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
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));

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
    public static void navigateToSportPage(Page page, Sport sport) throws Exception {
        log.info("{} {} Navigating to sport page: {}", EMOJI_NAVIGATION, EMOJI_SEARCH, sport);

        String currentUrl = page.url();
        if (!currentUrl.contains("/default/live_matches")) {
            throw new RuntimeException("Not on default Live Sport page. URL: " + currentUrl);
        }

        log.info("✅ Sport Page loaded: {}", currentUrl);

        // Navigate to target sport
        switch (sport) {
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
    public static boolean navigateToGame(Page page, BettingTask task)  {
        String home = task.homeTeam().trim();
        String away = task.awayTeam().trim();
        String fullMatch = home + " vs " + away;

        log.info("{} Navigating to: {}", EMOJI_BET, fullMatch);

        randomHumanDelay(500, 1500);

        if (!tryDirectNavigation(page, home, away, task)) {
            log.info("Failed to navigate to game: {} " , fullMatch);
            return false;
        }
        return true;
    }

    /**
     * Direct click in MSport event list (uses multiple strategies)
     */
    private static boolean tryDirectNavigation(Page page, String home, String away, BettingTask task) {
        log.info("🎯 Searching for match: {} vs {}", home, away);

        try {
//            if (tryClickByAriaLabel(page, home, away)) return true;
//            if (tryClickByHref(page, home, away)) return true;
            if (tryClickByLeagueAndTeamTextJs(page, task.leagueName(), home, away)) return true;
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
     * Strategy 1: Click by searching aria-label attribute with multiple patterns
     */
    private static boolean tryClickByAriaLabel(Page page, String home, String away) {
        log.info("Strategy 1: Searching by aria-label attribute");

        String[] labelPatterns = {
                String.format("%s vs %s", home, away),
                String.format("%s - %s", home, away),
                String.format("%s v %s", home, away)
        };

        for (String labelPattern : labelPatterns) {
            log.debug("Trying aria-label pattern: '{}'", labelPattern);

            try {
                Locator match = page.locator(".m-teams a").filter(
                        new Locator.FilterOptions().setHasText(labelPattern)
                );

                // Alternative: direct aria-label selector
                // Locator match = page.locator(String.format("a[aria-label='%s']", labelPattern));

                if (match.count() > 0) {
                    Locator firstMatch = match.first();

                    if (firstMatch.isVisible()) {
                        log.info("✅ Found by aria-label: '{}'", labelPattern);
                        return clickMatchElement(page, firstMatch);
                    } else {
                        log.debug("Match found but not visible for pattern: '{}'", labelPattern);
                    }
                }
            } catch (PlaywrightException e) {
                log.debug("aria-label pattern '{}' failed: {}", labelPattern, e.getMessage());
            }
        }

        log.debug("❌ aria-label strategy failed - no matches found");
        return false;
    }

    /**
     * Strategy 2: Click by matching href pattern
     */
    private static boolean tryClickByHref(Page page, String home, String away) {
        log.info("Strategy 2: Searching by href pattern");

        try {
            // Normalize team names for URL matching
            String homeUrlPart = normalizeForUrl(home);
            String awayUrlPart = normalizeForUrl(away);
            String hrefPattern = String.format("%s_vs_%s", homeUrlPart, awayUrlPart);

            log.debug("Looking for href pattern: '{}'", hrefPattern);

            Locator match = page.locator(String.format(".m-teams a[href*='%s']", hrefPattern));

            if (match.count() > 0) {
                Locator firstMatch = match.first();

                if (firstMatch.isVisible()) {
                    log.info("✅ Found by href pattern: '{}'", hrefPattern);
                    return clickMatchElement(page, firstMatch);
                } else {
                    log.debug("Match found but not visible for href pattern: '{}'", hrefPattern);
                }
            } else {
                log.debug("No matches found for href pattern: '{}'", hrefPattern);
            }

        } catch (PlaywrightException e) {
            log.debug("href strategy error: {}", e.getMessage());
        }

        log.debug("❌ href strategy failed");
        return false;
    }

    /**
     * Normalizes team name for URL matching
     * Removes spaces, commas, and other special characters commonly stripped in URLs
     */
    private static String normalizeForUrl(String teamName) {
        return teamName
                .replace(" ", "_")
                .replace(",", "")
                .replace("'", "")
                .replace(".", "");
    }


    private static boolean tryClickByLeagueAndTeamText(Page page, String leagueName, String home, String away) {
        log.info("Strategy 3: Searching for match in league '{}': {} vs {}", leagueName, home, away);
        try {
            // Get all tournaments on the page
            Locator allTournaments = page.locator(".m-tournament");
            int tournamentCount = allTournaments.count();

            log.debug("Found {} tournaments to search", tournamentCount);

            // Iterate through each tournament to find the matching league
            for (int t = 0; t < tournamentCount; t++) {
                try {
                    Locator tournament = allTournaments.nth(t);

                    // Get the tournament/league name
                    Locator tournamentTitle = tournament.locator(".category-tournament-title");
                    if (tournamentTitle.count() == 0) {
                        log.debug("Tournament {} has no title, skipping", t);
                        continue;
                    }

                    String fullTournamentText = tournamentTitle.textContent().trim();

                    // Clean up the tournament text (remove extra whitespace/newlines)
                    String cleanedTournamentText = fullTournamentText.replaceAll("\\s+", " ").trim();

                    log.debug("Tournament {}: '{}'", t, cleanedTournamentText);

                    // Check if this is the league we're looking for
                    if (!isLeagueMatch(cleanedTournamentText, leagueName)) {
                        log.debug("League '{}' doesn't match target '{}', skipping",
                                cleanedTournamentText, leagueName);
                        continue;
                    }

                    log.info("✅ Found target league: '{}'", cleanedTournamentText);

                    // Now search for the match within this tournament
                    Locator matchesInTournament = tournament.locator(".m-event .m-teams");
                    int matchCount = matchesInTournament.count();

                    log.debug("Searching {} matches in league '{}'", matchCount, cleanedTournamentText);

                    // Check each match in this tournament
                    for (int m = 0; m < matchCount; m++) {
                        try {
                            Locator teamsContainer = matchesInTournament.nth(m);
                            Locator teamWrappers = teamsContainer.locator(".m-server-name-wrapper");

                            if (teamWrappers.count() >= 2) {
                                String homeText = teamWrappers.nth(0)
                                        .locator(".tw-w-full.tw-truncate")
                                        .textContent()
                                        .trim();

                                String awayText = teamWrappers.nth(1)
                                        .locator(".tw-w-full.tw-truncate")
                                        .textContent()
                                        .trim();

                                log.debug("Checking match {}: {} vs {} (looking for {} vs {})",
                                        m, homeText, awayText, home, away);

                                if (homeText.equalsIgnoreCase(home) && awayText.equalsIgnoreCase(away)) {
                                    log.info("✅ Found exact match in '{}': {} vs {}",
                                            cleanedTournamentText, homeText, awayText);

                                    // Click the link within this teams container
                                    Locator clickTarget = teamsContainer.locator("a").first();
                                    return clickMatchElement(page, clickTarget);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Error checking match {} in league '{}': {}",
                                    m, cleanedTournamentText, e.getMessage());
                        }
                    }

                    log.debug("Match not found in league '{}', continuing search...", cleanedTournamentText);

                } catch (Exception e) {
                    log.debug("Error checking tournament {}: {}", t, e.getMessage());
                }
            }

            log.warn("❌ Match not found: {} vs {} in league '{}'", home, away, leagueName);
            return false;

        } catch (Exception e) {
            log.error("League and team text strategy error: {}", e.getMessage());
            return false;
        }
    }



    private static boolean isLeagueMatch(String fullTournamentText, String targetLeague) {
        if (fullTournamentText == null || targetLeague == null) {
            return false;
        }

        // Normalize both strings once
        String tournament = normalize(fullTournamentText);
        String target = normalize(targetLeague);

        // Early exit for empty strings after normalization
        if (tournament.isEmpty() || target.isEmpty()) {
            return false;
        }

        // Exact match - O(n)
        if (tournament.equals(target)) {
            log.debug("Exact match: '{}' == '{}'", fullTournamentText, targetLeague);
            return true;
        }

        // Contains match - O(n*m) but optimized with built-in Boyer-Moore variant
        if (tournament.contains(target) || target.contains(tournament)) {
            log.debug("Contains match: '{}' and '{}'", fullTournamentText, targetLeague);
            return true;
        }

        return false;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text.toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")  // Replace all non-alphanumeric chars with space
                .trim()                          // Remove leading/trailing spaces
                .replaceAll("\\s+", " ");       // Collapse multiple spaces to single space
    }




    private static boolean tryClickByLeagueAndTeamTextJs(Page page, String leagueName, String home, String away) {
        log.info("Strategy 3: Searching for match in league '{}': {} vs {}", leagueName, home, away);
        try {
            // Use JavaScript to find the match and return RELATIVE href
            Object result = page.evaluate("""
    (params) => {
        const { leagueName, home, away } = params;
        const searchLog = [];
        
        // Enhanced normalization function to handle hyphens, dots, and special characters
        function normalize(text) {
            if (!text) return '';
            return text.toLowerCase()
                .replace(/\\s*-\\s*/g, ' ')    // Replace " - " or "-" with space
                .replace(/[.,]/g, '')           // Remove dots and commas
                .replace(/\\s+/g, ' ')          // Normalize multiple spaces to single space
                .trim();
        }
        
        const normalizedLeague = normalize(leagueName);
        searchLog.push(`Searching for league: '${leagueName}' (normalized: '${normalizedLeague}')`);
        
        // Get all tournaments
        const tournaments = document.querySelectorAll('.m-tournament');
        searchLog.push(`Total tournaments: ${tournaments.length}`);
        
        for (let i = 0; i < tournaments.length; i++) {
            const tournament = tournaments[i];
            
            // Get tournament title
            const titleElement = tournament.querySelector('.category-tournament-title');
            if (!titleElement) {
                searchLog.push(`Tournament ${i}: No title found`);
                continue;
            }
            
            const tournamentText = titleElement.textContent.replace(/\\s+/g, ' ').trim();
            const normalizedTournament = normalize(tournamentText);
            
            // Multiple matching strategies for flexibility
            const exactMatch = normalizedTournament === normalizedLeague;
            const containsMatch = normalizedTournament.includes(normalizedLeague) || 
                                 normalizedLeague.includes(normalizedTournament);
            
            // Word-based matching: all significant words from league appear in tournament
            const leagueWords = normalizedLeague.split(' ').filter(word => word.length > 2);
            const wordMatch = leagueWords.length > 0 && 
                             leagueWords.every(word => normalizedTournament.includes(word));
            
            const matches = exactMatch || containsMatch || wordMatch;
            
            if (!matches) {
                searchLog.push(`Tournament ${i}: '${tournamentText}' (normalized: '${normalizedTournament}') - no match`);
                continue;
            }
            
            searchLog.push(`Tournament ${i}: '${tournamentText}' (normalized: '${normalizedTournament}') - MATCHED! (league normalized: '${normalizedLeague}')`);
            
            // Search for teams in this tournament
            const matchElements = tournament.querySelectorAll('.m-event .m-teams');
            searchLog.push(`  - Found ${matchElements.length} matches in this league`);
            
            for (let j = 0; j < matchElements.length; j++) {
                const match = matchElements[j];
                const teamWrappers = match.querySelectorAll('.m-server-name-wrapper');
                
                if (teamWrappers.length >= 2) {
                    const homeText = teamWrappers[0].querySelector('.tw-w-full.tw-truncate')?.textContent.trim();
                    const awayText = teamWrappers[1].querySelector('.tw-w-full.tw-truncate')?.textContent.trim();
                    
                    searchLog.push(`  - Match ${j}: ${homeText} vs ${awayText}`);
                    
                    if (homeText && awayText &&
                        homeText.toLowerCase() === home.toLowerCase() &&
                        awayText.toLowerCase() === away.toLowerCase()) {
                        
                        searchLog.push(`  - TEAMS MATCHED!`);
                        
                        // Get the link element
                        const link = match.querySelector('a');
                        if (link) {
                            // Return absolute href for direct navigation
                            const absoluteHref = link.href;
                            
                            searchLog.push(`  - Match URL: ${absoluteHref}`);
                            
                            return {
                                found: true,
                                absoluteHref: absoluteHref,
                                league: tournamentText,
                                home: homeText,
                                away: awayText,
                                searchLog: searchLog
                            };
                        }
                    }
                }
            }
        }
        
        return { found: false, searchLog: searchLog };
    }
    """,
                    Map.of(
                            "leagueName", leagueName,
                            "home", home,
                            "away", away
                    )
            );

            // Parse the result
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;

            // Log search details
            @SuppressWarnings("unchecked")
            List<String> searchLog = (List<String>) resultMap.get("searchLog");
            if (searchLog != null) {
                searchLog.forEach(line -> log.debug(line));
            }

            if (Boolean.TRUE.equals(resultMap.get("found"))) {
                String absoluteHref = (String) resultMap.get("absoluteHref");
                String foundLeague = (String) resultMap.get("league");
                String foundHome = (String) resultMap.get("home");
                String foundAway = (String) resultMap.get("away");

                log.info("✅ Found match in league '{}': {} vs {}", foundLeague, foundHome, foundAway);
                log.debug("Match URL: {}", absoluteHref);

                // Navigate directly using absolute URL
                try {
                    log.debug("Navigating directly to match URL...");

                    page.navigate(absoluteHref, new Page.NavigateOptions()
                            .setTimeout(15000)
                            .setWaitUntil(WaitUntilState.NETWORKIDLE));

                    log.info("✅ Successfully navigated to match page");
                    return true;

                } catch (Exception e) {
                    log.error("❌ Failed to navigate to match page: {}", e.getMessage());
                    return false;
                }

            } else {
                log.warn("❌ Match not found: {} vs {} in league '{}'", home, away, leagueName);
                return false;
            }

        } catch (Exception e) {
            log.error("League and team text strategy error: {}", e.getMessage(), e);
            return false;
        }
    }

    // Alternative: Simpler version using direct navigation
    private static boolean tryClickByLeagueAndTeamTextJsSimple(Page page, String leagueName, String home, String away) {
        log.info("Strategy 3 (Simple): Searching for match in league '{}': {} vs {}", leagueName, home, away);
        try {
            // Use JavaScript to find the match and get absolute URL
            Object result = page.evaluate("""
    (params) => {
        const { leagueName, home, away } = params;
        
        function normalize(text) {
            if (!text) return '';
            return text.toLowerCase()
                .replace(/\\s*-\\s*/g, ' ')
                .replace(/[.,]/g, '')
                .replace(/\\s+/g, ' ')
                .trim();
        }
        
        const normalizedLeague = normalize(leagueName);
        const tournaments = document.querySelectorAll('.m-tournament');
        
        for (const tournament of tournaments) {
            const titleElement = tournament.querySelector('.category-tournament-title');
            if (!titleElement) continue;
            
            const normalizedTournament = normalize(titleElement.textContent);
            
            if (normalizedTournament.includes(normalizedLeague) || 
                normalizedLeague.includes(normalizedTournament)) {
                
                const matchElements = tournament.querySelectorAll('.m-event .m-teams');
                
                for (const match of matchElements) {
                    const teamWrappers = match.querySelectorAll('.m-server-name-wrapper');
                    
                    if (teamWrappers.length >= 2) {
                        const homeText = teamWrappers[0].querySelector('.tw-w-full.tw-truncate')?.textContent.trim();
                        const awayText = teamWrappers[1].querySelector('.tw-w-full.tw-truncate')?.textContent.trim();
                        
                        if (homeText && awayText &&
                            homeText.toLowerCase() === home.toLowerCase() &&
                            awayText.toLowerCase() === away.toLowerCase()) {
                            
                            const link = match.querySelector('a');
                            if (link) {
                                return {
                                    found: true,
                                    url: link.href  // Get absolute URL
                                };
                            }
                        }
                    }
                }
            }
        }
        
        return { found: false };
    }
    """,
                    Map.of(
                            "leagueName", leagueName,
                            "home", home,
                            "away", away
                    )
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;

            if (Boolean.TRUE.equals(resultMap.get("found"))) {
                String url = (String) resultMap.get("url");

                log.info("✅ Found match, navigating to: {}", url);

                // Navigate directly using the URL
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(15000)
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));

                log.info("✅ Successfully navigated to match page");
                return true;
            } else {
                log.warn("❌ Match not found: {} vs {} in league '{}'", home, away, leagueName);
                return false;
            }

        } catch (Exception e) {
            log.error("Simple navigation strategy error: {}", e.getMessage(), e);
            return false;
        }
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
    public static void returnToSportPage(Page page, Sport sport) throws Exception {
        log.info("{} {} Returning to {} live page...", EMOJI_NAVIGATION, EMOJI_SEARCH, sport);

        switchToLiveSport(page, sport.getDisplayName());
    }

    /**
     * Wait for page to be fully loaded and ready
     */
    public static void waitForPageReady(Page page) throws Exception {
//        log.info("{} {} Waiting for page to be ready...", EMOJI_CLOCK, EMOJI_HEALTH);
//
//        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
////        page.waitForFunction("document.readyState === 'complete'");
//        page.waitForSelector("body", new Page.WaitForSelectorOptions().setTimeout(10000));
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

    public static void randomHumanDelay(long minMs, long maxMs) {
        try {
            long delay = minMs + ThreadLocalRandom.current().nextLong(maxMs - minMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }




}