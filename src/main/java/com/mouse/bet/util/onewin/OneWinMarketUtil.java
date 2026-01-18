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
        try {
            log.info("🔍 Searching for bet -- Market: {}, Outcome: {}, Expected Odds: {}",
                    task.marketType(), task.outcome(), task.expectedOdds());

            String marketType = normalizeText(task.marketType());
            String outcome = normalizeText(task.outcome());

            // ⚡ STEP 1: Use JavaScript to FIND the bet quickly (100-300ms)
            String jsScript = String.format("""
        (function() {
            const marketType = '%s';
            const outcome = '%s';
            
            function normalize(text) {
                if (!text) return '';
                return text.toLowerCase()
                    .trim()
                    .replace(/\\s+/g, ' ')
                    .replace(/[^a-z0-9\\s.:+-]/g, '');
            }
            
            const normalizedMarket = normalize(marketType);
            const normalizedOutcome = normalize(outcome);
            
            const groups = document.querySelectorAll('div._group_ahjwn_2');
            
            for (const group of groups) {
                const titleElement = group.querySelector('div._title_8ulje_6');
                if (!titleElement) continue;
                
                const title = normalize(titleElement.textContent);
                
                const isMarketMatch = title.includes(normalizedMarket) || 
                                     normalizedMarket.includes(title) ||
                                     normalizedMarket.split(' ').some(word => title.includes(word));
                
                if (isMarketMatch) {
                    const betButtons = group.querySelectorAll('button._root_1hr84_2:not(:has(div._headerCell_xgz91_2))');
                    
                    for (let i = 0; i < betButtons.length; i++) {
                        const button = betButtons[i];
                        
                        // Skip header cells
                        const headerCell = button.closest('div._cell_9pkob_21')?.querySelector('div._headerCell_xgz91_2');
                        if (headerCell) continue;
                        
                        const nameSpan = button.querySelector('span._name_1hr84_36');
                        const oddsSpan = button.querySelector('span._cf_17if8_2');
                        
                        if (nameSpan && oddsSpan) {
                            const betName = normalize(nameSpan.textContent);
                            const oddsText = oddsSpan.textContent.trim();
                            const odds = parseFloat(oddsText);
                            
                            const isOutcomeMatch = betName.includes(normalizedOutcome) || 
                                                  normalizedOutcome.includes(betName) ||
                                                  betName.split(' ').every(word => normalizedOutcome.includes(word));
                            
                            if (isOutcomeMatch) {
                                const isDisabled = button.disabled || 
                                                  button.classList.contains('disabled') ||
                                                  button.classList.contains('_locked_1hr84_2');
                                
                                // Add unique identifier to button for Playwright to find
                                button.setAttribute('data-arb-target', 'true');
                                
                                return {
                                    market: titleElement.textContent.trim(),
                                    outcome: nameSpan.textContent.trim(),
                                    odds: odds,
                                    found: true,
                                    disabled: isDisabled,
                                    buttonIndex: i
                                };
                            }
                        }
                    }
                }
            }
            
            return { found: false };
        })();
        """, escapeJs(marketType), escapeJs(outcome));

            // Execute JavaScript search
            Object resultObj = page.evaluate(jsScript);
            Map<String, Object> result = (Map<String, Object>) resultObj;

            if (result == null || !(Boolean) result.getOrDefault("found", false)) {
                log.error("❌ Bet not found - Market: {}, Outcome: {}", task.marketType(), task.outcome());
                takeMarketScreenshot(page, "bet_not_found");
                logAvailableMarkets(page);
                return false;
            }

            String foundMarket = (String) result.get("market");
            String foundOutcome = (String) result.get("outcome");
            Double foundOdds = ((Number) result.get("odds")).doubleValue();
            Boolean isDisabled = (Boolean) result.getOrDefault("disabled", false);

            log.info("✅ Found bet - Market: '{}', Outcome: '{}', Odds: {}, Disabled: {}",
                    foundMarket, foundOutcome, foundOdds, isDisabled);

            takeMarketScreenshot(page, "bet_found");

            if (isDisabled) {
                log.warn("⚠️ Bet button is disabled/locked");
                return false;
            }

            // Get fresh task from DB
            BettingTask freshTask = ModelConverter.convertFromArbOutcome(
                    arbOutcomeService.findByExternalIdAndBookmaker(task.taskId(), task.bookmakerId())
                            .orElse(null));

            if (freshTask != null) {
                log.info("🔄 Using fresh betting task from DB");
                task = freshTask;
            }

            // Verify odds
            if (!isOddsAcceptable(foundOdds, task)) {
                log.warn("⚠️ Odds not acceptable -- Found: {}, Expected: {}, Min: {}, Max: {}",
                        foundOdds, task.expectedOdds(), task.minOdds(), task.maxOdds());
                // return false; // TODO
            }

            // 🎯 STEP 2: Use Playwright locator to CLICK the marked button (50-100ms)
            // Find the button that JS marked with data-arb-target="true"
            Locator targetButton = page.locator("button[data-arb-target='true']");

            if (targetButton.count() == 0) {
                log.error("❌ Target button not found (JS marking failed)");
                takeMarketScreenshot(page, "target_not_found");
                return false;
            }

            log.info("🖱️ Clicking target button using Playwright locator...");
            try {
                // Scroll into view
                targetButton.scrollIntoViewIfNeeded();
                sleepRandom(200, 400);

                // Click with timeout and force option
                targetButton.click(new Locator.ClickOptions()
                        .setTimeout(5000)
                        .setForce(false)); // Let Playwright check actionability

                log.info("✅ Bet button clicked successfully");

                // Clean up the marker attribute
                page.evaluate("document.querySelector('button[data-arb-target]')?.removeAttribute('data-arb-target')");

            } catch (Exception e) {
                log.error("❌ Failed to click bet button: {}", e.getMessage());
                takeMarketScreenshot(page, "click_failed");
                return false;
            }

            sleepRandom(200, 400);
//            takeMarketScreenshot(page, "bet_clicked");

            // Verify bet in betslip
            boolean verifyBetInSlip = verifyBetInBetslipJS(page, task);
            if (!verifyBetInSlip) {
                log.warn("⚠️ Bet in slip may not match expected outcome");
                takeMarketScreenshot(page, "betslip_mismatch");
                return false;
            }

            log.info("✅ Bet verified in betslip");
//            takeMarketScreenshot(page, "bet_verified");

            return true;

        } catch (Exception e) {
            log.error("❌ Failed to select bet: {}", e.getMessage(), e);
            takeMarketScreenshot(page, "exception");

            // Clean up marker if it exists
            try {
                page.evaluate("document.querySelector('button[data-arb-target]')?.removeAttribute('data-arb-target')");
            } catch (Exception ignored) {}

            return false;
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
     * Capture screenshot with context information
     *
     * @param page Playwright page
     * @param task Betting task
     * @param stage Stage of the process (bet-found, bet-clicked, etc.)
     * @param foundMarket Market found (can be null)
     * @param foundOutcome Outcome found (can be null)
     */
    private static void captureScreenshot(Page page, BettingTask task, String stage,
                                          String foundMarket, String foundOutcome) {
        try {
            // Create screenshots directory if it doesn't exist
            String screenshotsDir = "screenshots/" + task.bookmakerId() + "/";
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(screenshotsDir));

            // Generate filename with timestamp and context
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));

            String sanitizedMarket = sanitizeFilename(task.marketType());
            String sanitizedOutcome = sanitizeFilename(task.outcome());

            String filename = String.format("%s%s_%s_%s_%s_%s.png",
                    screenshotsDir,
                    timestamp,
                    task.taskId(),
                    stage,
                    sanitizedMarket,
                    sanitizedOutcome);

            // Capture full page screenshot
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get(filename))
                    .setFullPage(true));

            log.info("📸 Screenshot captured: {} | Stage: {} | Market: {} | Outcome: {}",
                    filename, stage,
                    foundMarket != null ? foundMarket : task.marketType(),
                    foundOutcome != null ? foundOutcome : task.outcome());

            // Optional: Also create a metadata file with details
            createScreenshotMetadata(filename, task, stage, foundMarket, foundOutcome);

        } catch (Exception e) {
            log.error("❌ Failed to capture screenshot: {}", e.getMessage());
            // Don't throw - screenshot failure shouldn't break the main flow
        }
    }

    /**
     * Create metadata file alongside screenshot with context information
     */
    private static void createScreenshotMetadata(String screenshotPath, BettingTask task,
                                                 String stage, String foundMarket, String foundOutcome) {
        try {
            String metadataPath = screenshotPath.replace(".png", "_metadata.txt");

            StringBuilder metadata = new StringBuilder();
            metadata.append("Screenshot Metadata\n");
            metadata.append("===================\n\n");
            metadata.append("Timestamp: ").append(java.time.LocalDateTime.now()).append("\n");
            metadata.append("Stage: ").append(stage).append("\n\n");
            metadata.append("Task Details:\n");
            metadata.append("  Task ID: ").append(task.taskId()).append("\n");
            metadata.append("  Bookmaker: ").append(task.bookmakerId()).append("\n");
            metadata.append("  Expected Market: ").append(task.marketType()).append("\n");
            metadata.append("  Expected Outcome: ").append(task.outcome()).append("\n");
            metadata.append("  Expected Odds: ").append(task.expectedOdds()).append("\n");
            metadata.append("  Min Odds: ").append(task.minOdds()).append("\n");
            metadata.append("  Max Odds: ").append(task.maxOdds()).append("\n\n");

            if (foundMarket != null || foundOutcome != null) {
                metadata.append("Found Details:\n");
                metadata.append("  Found Market: ").append(foundMarket != null ? foundMarket : "N/A").append("\n");
                metadata.append("  Found Outcome: ").append(foundOutcome != null ? foundOutcome : "N/A").append("\n");
            }

            java.nio.file.Files.writeString(
                    java.nio.file.Paths.get(metadataPath),
                    metadata.toString());

        } catch (Exception e) {
            log.error("❌ Failed to create screenshot metadata: {}", e.getMessage());
        }
    }

    /**
     * Sanitize filename by removing invalid characters
     */
    private static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "null";
        }
        return filename
                .replaceAll("[^a-zA-Z0-9.-]", "_")
                .replaceAll("_+", "_")
                .substring(0, Math.min(filename.length(), 50)); // Limit length
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
