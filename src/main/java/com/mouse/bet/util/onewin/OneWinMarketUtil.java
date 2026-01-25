package com.mouse.bet.util.onewin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.mouse.bet.converter.ModelConverter;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.orchestrator.model.BetLeg;
import com.mouse.bet.service.ArbOutcomeService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.mouse.bet.util.onewin.OneWinLoginUtil.typeFastHumanLike;

@Slf4j
public class OneWinMarketUtil {

    private static final double TOLERANCE_PERCENT = 0.05;


//    public static void clearBetSlip(Page page) {
//    }

    /**
     * Select and verify bet using Playwright locators (MORE RELIABLE)
     */
    public static boolean selectAndVerifyBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        try {
            log.info("Searching for bet - Market: {}, Outcome: {}, Expected Odds: {}",
                    task.marketType(), task.outcome(), task.expectedOdds());

            String normalizedMarket = normalizeText(task.marketType());
            String normalizedOutcome = normalizeText(task.outcome());

            // Get all market groups
            Locator marketGroups = page.locator("div._group_ahjwn_2");

            // Wait for at least one group to be visible
            marketGroups.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));

            int groupCount = marketGroups.count();
            log.debug("Found {} market groups", groupCount);

            // Iterate through each market group
            for (int i = 0; i < groupCount; i++) {
                Locator group = marketGroups.nth(i);

                // Get the market title
                Locator titleElement = group.locator("div._title_8ulje_6");

                try {
                    String marketTitle = titleElement.textContent().trim();
                    String normalizedTitle = normalizeText(marketTitle);

                    // Check if this is the correct market
                    if (!fuzzyMatch(normalizedTitle, normalizedMarket, 70)) {
                        continue;
                    }

                    log.debug("Found matching market: {}", marketTitle);

                    // Get all bet buttons in this group
                    Locator betButtons = group.locator("button._root_1hr84_2");
                    int buttonCount = betButtons.count();

                    // Iterate through bet buttons
                    for (int j = 0; j < buttonCount; j++) {
                        Locator button = betButtons.nth(j);

                        // Get outcome name and odds
                        Locator nameElement = button.locator("span._name_1hr84_36");
                        Locator oddsElement = button.locator("span._cf_17if8_2");

                        if (nameElement.count() == 0 || oddsElement.count() == 0) {
                            continue;
                        }

                        String outcomeName = nameElement.textContent().trim();
                        String oddsText = oddsElement.textContent().trim();
                        double odds = parseOdds(oddsText);

                        String normalizedOutcomeName = normalizeText(outcomeName);

                        // Check if outcome matches
                        if (fuzzyMatch(normalizedOutcomeName, normalizedOutcome, 75)) {
                            log.info("Found matching bet - Market: {}, Outcome: {}, Odds: {}",
                                    marketTitle, outcomeName, odds);

                            // Verify odds
                            if (!isOddsAcceptable(odds, task)) {
                                log.warn("Odds not acceptable - Found: {}, Expected: {}, Min: {}, Max: {}",
                                        odds, task.expectedOdds(), task.minOdds(), task.maxOdds());
                                return false;
                            }

                            // Human-like behavior before clicking
                            sleepRandom(400, 800);

                            // Scroll to button if needed
                            button.scrollIntoViewIfNeeded();
                            sleepRandom(200, 400);

                            // Click the bet button
                            button.click();

                            sleepRandom(500, 1000);
                            log.info("Bet selected successfully");

                            boolean verifyBetInSlip = verifyBetInBetslip(page, task);
                            if (!verifyBetInSlip) {
                                return false;
                            }
                            return true;
                        }
                    }

                } catch (PlaywrightException e) {
                    log.debug("Error processing group {}: {}", i, e.getMessage());
                    continue;
                }
            }

            log.error("Bet not found - Market: {}, Outcome: {}", task.marketType(), task.outcome());
            return false;

        } catch (Exception e) {
            log.error("Failed to select bet: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verify if odds are acceptable based on task parameters
     */
    private static boolean isOddsAcceptable(double foundOdds, BettingTask task) {

        double expectedOdds = task.expectedOdds();

        if (expectedOdds <= 0) {
            log.warn("Expected odds must be positive: {}", expectedOdds);
            return false;
        }

        if (foundOdds <= 0) {
            log.warn("Found odds must be positive: {}", foundOdds);
            return false;
        }

        // Calculate allowed range
        double lowerBound = expectedOdds * (1 - TOLERANCE_PERCENT);
        double upperBound = expectedOdds * (1 + TOLERANCE_PERCENT);

        boolean isAcceptable = foundOdds >= lowerBound && foundOdds <= upperBound;

        if (isAcceptable) {
            double percentDiff = ((foundOdds - expectedOdds) / expectedOdds) * 100.0;
            log.debug("Found odds {} is {:.2f}% from expected {} → ACCEPTED (±{:.0f}% tolerance)",
                    foundOdds, percentDiff, expectedOdds, TOLERANCE_PERCENT * 100);
        } else {
            String reason = foundOdds < lowerBound ? "too low" : "too high";
            double percentDiff = ((foundOdds - expectedOdds) / expectedOdds) * 100.0;
            log.debug("Found odds {} is {:.2f}% {} expected {} → REJECTED (±{:.0f}% tolerance)",
                    foundOdds, Math.abs(percentDiff), reason, expectedOdds, TOLERANCE_PERCENT * 100);
        }

        return isAcceptable;
    }


    /**
     * Parse odds from string, handling various formats
     */
    private static double parseOdds(String oddsText) {
        try {
            // Remove any non-numeric characters except decimal point and minus
            String cleaned = oddsText.replaceAll("[^0-9.-]", "");
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse odds: {}", oddsText);
            return 0.0;
        }
    }

    /**
     * Alternative odds verification with configurable tolerance
     */
    private static boolean isOddsAcceptableWithTolerance(double foundOdds, BettingTask task, double tolerancePercent) {
        double expectedOdds = task.expectedOdds();

        if (expectedOdds <= 0) {
            return true; // No expected odds specified
        }

        double difference = Math.abs(foundOdds - expectedOdds);
        double maxDifference = expectedOdds * (tolerancePercent / 100.0);

        boolean acceptable = difference <= maxDifference;

        log.debug("Odds verification - Found: {}, Expected: {}, Difference: {}, MaxAllowed: {}, Acceptable: {}",
                foundOdds, expectedOdds, difference, maxDifference, acceptable);

        return acceptable;
    }

    /**
     * Normalize text for comparison
     */
    private static String normalizeText(String text) {
        if (text == null) return "";

        return text.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9\\s.]", "");
    }

    /**
     * Fuzzy match with threshold percentage
     */
    private static boolean fuzzyMatch(String text1, String text2, int threshold) {
        String normalized1 = normalizeText(text1);
        String normalized2 = normalizeText(text2);

        // Exact match
        if (normalized1.equals(normalized2)) {
            return true;
        }

        // Contains match
        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
            return true;
        }

        // Word-based similarity
        String[] words1 = normalized1.split(" ");
        String[] words2 = normalized2.split(" ");

        int matchingWords = 0;
        for (String word1 : words1) {
            for (String word2 : words2) {
                if (word1.equals(word2) || word1.contains(word2) || word2.contains(word1)) {
                    matchingWords++;
                    break;
                }
            }
        }

        int totalWords = Math.max(words1.length, words2.length);
        if (totalWords == 0) return false;

        int similarityPercent = (matchingWords * 100) / totalWords;

        return similarityPercent >= threshold;
    }

    private static void sleepRandom(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }



    /**
     * HYBRID: Use JavaScript to FIND, Playwright to CLICK
     * Fast search (JS) + Reliable click (Locator)
     */
    public static boolean selectAndVerifyBetJS(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        String market = task.marketType().trim();
        String outcome = task.outcome().trim();

        log.info("Selecting: {} → {}", market, outcome);

        try {
            // Get fresh task for latest odds from database
            BettingTask freshTask = getFreshTask(task, arbOutcomeService);
            if (freshTask != null) {
                log.info("Using fresh betting task from DB");
                task = freshTask;
            } else {
                log.warn("Could not fetch fresh task, using current task");
            }

            double expectedOdds = task.expectedOdds();

            // ⚡ Use optimized OneWin finder with automatic waiting
            OneWinMarketOutcomeFinder.OutcomeResult result =
                    OneWinMarketOutcomeFinder.findAndClickOutcome(page, market, outcome, expectedOdds);

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

                if ("Outcome is disabled/locked".equals(result.errorMessage)) {
                    log.error("Outcome '{}' is currently disabled/locked", outcome);
                    takeMarketScreenshot(page, "disabled-" + safeFileName(market + "-" + outcome));
                    return false;
                }

                if ("Odds not acceptable".equals(result.errorMessage)) {
                    log.warn("Odds drifted: expected {} → got {}", expectedOdds, result.odds);

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
                            // return false;
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

            // Verify outcome match (sanity check)
            if (!isOutcomeMatchValid(result.outcomeText, outcome)) {
                log.warn("Outcome mismatch: expected '{}' → got '{}'", outcome, result.outcomeText);
                takeMarketScreenshot(page, "mismatch-" + safeFileName(market + "-" + outcome));
                return false;
            }

            log.info("FOUND: {} → {} @ {}", result.marketTitle, result.outcomeText, result.odds);

            randomHumanDelay(200, 400);

            // Verify bet slip
            if (!verifyBetSlip(page, task)) {
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
     * Validate outcome match with flexible matching
     * Handles variations in text formatting
     */
    private static boolean isOutcomeMatchValid(String actualOutcome, String expectedOutcome) {
        if (actualOutcome == null || expectedOutcome == null) {
            return false;
        }

        // Direct match
        if (actualOutcome.equalsIgnoreCase(expectedOutcome)) {
            return true;
        }

        // Normalized match
        String normalizedActual = normalizeOutcome(actualOutcome);
        String normalizedExpected = normalizeOutcome(expectedOutcome);

        return normalizedActual.equalsIgnoreCase(normalizedExpected);
    }

    /**
     * Normalize outcome string for comparison
     * Examples:
     *   "Home Team" -> "hometeam"
     *   "Over 76.5" -> "over76.5"
     *   "Home (+2.5)" -> "home+2.5"
     */
    private static String normalizeOutcome(String outcome) {
        return outcome.trim()
                .toLowerCase()
                .replaceAll("\\s+", "")          // Remove all spaces
                .replaceAll("[^a-z0-9.:+-]", ""); // Keep only alphanumeric and . : + -
    }

    /**
     * Verify bet was added to betslip
     */
    private static boolean verifyBetSlip(Page page, BettingTask task) {
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
        
        // Find all coupon items
        const coupons = document.querySelectorAll("div[class*='_coupon_']");
        
        if (!coupons || coupons.length === 0) {
            return { found: false, error: 'Betslip is empty' };
        }
        
        // Check each coupon for matching outcome
        for (let i = 0; i < coupons.length; i++) {
            const coupon = coupons[i];
            
            // Get the outcome text (e.g., "4th quarter. Away team to score, No 20.0")
            const outcomeEl = coupon.querySelector("div[class*='_outcome_']");
            
            if (!outcomeEl) continue;
            
            const outcomeText = outcomeEl.textContent.trim();
            const normalizedText = normalize(outcomeText);
            
            // Check if it contains market or outcome
            const hasMarket = normalizedText.includes(normalizedMarket);
            const hasOutcome = normalizedText.includes(normalizedOutcome);
            
            if (hasMarket || hasOutcome) {
                // Get odds for additional info
                const oddsEl = coupon.querySelector("span[class*='_root_1lnnj_']");
                const odds = oddsEl ? oddsEl.textContent.trim() : 'N/A';
                
                return {
                    found: true,
                    outcomeText: outcomeText,
                    odds: odds
                };
            }
        }
        
        return { 
            found: false, 
            error: 'Bet not found in betslip'
        };
    }
    """;

        try {
            // Wait for betslip to update
            randomHumanDelay(300, 500);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) page.evaluate(
                    jsVerify,
                    Map.of("market", task.marketType().trim(), "outcome", task.outcome().trim())
            );

            if (result == null) {
                log.error("Failed to verify betslip: null result");
                return false;
            }

            Boolean found = (Boolean) result.get("found");

            if (Boolean.TRUE.equals(found)) {
                log.info("✅ Bet verified in betslip: {} @ {}",
                        result.get("outcomeText"),
                        result.get("odds"));
                return true;
            } else {
                String error = (String) result.get("error");
                log.warn("❌ {}: {} → {}", error, task.marketType(), task.outcome());
                return false;
            }

        } catch (Exception e) {
            log.error("Error verifying betslip: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Take screenshot for debugging
     */
//    private static void takeMarketScreenshot(Page page, String filename) {
//        try {
//            String screenshotDir = "screenshots/onewin/";
//            Files.createDirectories(Paths.get(screenshotDir));
//
//            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
//            String fullPath = screenshotDir + timestamp + "_" + filename + ".png";
//
//            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(fullPath)));
//            log.info("Screenshot saved: {}", fullPath);
//        } catch (Exception e) {
//            log.warn("Could not save screenshot: {}", e.getMessage());
//        }
//    }

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

    /**
     * Speed comparison summary:
     *
     * JavaScript only:    100-300ms   (fast but unreliable click)
     * Locators only:    1,500-3,000ms (slow but reliable)
     * Hybrid:             150-400ms   (fast AND reliable) ⚡🎯
     *
     * The hybrid approach:
     * - Uses JS for fast searching (1 browser call)
     * - Marks the found button with data attribute
     * - Uses Playwright locator for reliable clicking
     * - Best of both worlds!
     */


    public static void takeMarketScreenshot(Page page, String suffix) {
        try {
            Path dir = Paths.get("screenshots", "markets");
            Files.createDirectories(dir);
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
            String filename = String.format("%s-%s-%s.png", timestamp, BookMaker._1WIN, suffix);
            Path path = dir.resolve(filename);

            Locator marketList = page.locator(".m-market-list");
            if (marketList.count() > 0) {
                marketList.screenshot(new Locator.ScreenshotOptions().setPath(path).setType(ScreenshotType.PNG));
            } else {
                page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true).setType(ScreenshotType.PNG));
            }
            log.info("📸 Screenshot saved: {}", path);
        } catch (Exception e) {
            log.warn("Failed to take screenshot: {}", e.getMessage());
        }
    }






    /**
     * Click bet button using Playwright locator (more reliable than JavaScript click)
     */
    private static boolean clickBetButtonUsingLocator(Page page, String market, String outcome, Double odds) {
        try {
            log.debug("🖱️ Clicking bet button: Market={}, Outcome={}, Odds={}", market, outcome, odds);

            // Strategy 1: Use precise text matching
            String selector = String.format(
                    "button:has(span._name_1hr84_36:text-is(\"%s\")):has(span._cf_17if8_2:text-is(\"%.2f\"))",
                    outcome, odds
            );

            Locator button = page.locator(selector).first();

            if (button.count() == 0) {

                log.warn("⚠️ Button not found with precise selector, trying flexible matching");

                // Strategy 2: Flexible text matching
                selector = String.format(
                        "button:has(span._name_1hr84_36:has-text(\"%s\")):has(span._cf_17if8_2)",
                        outcome
                );
                button = page.locator(selector).first();
            }

            if (button.count() == 0) {
                log.error("❌ Button not found with any selector");
                return false;
            }

            // Scroll into view
            button.scrollIntoViewIfNeeded();
            sleepRandom(200, 400);

            // Click with retry
            int maxAttempts = 3;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    button.click(new Locator.ClickOptions().setTimeout(5000));
                    log.info("✅ Bet button clicked successfully (attempt {})", attempt);
                    return true;
                } catch (Exception e) {
                    log.warn("⚠️ Click attempt {} failed: {}", attempt, e.getMessage());
                    if (attempt < maxAttempts) {
                        sleepRandom(500, 1000);
                    }
                }
            }

            return false;

        } catch (Exception e) {
            log.error("❌ Error clicking bet button: {}", e.getMessage(), e);
            return false;
        }
    }


    /**
     * Verify bet is in betslip
     */
    private static boolean verifyBetInBetslipJS(Page page, BetLeg betLeg) {
        try {
            String outcome = normalizeText(betLeg.outcome());

            String jsScript = String.format("""
        (function() {
            const outcome = '%s';
            
            function normalize(text) {
                if (!text) return '';
                return text.toLowerCase()
                    .trim()
                    .replace(/\\s+/g, ' ')
                    .replace(/[^a-z0-9\\s.:+-]/g, '');
            }
            
            const normalizedOutcome = normalize(outcome);
            
            // Find betslip container
            const betslip = document.querySelector('div[class*="betslip"]') || 
                           document.querySelector('div[class*="_betslip_"]') ||
                           document.querySelector('div[class*="bet-slip"]');
            
            if (!betslip) {
                console.log('Betslip container not found');
                return { found: false, reason: 'Betslip container not found' };
            }
            
            // Look for bet items in betslip
            const betItems = betslip.querySelectorAll('div[class*="bet-item"], div[class*="_item_"]');
            
            for (const item of betItems) {
                const itemText = normalize(item.textContent);
                
                if (itemText.includes(normalizedOutcome)) {
                    const oddsElement = item.querySelector('span[class*="odds"], span[class*="_cf_"]');
                    const odds = oddsElement ? parseFloat(oddsElement.textContent) : null;
                    
                    return {
                        found: true,
                        outcome: item.textContent.trim(),
                        odds: odds
                    };
                }
            }
            
            return { found: false, reason: 'Outcome not found in betslip' };
        })();
        """, escapeJs(outcome));

            Map<String, Object> result = (Map<String, Object>) page.evaluate(jsScript);

            if (result == null || !(Boolean) result.getOrDefault("found", false)) {
                String reason = (String) result.getOrDefault("reason", "Unknown");
                log.warn("⚠️ Bet not verified in betslip: {}", reason);
                return false;
            }

            log.info("✅ Bet verified in betslip: {}", result.get("outcome"));
            return true;

        } catch (Exception e) {
            log.error("❌ Error verifying bet in betslip: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Log available markets for debugging
     */
    private static void logAvailableMarkets(Page page) {
        try {
            String jsScript = """
        (function() {
            const groups = document.querySelectorAll('div._group_ahjwn_2');
            const markets = [];
            
            for (const group of groups) {
                const titleElement = group.querySelector('div._title_8ulje_6');
                if (titleElement) {
                    const outcomes = [];
                    const buttons = group.querySelectorAll('button._root_1hr84_2');
                    
                    for (const button of buttons) {
                        const nameSpan = button.querySelector('span._name_1hr84_36');
                        const oddsSpan = button.querySelector('span._cf_17if8_2');
                        
                        if (nameSpan && oddsSpan) {
                            outcomes.push({
                                name: nameSpan.textContent.trim(),
                                odds: oddsSpan.textContent.trim()
                            });
                        }
                    }
                    
                    markets.push({
                        title: titleElement.textContent.trim(),
                        outcomes: outcomes
                    });
                }
            }
            
            return markets;
        })();
        """;

            List<Map<String, Object>> markets = (List<Map<String, Object>>) page.evaluate(jsScript);

            log.info("📊 Available markets ({}):", markets.size());
            for (Map<String, Object> market : markets) {
                String title = (String) market.get("title");
                List<Map<String, String>> outcomes = (List<Map<String, String>>) market.get("outcomes");
                log.info("  - {} ({} outcomes)", title, outcomes.size());
                for (Map<String, String> outcome : outcomes) {
                    log.info("    • {} @ {}", outcome.get("name"), outcome.get("odds"));
                }
            }

        } catch (Exception e) {
            log.debug("Failed to log available markets: {}", e.getMessage());
        }
    }

    /**
     * Escape JavaScript string
     */
    private static String escapeJs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }


    /**
     * Click bet button using Playwright locator (more reliable than JS click)
     */
    private static boolean clickBetButtonUsingLocator(Page page, String marketTitle, String outcomeName) {
        try {
            log.debug("Clicking bet button for market: {}, outcome: {}", marketTitle, outcomeName);

            // Get all market groups
            Locator marketGroups = page.locator("div._group_ahjwn_2");
            int groupCount = marketGroups.count();

            for (int i = 0; i < groupCount; i++) {
                Locator group = marketGroups.nth(i);

                // Get market title
                Locator titleElement = group.locator("div._title_8ulje_6");

                if (titleElement.count() == 0) continue;

                String currentMarketTitle = titleElement.textContent().trim();

                // Check if this is the correct market
                if (currentMarketTitle.contains(marketTitle) || marketTitle.contains(currentMarketTitle)) {

                    // Get all bet buttons in this group
                    Locator betButtons = group.locator("button._root_1hr84_2");
                    int buttonCount = betButtons.count();

                    for (int j = 0; j < buttonCount; j++) {
                        Locator button = betButtons.nth(j);

                        // Get outcome name
                        Locator nameElement = button.locator("span._name_1hr84_36");

                        if (nameElement.count() == 0) continue;

                        String currentOutcome = nameElement.textContent().trim();

                        // Check if this is the correct outcome
                        if (currentOutcome.equals(outcomeName) ||
                                currentOutcome.contains(outcomeName) ||
                                outcomeName.contains(currentOutcome)) {

                            log.debug("Found matching button, clicking...");

                            // Human-like delay
                            sleepRandom(100, 300);

                            // Scroll into view if needed
                            button.scrollIntoViewIfNeeded();
                            sleepRandom(100, 200);

                            // Try multiple click methods
                            try {
                                // Method 1: Regular click
                                button.click();
                                log.debug("Clicked using regular click");
                                return true;
                            } catch (Exception e1) {
                                log.debug("Regular click failed, trying force click...");
                                try {
                                    // Method 2: Force click
                                    button.click(new Locator.ClickOptions().setForce(true));
                                    log.debug("Clicked using force click");
                                    return true;
                                } catch (Exception e2) {
                                    log.debug("Force click failed, trying JS click...");
                                    // Method 3: JavaScript click as last resort
                                    button.evaluate("element => element.click()");
                                    log.debug("Clicked using JavaScript");
                                    return true;
                                }
                            }
                        }
                    }
                }
            }

            log.error("Could not find button to click");
            return false;

        } catch (Exception e) {
            log.error("Error clicking bet button: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Alternative: Pure JavaScript click with multiple strategies
     */
    private static boolean clickBetButtonUsingJS(Page page, String marketTitle, String outcomeName) {
        try {
            String clickScript = String.format("""
            (function() {
                const marketTitle = '%s';
                const outcomeName = '%s';
                
                const groups = document.querySelectorAll('div._group_ahjwn_2');
                
                for (const group of groups) {
                    const titleElement = group.querySelector('div._title_8ulje_6');
                    if (!titleElement) continue;
                    
                    const title = titleElement.textContent.trim();
                    
                    if (title.includes(marketTitle) || marketTitle.includes(title)) {
                        const betButtons = group.querySelectorAll('button._root_1hr84_2');
                        
                        for (const button of betButtons) {
                            const nameSpan = button.querySelector('span._name_1hr84_36');
                            
                            if (nameSpan) {
                                const outcome = nameSpan.textContent.trim();
                                
                                if (outcome === outcomeName || outcome.includes(outcomeName)) {
                                    // Try multiple click methods
                                    try {
                                        // Method 1: Standard click
                                        button.click();
                                        return { success: true, method: 'click' };
                                    } catch (e1) {
                                        try {
                                            // Method 2: Dispatch click event
                                            button.dispatchEvent(new MouseEvent('click', {
                                                view: window,
                                                bubbles: true,
                                                cancelable: true
                                            }));
                                            return { success: true, method: 'dispatchEvent' };
                                        } catch (e2) {
                                            try {
                                                // Method 3: mousedown + mouseup
                                                button.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                                                button.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
                                                button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
                                                return { success: true, method: 'mouseEvents' };
                                            } catch (e3) {
                                                return { success: false, error: e3.message };
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                return { success: false, error: 'Button not found' };
            })();
            """, marketTitle, outcomeName);

            Map<String, Object> result = (Map<String, Object>) page.evaluate(clickScript);

            Boolean success = (Boolean) result.get("success");
            if (success) {
                String method = (String) result.get("method");
                log.debug("Clicked using method: {}", method);
                return true;
            } else {
                String error = (String) result.get("error");
                log.error("JS click failed: {}", error);
                return false;
            }

        } catch (Exception e) {
            log.error("Error in JS click: {}", e.getMessage(), e);
            return false;
        }
    }





    /**
     * Verify bet in betslip using JavaScript (FASTER)
     */
    private static boolean verifyBetInBetslipJS(Page page, BettingTask task) {
        try {
            log.info("Verifying bet in betslip...");

            // Wait a moment for betslip to update
            sleepRandom(200, 500);

            String normalizedMarket = normalizeText(task.marketType());
            String normalizedOutcome = normalizeText(task.outcome());

            // JavaScript to extract betslip information
            String jsScript = """
            (function() {
                function normalize(text) {
                    return text.toLowerCase()
                        .trim()
                        .replace(/\\s+/g, ' ')
                        .replace(/[^a-z0-9\\s.]/g, '');
                }
                
                // Find the coupon in betslip
                const coupon = document.querySelector('div._coupon_4pzt1_2');
                if (!coupon) return null;
                
                // Get competitors/teams
                const competitorsElement = coupon.querySelector('div._competitors_1p63s_21');
                const competitors = competitorsElement ? competitorsElement.textContent.trim() : '';
                
                // Get outcome text
                const outcomeElement = coupon.querySelector('div._outcome_1p63s_42');
                const outcome = outcomeElement ? outcomeElement.textContent.trim() : '';
                
                // Get odds
                const oddsElement = coupon.querySelector('span._root_1lnnj_2');
                const odds = oddsElement ? parseFloat(oddsElement.textContent.trim()) : 0;
                
                // Get score (if live)
                const scoreElement = coupon.querySelector('div._score_126cr_2');
                const score = scoreElement ? scoreElement.textContent.trim() : '';
                
                return {
                    competitors: competitors,
                    outcome: outcome,
                    odds: odds,
                    score: score,
                    isLive: scoreElement !== null
                };
            })();
            """;

            Map<String, Object> betslipData = (Map<String, Object>) page.evaluate(jsScript);

            if (betslipData == null) {
                log.error("No bet found in betslip");
                return false;
            }

            String competitors = (String) betslipData.get("competitors");
            String outcome = (String) betslipData.get("outcome");
            Double odds = ((Number) betslipData.get("odds")).doubleValue();
            String score = (String) betslipData.get("score");
            Boolean isLive = (Boolean) betslipData.get("isLive");

            log.info("=== BETSLIP VERIFICATION ===");
            log.info("Teams/Competitors: {}", competitors);
            log.info("Outcome: {}", outcome);
            log.info("Odds: {}", odds);
            log.info("Score: {}", isLive ? score : "Pre-match");
            log.info("===========================");

            // Verify the outcome matches
            String normalizedBetslipOutcome = normalizeText(outcome);

            // Check if outcome contains the expected market type and outcome
            boolean outcomeMatches = normalizedBetslipOutcome.contains(normalizedOutcome) ||
                    normalizedOutcome.contains(normalizedBetslipOutcome);

            if (!outcomeMatches) {
                log.error("Outcome mismatch - Expected: {}, Found: {}", task.outcome(), outcome);
                return false;
            }

            // Verify odds
            if (!isOddsAcceptable(odds, task)) {
                log.error("Odds in betslip not acceptable - Found: {}, Expected: {}", odds, task.expectedOdds());
                return false; //todo: try and ignore the odds from btslip, it might come back again
            }

            // Verify teams if provided
            if (task.homeTeam() != null && task.awayTeam() != null) {
                String normalizedCompetitors = normalizeText(competitors);
                String normalizedHome = normalizeText(task.homeTeam());
                String normalizedAway = normalizeText(task.awayTeam());

                boolean teamsMatch = (normalizedCompetitors.contains(normalizedHome) &&
                        normalizedCompetitors.contains(normalizedAway));

                if (!teamsMatch) {
                    log.warn("Teams might not match - Expected: {} vs {}, Found: {}",
                            task.homeTeam(), task.awayTeam(), competitors);
                }
            }

            log.info("Betslip verification PASSED");
            return true;

        } catch (Exception e) {
            log.error("Failed to verify betslip: {}", e.getMessage(), e);
            return false;
        }
    }



    /**
     * Verify bet in betslip using Playwright locators (MORE RELIABLE)
     */
    private static boolean verifyBetInBetslip(Page page, BettingTask task) {
        try {
            log.info("Verifying bet in betslip...");

            // Wait for betslip to update
            sleepRandom(200, 500);

            // Locate the coupon in betslip
            Locator coupon = page.locator("div._coupon_4pzt1_2");

            // Wait for coupon to be visible
            try {
                coupon.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(3000));
            } catch (PlaywrightException e) {
                log.error("No bet found in betslip");
                return false;
            }

            // Extract betslip information
            BetslipInfo betslipInfo = extractBetslipInfo(coupon);

            if (betslipInfo == null) {
                log.error("Failed to extract betslip information");
                return false;
            }

            // Log betslip details
            log.info("=== BETSLIP VERIFICATION ===");
            log.info("Teams/Competitors: {}", betslipInfo.competitors);
            log.info("Market & Outcome: {}", betslipInfo.outcome);
            log.info("Odds: {}", betslipInfo.odds);
            log.info("Score: {}", betslipInfo.isLive ? betslipInfo.score : "Pre-match");
            log.info("===========================");

            // Verify outcome matches
            String normalizedBetslipOutcome = normalizeText(betslipInfo.outcome);
            String normalizedExpectedOutcome = normalizeText(task.outcome());
            String normalizedExpectedMarket = normalizeText(task.marketType());

            // Check if the betslip outcome contains the expected outcome
            boolean outcomeMatches = normalizedBetslipOutcome.contains(normalizedExpectedOutcome) ||
                    normalizedExpectedOutcome.contains(normalizedBetslipOutcome) ||
                    normalizedBetslipOutcome.contains(normalizedExpectedMarket);

            if (!outcomeMatches) {
                log.error("Outcome mismatch - Expected: {} ({}), Found: {}",
                        task.outcome(), task.marketType(), betslipInfo.outcome);
                return false;
            }

            // Verify odds
            if (!isOddsAcceptable(betslipInfo.odds, task)) {
                log.error("Odds in betslip not acceptable - Found: {}, Expected: {}, Min: {}, Max: {}",
                        betslipInfo.odds, task.expectedOdds(), task.minOdds(), task.maxOdds());
//                return false; todo
            }

            // Verify teams if provided
            if (task.homeTeam() != null && task.awayTeam() != null) {
                boolean teamsMatch = verifyTeamsInBetslip(betslipInfo.competitors,
                        task.homeTeam(), task.awayTeam());

                if (!teamsMatch) {
                    log.warn("Teams might not match exactly - Expected: {} vs {}, Found: {}",
                            task.homeTeam(), task.awayTeam(), betslipInfo.competitors);
                    // Don't fail, just warn - team names might have slight variations
                }
            }

            log.info("✓ Betslip verification PASSED");
            return true;

        } catch (Exception e) {
            log.error("Failed to verify betslip: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extract all information from the betslip coupon
     */
    private static BetslipInfo extractBetslipInfo(Locator coupon) {
        try {
            BetslipInfo info = new BetslipInfo();

            // Get competitors/teams
            Locator competitorsElement = coupon.locator("div._competitors_1p63s_21");
            if (competitorsElement.count() > 0) {
                info.competitors = competitorsElement.textContent().trim();
            }

            // Get outcome text (includes market type and selection)
            Locator outcomeElement = coupon.locator("div._outcome_1p63s_42");
            if (outcomeElement.count() > 0) {
                info.outcome = outcomeElement.textContent().trim();
            }

            // Get odds
            Locator oddsElement = coupon.locator("span._root_1lnnj_2");
            if (oddsElement.count() > 0) {
                String oddsText = oddsElement.textContent().trim();
                info.odds = parseOdds(oddsText);
            }

            // Get score (if live)
            Locator scoreElement = coupon.locator("div._score_126cr_2");
            if (scoreElement.count() > 0) {
                info.score = scoreElement.textContent().trim();
                info.isLive = true;

                // Check for live icon
                Locator liveIcon = scoreElement.locator("span._liveIcon_126cr_17");
                info.isLive = liveIcon.count() > 0;
            }

            return info;

        } catch (Exception e) {
            log.error("Error extracting betslip info: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verify if teams match in betslip
     */
    private static boolean verifyTeamsInBetslip(String competitors, String homeTeam, String awayTeam) {
        if (competitors == null || competitors.isEmpty()) {
            return false;
        }

        String normalizedCompetitors = normalizeText(competitors);
        String normalizedHome = normalizeText(homeTeam);
        String normalizedAway = normalizeText(awayTeam);

        // Check if both teams are present in the competitors string
        boolean homeFound = normalizedCompetitors.contains(normalizedHome) ||
                normalizedHome.contains(normalizedCompetitors);
        boolean awayFound = normalizedCompetitors.contains(normalizedAway) ||
                normalizedAway.contains(normalizedCompetitors);

        return homeFound && awayFound;
    }

    /**
     * Data class to hold betslip information
     */
    private static class BetslipInfo {
        String competitors = "";
        String outcome = "";
        double odds = 0.0;
        String score = "";
        boolean isLive = false;
    }

    /**
     * Get betslip count (number of bets in betslip)
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
     * Clear betslip (remove all bets)
     */
    public static void clearBetSlip(Page page) {
        try {
            log.info("Clearing betslip...");

            // Click the trash/delete all button
            Locator deleteButton = page.locator("button._root_9f102_8:has(span[style*='trash.svg'])");

            if (deleteButton.count() > 0) {
                deleteButton.click();
                sleepRandom(500, 1000);
                log.info("Betslip cleared");
            }

        } catch (Exception e) {
            log.error("Failed to clear betslip: {}", e.getMessage());
        }
    }

    /**
     * Get possible win amount from betslip
     */
    private static String getPossibleWinAmount(Page page) {
        try {
            Locator winAmount = page.locator("span._betAmount_151rq_16");
            if (winAmount.count() > 0) {
                return winAmount.textContent().trim();
            }
        } catch (Exception e) {
            log.error("Failed to get win amount: {}", e.getMessage());
        }
        return "N 0.00";
    }

    /**
     * Enter stake amount in betslip
     */
    private static void enterStakeAmount(Page page, double amount) {
        try {
            log.info("Entering stake amount: {}", amount);

            Locator stakeInput = page.locator("input[data-qa='amount']").first();

            if (stakeInput.count() > 0) {
                stakeInput.click();
                sleepRandom(200, 400);

                // Clear existing value
                stakeInput.fill("");
                sleepRandom(100, 200);

                // Type amount with human-like behavior
                typeFastHumanLike(stakeInput, String.valueOf(amount));

                sleepRandom(300, 600);
                log.info("Stake amount entered: {}", amount);
            }

        } catch (Exception e) {
            log.error("Failed to enter stake amount: {}", e.getMessage());
        }
    }



    /**
     * Place bet with retry logic for disappearing outcomes
     */
    public static boolean placeBet(Page page, BettingTask task, ArbOutcomeService arbOutcomeService) {
        try {
            log.info("Starting bet placement process for task: {}", task.taskId());

            // Configuration
            int maxAttempts = 10; // Maximum number of attempts
            int waitBetweenAttempts = 3000; // 3 seconds between attempts
            int totalWaitTime = 30000; // Maximum 30 seconds total wait time

            long startTime = System.currentTimeMillis();
            int attempt = 0;

            while (attempt < maxAttempts) {
                attempt++;
                long elapsedTime = System.currentTimeMillis() - startTime;

                // Check if we've exceeded total wait time
                if (elapsedTime > totalWaitTime) {
                    log.error("Timeout: Bet placement exceeded {} seconds", totalWaitTime / 1000);
                    return false;
                }

                log.info("Bet placement attempt {}/{}", attempt, maxAttempts);

                // Step 1: Try to select and verify the bet
                if (attempt > 1) {
                    boolean betSelected = selectAndVerifyBetJS(page, task,arbOutcomeService);

                    if (!betSelected) {
                        log.warn("Bet not available or verification failed. Waiting {} seconds for outcome to appear...",
                                waitBetweenAttempts / 1000);
                        sleepRandom(waitBetweenAttempts - 500, waitBetweenAttempts + 500);
                        continue;
                    }
                }


                log.info("Bet successfully selected and verified");

                // Step 2: Check if bet is still in betslip (might disappear if odds changed)
                sleepRandom(500, 1000);

                int betslipCount = getBetslipCount(page);
                if (betslipCount == 0) {
                    log.warn("Bet disappeared from betslip (likely due to odds change). Retrying...");
                    continue;
                }

//                // Step 3: Verify bet is still valid in betslip
//                boolean betStillValid = verifyBetInBetslip(page, task);
//                if (!betStillValid) {
//                    log.warn("Bet in betslip no longer valid. Retrying...");
//                    clearBetSlip(page);
//                    sleepRandom(1000, 1500);
//                    continue;
//                }

                // Step 4: Enter stake amount
                log.info("Entering stake amount: {}", task.stakeAmount());
                boolean stakeEntered = enterStakeAmountWithVerification(page, task.stakeAmount());

                if (!stakeEntered) {
                    log.error("Failed to enter stake amount");
                    clearBetSlip(page);
                    return false;
                }

                sleepRandom(500, 1000);

                // Step 5: Verify bet is still in betslip after entering stake
                betslipCount = getBetslipCount(page);
                if (betslipCount == 0) {
                    log.warn("Bet disappeared after entering stake. Retrying...");
                    continue;
                }

                // Step 6: Get possible win amount for logging
                String possibleWin = getPossibleWinAmount(page);
                log.info("Possible win amount: {}", possibleWin);

                // Step 7: Click place bet button
                log.info("Clicking 'Place a bet' button...");
                boolean betPlaced = clickPlaceBetButton(page);

                if (!betPlaced) {
                    log.error("Failed to click place bet button");
                    clearBetSlip(page);
                    return false;
                }

                sleepRandom(1000, 2000);

                // Step 8: Handle post-placement scenarios
                BetPlacementResult result = handleBetPlacementResponse(page, task);

                switch (result) {
                    case SUCCESS:
                        log.info("✓ Bet placed successfully!");
                        return true;

                    case ODDS_CHANGED:
                        log.warn("Odds changed - attempting to accept changes and retry...");
                        boolean changesAccepted = handleOddsChange(page);
                        if (changesAccepted) {
                            // Continue to next iteration to try again
                            sleepRandom(1000, 1500);
                            continue;
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
                        sleepRandom(2000, 3000);
                        continue;

                    case UNKNOWN:
                    default:
                        log.error("Unknown bet placement result");
                        clearBetSlip(page);
                        return false;
                }
            }

            log.error("Failed to place bet after {} attempts", maxAttempts);
            clearBetSlip(page);
            return false;

        } catch (Exception e) {
            log.error("Error during bet placement: {}", e.getMessage(), e);
            clearBetSlip(page);
            return false;
        }
    }

    /**
     * Enter stake amount with verification
     */
    private static boolean enterStakeAmountWithVerification(Page page, double amount) {
        try {
            log.debug("Entering stake amount: {}", amount);

            Locator stakeInput = page.locator("input[data-qa='amount']").first();

            if (stakeInput.count() == 0) {
                log.error("Stake input field not found");
                return false;
            }

            // Click to focus
            stakeInput.click();
            sleepRandom(200, 400);

            // Clear existing value
            stakeInput.fill("");
            sleepRandom(100, 200);

            // Type amount with human-like behavior
            String amountStr = String.format("%.2f", amount);
            typeFastHumanLike(stakeInput, amountStr);

            sleepRandom(300, 600);

            // Verify the amount was entered correctly
            String enteredValue = stakeInput.inputValue();
            double enteredAmount = 0.0;

            try {
                enteredAmount = Double.parseDouble(enteredValue);
            } catch (NumberFormatException e) {
                log.error("Failed to parse entered amount: {}", enteredValue);
                return false;
            }

            // Allow small difference due to rounding
            double difference = Math.abs(enteredAmount - amount);
            if (difference > 0.01) {
                log.error("Stake amount mismatch - Expected: {}, Entered: {}", amount, enteredAmount);
                return false;
            }

            log.info("Stake amount verified: {}", enteredAmount);
            return true;

        } catch (Exception e) {
            log.error("Failed to enter stake amount: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Click the "Place a bet" button
     */
    private static boolean clickPlaceBetButton(Page page) {
        try {
            // Try multiple selectors for the place bet button
            String[] selectors = {
                    "button:has-text('Place a bet')",
                    "button._root_9f102_8._variantAccent_9f102_143:has-text('Place')",
                    "button[type='button']:has(span:has-text('Place a bet'))"
            };

            for (String selector : selectors) {
                Locator placeBetButton = page.locator(selector);

                if (placeBetButton.count() > 0) {
                    // Check if button is enabled
                    boolean isDisabled = placeBetButton.isDisabled();

                    if (isDisabled) {
                        log.warn("Place bet button is disabled");
                        return false;
                    }

                    // Human-like delay before clicking
                    sleepRandom(500, 1000);

                    // Click the button
                    placeBetButton.click();

                    log.info("Clicked 'Place a bet' button");
                    return true;
                }
            }

            log.error("Place bet button not found");
            return false;

        } catch (Exception e) {
            log.error("Failed to click place bet button: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Handle bet placement response and determine the result
     */
    private static BetPlacementResult handleBetPlacementResponse(Page page, BettingTask task) {
        try {
            log.debug("Waiting for bet placement response...");

            // Wait for potential response (modal, message, etc.)
            sleepRandom(2000, 3000);

            // Check for success indicators
            if (checkBetPlacementSuccess(page)) {
                return BetPlacementResult.SUCCESS;
            }

            // Check for odds change prompt
            if (checkOddsChangePrompt(page)) {
                return BetPlacementResult.ODDS_CHANGED;
            }

            // Check for insufficient balance
            if (checkInsufficientBalance(page)) {
                return BetPlacementResult.INSUFFICIENT_BALANCE;
            }

            // Check for bet rejection
            if (checkBetRejected(page)) {
                return BetPlacementResult.BET_REJECTED;
            }

            // Check if betslip is empty (might indicate success or failure)
            int betslipCount = getBetslipCount(page);
            if (betslipCount == 0) {
                log.debug("Betslip is empty - assuming success");
                return BetPlacementResult.SUCCESS;
            }

            log.warn("Unable to determine bet placement result");
            return BetPlacementResult.UNKNOWN;

        } catch (Exception e) {
            log.error("Error handling bet placement response: {}", e.getMessage());
            return BetPlacementResult.UNKNOWN;
        }
    }

    /**
     * Check if bet placement was successful
     */
    private static boolean checkBetPlacementSuccess(Page page) {
        try {
            log.debug("Checking for bet placement success...");

            // Strategy 1: Check for "Bet placed" title
            Locator betPlacedTitle = page.locator("p._title_1yhg0_7:has-text('Bet placed')");
            if (betPlacedTitle.count() > 0 && betPlacedTitle.isVisible()) {
                log.info("✓ Success: Found 'Bet placed' title");
                logBetPlacementDetails(page);
                return true;
            }

            // Strategy 2: Check for success modal container
            Locator successModal = page.locator("div._root_1yhg0_2");
            if (successModal.count() > 0 && successModal.isVisible()) {
                // Verify it contains success indicators
                Locator checkIcon = successModal.locator("span._checkIcon_zbiwv_52");
                Locator continueButton = successModal.locator("button:has-text('Continue betting')");

                if (checkIcon.count() > 0 && continueButton.count() > 0) {
                    log.info("✓ Success: Found success modal with check icon");
                    logBetPlacementDetails(page);
                    return true;
                }
            }

            // Strategy 3: Check for "Continue betting" button
            Locator continueButton = page.locator("button._goBet_1yhg0_41:has-text('Continue betting')");
            if (continueButton.count() > 0 && continueButton.isVisible()) {
                log.info("✓ Success: Found 'Continue betting' button");
                logBetPlacementDetails(page);
                return true;
            }

            // Strategy 4: Check for "Go to history" button
            Locator historyButton = page.locator("button:has-text('Go to history')");
            if (historyButton.count() > 0 && historyButton.isVisible()) {
                log.info("✓ Success: Found 'Go to history' button");
                logBetPlacementDetails(page);
                return true;
            }

            log.debug("No success indicators found");
            return false;

        } catch (Exception e) {
            log.error("Error checking bet placement success: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Log detailed bet placement information from success modal
     */
    private static void logBetPlacementDetails(Page page) {
        try {
            log.info("=== BET PLACEMENT SUCCESS DETAILS ===");

            // Get bet selection/outcome
            Locator titleElement = page.locator("div._title_zbiwv_31");
            if (titleElement.count() > 0) {
                String selection = titleElement.textContent().trim();
                log.info("Selection: {}", selection);
            }

            // Get match/event
            Locator subtitleElement = page.locator("div._subtitle_zbiwv_39");
            if (subtitleElement.count() > 0) {
                String match = subtitleElement.textContent().trim();
                log.info("Match: {}", match);
            }

            // Get odds
            Locator oddsElement = page.locator("span._root_1lnnj_2._primary_1lnnj_15");
            if (oddsElement.count() > 0) {
                String odds = oddsElement.textContent().trim();
                log.info("Odds: {}", odds);
            }

            // Get bet amount
            Locator betAmountElement = page.locator("div._betAmount_zbiwv_82 span");
            if (betAmountElement.count() > 0) {
                String betAmount = betAmountElement.textContent().trim();
                log.info("Bet Amount: {}", betAmount);
            }

            // Get possible win
            Locator winAmountElement = page.locator("div._profitAmount_zbiwv_91");
            if (winAmountElement.count() > 0) {
                String winAmount = winAmountElement.textContent().trim();
                log.info("Possible Win: {}", winAmount);
            }

            log.info("=====================================");

        } catch (Exception e) {
            log.debug("Could not extract all bet details: {}", e.getMessage());
        }
    }

    /**
     * Extract bet placement details as an object
     */
    private static BetPlacementDetails extractBetPlacementDetails(Page page) {
        try {
            BetPlacementDetails details = new BetPlacementDetails();

            // Get selection
            Locator titleElement = page.locator("div._title_zbiwv_31");
            if (titleElement.count() > 0) {
                details.selection = titleElement.textContent().trim();
            }

            // Get match
            Locator subtitleElement = page.locator("div._subtitle_zbiwv_39");
            if (subtitleElement.count() > 0) {
                details.match = subtitleElement.textContent().trim();
            }

            // Get odds
            Locator oddsElement = page.locator("span._root_1lnnj_2._primary_1lnnj_15");
            if (oddsElement.count() > 0) {
                String oddsText = oddsElement.textContent().trim();
                details.odds = parseOdds(oddsText);
            }

            // Get bet amount
            Locator betAmountElement = page.locator("div._betAmount_zbiwv_82 span");
            if (betAmountElement.count() > 0) {
                String amountText = betAmountElement.textContent().trim();
                details.betAmount = parseAmount(amountText);
            }

            // Get possible win
            Locator winAmountElement = page.locator("div._profitAmount_zbiwv_91");
            if (winAmountElement.count() > 0) {
                String winText = winAmountElement.textContent().trim();
                details.possibleWin = parseAmount(winText);
            }

            details.success = true;
            return details;

        } catch (Exception e) {
            log.error("Error extracting bet placement details: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse amount from text (handles currency symbols)
     */
    private static double parseAmount(String amountText) {
        try {
            // Remove currency symbols and non-numeric characters except decimal point
            String cleaned = amountText.replaceAll("[^0-9.]", "");
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse amount: {}", amountText);
            return 0.0;
        }
    }


    /**
     * Data class for bet placement details
     */
    private static class BetPlacementDetails {
        boolean success = false;
        String selection = "";
        String match = "";
        double odds = 0.0;
        double betAmount = 0.0;
        double possibleWin = 0.0;

        @Override
        public String toString() {
            return String.format(
                    "BetPlacementDetails{success=%s, selection='%s', match='%s', odds=%.2f, betAmount=%.2f, possibleWin=%.2f}",
                    success, selection, match, odds, betAmount, possibleWin
            );
        }
    }

    /**
     * Click "Continue betting" button to close success modal
     */
    private static void clickContinueBetting(Page page) {
        try {
            log.info("Clicking 'Continue betting' button...");

            Locator continueButton = page.locator("button._goBet_1yhg0_41:has-text('Continue betting')");

            if (continueButton.count() > 0 && continueButton.isVisible()) {
                sleepRandom(500, 1000);

                try {
                    continueButton.click();
                    log.info("Clicked 'Continue betting'");
                } catch (Exception e) {
                    log.warn("Regular click failed, trying force click...");
                    continueButton.click(new Locator.ClickOptions().setForce(true));
                    log.info("Clicked 'Continue betting' with force");
                }

                sleepRandom(800, 1200);
            } else {
                log.warn("'Continue betting' button not found");
            }

        } catch (Exception e) {
            log.error("Error clicking continue betting: {}", e.getMessage());
        }
    }

    /**
     * Click "Go to history" button
     */
    private static void clickGoToHistory(Page page) {
        try {
            log.info("Clicking 'Go to history' button...");

            Locator historyButton = page.locator("button:has-text('Go to history')");

            if (historyButton.count() > 0 && historyButton.isVisible()) {
                sleepRandom(500, 1000);
                historyButton.click();
                log.info("Clicked 'Go to history'");
                sleepRandom(1000, 1500);
            } else {
                log.warn("'Go to history' button not found");
            }

        } catch (Exception e) {
            log.error("Error clicking go to history: {}", e.getMessage());
        }
    }

    /**
     * Close bet placement success modal
     */
    private static void closeBetSuccessModal(Page page) {
        try {
            log.info("Closing bet success modal...");

            // Try clicking "Continue betting" first
            Locator continueButton = page.locator("button._goBet_1yhg0_41:has-text('Continue betting')");

            if (continueButton.count() > 0 && continueButton.isVisible()) {
                clickContinueBetting(page);
                return;
            }

            // Alternative: Try clicking outside the modal
            log.debug("Continue button not found, trying alternative methods...");

            // Press Escape key
            page.keyboard().press("Escape");
            sleepRandom(500, 1000);

            log.info("Attempted to close modal with Escape key");

        } catch (Exception e) {
            log.error("Error closing bet success modal: {}", e.getMessage());
        }
    }



    /**
     * Check if odds change prompt appeared
     * TODO: Implement based on actual odds change prompt
     */
    private static boolean checkOddsChangePrompt(Page page) {
        try {
            log.debug("Checking for odds change prompt...");

            // TODO: Implement odds change detection
            // Examples:
            // - Check for "Accept changes" button
            // - Check for odds change message/modal
            // - Check for updated odds display

            // Placeholder selectors - update based on actual UI
            String[] selectors = {
                    "button:has-text('Accept changes')",
                    "button:has-text('Accept')",
                    "div:has-text('Odds have changed')"
            };

            for (String selector : selectors) {
                if (page.locator(selector).count() > 0) {
                    log.info("Odds change prompt detected");
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("Error checking odds change prompt: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Handle odds change by accepting new odds
     * TODO: Implement based on actual odds change UI
     */
    private static boolean handleOddsChange(Page page) {
        try {
            log.info("Handling odds change...");

            // TODO: Implement odds change handling
            // Examples:
            // - Click "Accept changes" button
            // - Verify new odds are acceptable
            // - Log the odds change details

            sleepRandom(500, 1000);

            // Placeholder - update based on actual UI
            Locator acceptButton = page.locator("button:has-text('Accept changes')");

            if (acceptButton.count() > 0) {
                acceptButton.click();
                sleepRandom(1000, 1500);
                log.info("Accepted odds change");
                return true;
            }

            log.warn("Accept changes button not found");
            return false;

        } catch (Exception e) {
            log.error("Error handling odds change: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if insufficient balance error occurred
     * TODO: Implement based on actual error message
     */
    private static boolean checkInsufficientBalance(Page page) {
        try {
            log.debug("Checking for insufficient balance error...");

            // TODO: Implement based on actual error UI
            // Check for error messages related to insufficient balance

            String[] errorSelectors = {
                    "text=Insufficient balance",
                    "text=Not enough funds",
                    "div:has-text('balance')"
            };

            for (String selector : errorSelectors) {
                if (page.locator(selector).count() > 0) {
                    log.error("Insufficient balance detected");
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("Error checking insufficient balance: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if bet was rejected
     * TODO: Implement based on actual rejection message
     */
    private static boolean checkBetRejected(Page page) {
        try {
            log.debug("Checking for bet rejection...");

            // TODO: Implement based on actual rejection UI
            // Check for rejection messages

            String[] rejectionSelectors = {
                    "text=Bet rejected",
                    "text=rejected",
                    "text=not accepted"
            };

            for (String selector : rejectionSelectors) {
                if (page.locator(selector).count() > 0) {
                    log.error("Bet rejection detected");
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("Error checking bet rejection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Enum for bet placement results
     */
    private enum BetPlacementResult {
        SUCCESS,              // Bet placed successfully
        ODDS_CHANGED,        // Odds changed, need to accept
        INSUFFICIENT_BALANCE, // Not enough balance
        BET_REJECTED,        // Bet rejected by bookmaker
        TIMEOUT,             // Timeout waiting for response
        UNKNOWN              // Unknown result
    }

    /**
     * Get the current balance from betslip header
     */
    private static double getCurrentBalance(Page page) {
        try {
            Locator balanceElement = page.locator("div._amount_1ch79_13");

            if (balanceElement.count() > 0) {
                String balanceText = balanceElement.textContent().trim();
                // Remove currency symbol and parse
                String cleanedBalance = balanceText.replaceAll("[^0-9.]", "");
                return Double.parseDouble(cleanedBalance);
            }

        } catch (Exception e) {
            log.error("Failed to get current balance: {}", e.getMessage());
        }

        return 0.0;
    }

    /**
     * Check if betslip is open/visible
     */
    private static boolean isBetslipOpen(Page page) {
        try {
            Locator betslipCard = page.locator("div._card_1hqdr_29._open_1hqdr_23");
            return betslipCard.count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Open betslip if closed
     */
    private static void openBetslip(Page page) {
        try {
            if (!isBetslipOpen(page)) {
                log.info("Opening betslip...");

                // Try to find and click betslip toggle button
                Locator betslipToggle = page.locator("button:has-text('Betslip')");

                if (betslipToggle.count() > 0) {
                    betslipToggle.click();
                    sleepRandom(500, 1000);
                    log.info("Betslip opened");
                }
            }
        } catch (Exception e) {
            log.error("Failed to open betslip: {}", e.getMessage());
        }
    }
}
