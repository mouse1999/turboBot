package com.mouse.bet.util.msport;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.mouse.bet.entity.Wallet;
import com.mouse.bet.enums.BookMaker;

import com.mouse.bet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for MSport login operations
 * Handles login, logout, login status verification, and wallet management
 */
@Slf4j

public class MSportLoginUtil {

    private static final String EMOJI_LOGIN = "🔐";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_SEARCH = "🔍";
    private static final String EMOJI_TARGET = "🎯";
    private static final String EMOJI_WARNING = "⚠️";

    @Value("${msport.username:}")
    private static String msportUsername;

    @Value("${msport.password:}")
    private static String msportPassword;

    @Value("${msport.login.url:https://www.msport.com/ng/web}")
    private static String loginUrl;

    /**
     * Perform login to MSport site
     *
     * @param page The Playwright page instance
     * @throws Exception if login fails
     */
    public static void performLogin(Page page) throws Exception {
        log.info("{} {} Attempting to login with username: {}", EMOJI_LOGIN, EMOJI_TARGET, msportUsername);

        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Check if already logged in
            if (checkIfLoggedIn(page)) {
                log.info("User is already logged in");
                return;
            }

            // Wait for login form to be visible
            log.info("Waiting for login form");
            Locator phoneInput = page.locator("input[type='tel'][placeholder='Mobile Phone']");
            Locator passwordInput = page.locator("input[type='password'][placeholder='Password']");
            Locator loginButton = page.locator("button.login:has-text('Login')");

            // Wait for elements to be visible
            phoneInput.waitFor(new Locator.WaitForOptions().setTimeout(10000));

            // Random delay before starting (simulate human behavior)
            sleepRandom(500, 1500);

            // Click on phone input field
            log.info("Clicking phone input field");
            phoneInput.click();
            sleepRandom(200, 500);

            // Clear any existing value
            phoneInput.clear();
            sleepRandom(100, 300);

            // Type phone number with human-like behavior
            log.info("Entering phone number");
            typeHumanLike(phoneInput, msportUsername);
            sleepRandom(300, 700);

            // Click on password input field
            log.info("Clicking password input field");
            passwordInput.click();
            sleepRandom(200, 500);

            // Clear any existing value
            passwordInput.clear();
            sleepRandom(100, 300);

            // Type password with human-like behavior
            log.info("Entering password");
            typeHumanLike(passwordInput, msportPassword);
            sleepRandom(500, 1000);

            // Click login button
            log.info("Clicking login button");
            loginButton.click();

            // Wait for navigation or login to complete
            sleepRandom(2000, 3000);

            // Extra safety: wait for network + post-login API calls
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(3000);

            log.info("{} {} Verifying login status...", EMOJI_LOGIN, EMOJI_SEARCH);

            if (!checkIfLoggedIn(page)) {
                log.error("{} {} Login verification failed – user not detected", EMOJI_ERROR, EMOJI_LOGIN);
                throw new Exception("Login verification failed – user not detected");
            }

            log.info("{} {} Login successful and verified!", EMOJI_SUCCESS, EMOJI_LOGIN);

        } catch (TimeoutError e) {
            log.error("{} {} Timeout waiting for login elements", EMOJI_ERROR, EMOJI_LOGIN, e);
            throw new Exception("Login timeout: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("{} {} Login failed: {}", EMOJI_ERROR, EMOJI_LOGIN, e.getMessage());
            throw new Exception("Login failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if user is currently logged in
     *
     * @param page The Playwright page instance
     * @return true if logged in, false otherwise
     * @throws Exception if check fails
     */
    public static boolean checkIfLoggedIn(Page page) throws Exception {
        log.info("{} {} Checking login status...", EMOJI_SEARCH, EMOJI_LOGIN);

        try {
            // First check: If login form is visible, user is definitely NOT logged in
            try {
                Locator loginButton = page.locator("button.login:has-text('Login')");
                if (loginButton.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                    log.info("Login form detected - user is NOT logged in");
                    return false;
                }
            } catch (TimeoutError e) {
                log.debug("No login form found, checking for logged-in elements");
            }

            // Check for specific logged-in elements from the account info section
            int visibleCount = 0;

            // Check 1: Account balance element
            try {
                Locator accountBalance = page.locator(".account--balance.account-item");
                if (accountBalance.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                    log.debug("Account balance element found");
                    visibleCount++;
                }
            } catch (TimeoutError e) {
                log.debug("Account balance element not found");
            }

            // Check 2: Deposit button
            try {
                Locator depositButton = page.locator("a.account-btn:has-text('Deposit')");
                if (depositButton.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                    log.debug("Deposit button found");
                    visibleCount++;
                }
            } catch (TimeoutError e) {
                log.debug("Deposit button not found");
            }

            // Check 3: My Bets button
            try {
                Locator myBetsButton = page.locator("a.account-btn:has-text('My Bets')");
                if (myBetsButton.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                    log.debug("My Bets button found");
                    visibleCount++;
                }
            } catch (TimeoutError e) {
                log.debug("My Bets button not found");
            }

            // Check 4: My Account button
            try {
                Locator myAccountButton = page.locator("a.account-btn.account.popper-my-accounts:has-text('My Account')");
                if (myAccountButton.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                    log.debug("My Account button found");
                    visibleCount++;
                }
            } catch (TimeoutError e) {
                log.debug("My Account button not found");
            }

            // Check 5: Account info container
            try {
                Locator accountInfo = page.locator(".account-info");
                if (accountInfo.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                    log.debug("Account info container found");
                    visibleCount++;
                }
            } catch (TimeoutError e) {
                log.debug("Account info container not found");
            }

            // If at least 2 logged-in indicators are present, consider user logged in
            boolean isLoggedIn = visibleCount >= 2;

            if (isLoggedIn) {
                log.info("{} {} User IS logged in ({} account elements detected)",
                        EMOJI_SUCCESS, EMOJI_LOGIN, visibleCount);
            } else {
                log.info("{} User is NOT logged in (only {} account elements detected)",
                        EMOJI_WARNING, visibleCount);
            }

            return isLoggedIn;

        } catch (Exception e) {
            log.error("{} Error checking login status: {}", EMOJI_ERROR, e.getMessage());
            return false;
        }
    }

    /**
     * Perform logout from MSport site
     *
     * @param page The Playwright page instance
     * @throws Exception if logout fails
     */
    public static void performLogout(Page page) throws Exception {
        log.info("{} {} Attempting to logout...", EMOJI_LOGIN, EMOJI_TARGET);

        try {
            // Find and click user profile/menu
            Locator userMenu = page.locator(".user-profile, .user-menu, .account-menu").first();

            if (userMenu.count() > 0 && userMenu.isVisible()) {
                userMenu.click();
                page.waitForTimeout(1000);

                // Click logout button
                Locator logoutButton = page.locator("button:has-text('Logout'), a:has-text('Logout')").first();

                if (logoutButton.count() > 0) {
                    logoutButton.click();
                    page.waitForTimeout(2000);

                    log.info("{} {} Logout completed", EMOJI_SUCCESS, EMOJI_LOGIN);
                } else {
                    log.warn("{} {} Logout button not found", EMOJI_WARNING, EMOJI_LOGIN);
                }
            } else {
                log.warn("{} {} User menu not found", EMOJI_WARNING, EMOJI_LOGIN);
            }

        } catch (Exception e) {
            log.error("{} {} Logout failed: {}", EMOJI_ERROR, EMOJI_LOGIN, e.getMessage());
            throw e;
        }
    }

    /**
     * Wait for login page to be fully loaded
     *
     * @param page The Playwright page instance
     * @throws Exception if page doesn't load
     */
    public static void waitForLoginPageReady(Page page) throws Exception {
        log.info("{} {} Waiting for login page to be ready...", EMOJI_SEARCH, EMOJI_LOGIN);

        try {
            // Wait for login form elements
            page.waitForSelector("input[type='tel'][placeholder='Mobile Phone']",
                    new Page.WaitForSelectorOptions().setTimeout(10000));
            page.waitForSelector("input[type='password'][placeholder='Password']",
                    new Page.WaitForSelectorOptions().setTimeout(10000));
            page.waitForSelector("button.login:has-text('Login')",
                    new Page.WaitForSelectorOptions().setTimeout(10000));

            log.info("{} {} Login page ready", EMOJI_SUCCESS, EMOJI_LOGIN);

        } catch (Exception e) {
            log.error("{} {} Login page not ready: {}", EMOJI_ERROR, EMOJI_LOGIN, e.getMessage());
            throw e;
        }
    }

    // ========================================================================
    // WALLET MANAGEMENT
    // ========================================================================

    /**
     * Updates wallet balance for MSport
     * @param page The Playwright Page object
     * @return true if balance was successfully updated, false otherwise
     */
    public  static boolean updateWalletBalance(Page page, WalletService walletService) {
        try {
            log.info("Updating wallet balance for MSport");

            // Wait for balance element to be visible with timeout
            Locator balanceContainer = page.locator(".account--balance.account-item");
            balanceContainer.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));

            // Get the balance text
            String balanceText = balanceContainer.textContent().trim();

            // Extract numeric value
            BigDecimal balance = extractBalanceAmount(balanceText);

            if (balance == null) {
                log.error("Failed to extract balance amount from text: {}", balanceText);
                return false;
            }

            log.info("Current MSport balance: NGN {}", balance);

            // Save balance using WalletService
            Wallet updatedWallet = walletService.saveBalance(BookMaker.MSPORT, balance);

            if (updatedWallet != null) {
                log.info("Successfully updated MSport wallet balance to NGN {}", balance);
                return true;
            } else {
                log.warn("Failed to save MSport balance to database");
                return false;
            }

        } catch (Exception e) {
            log.error("Error updating wallet balance for MSport: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Deduct bet stake from wallet balance
     */
    public static void spendAmount(BigDecimal betAmount, String arbId, WalletService walletService) {
        Wallet updatedWallet = walletService.spend(BookMaker.MSPORT, betAmount);

        if (updatedWallet != null) {
            log.info("SUCCESS: Bet stake deducted for arbId={}, bookmaker=M_SPORT: {} - New balance: {}",
                    arbId, betAmount, updatedWallet.getAvailableBalance());
        } else {
            log.error("FAILED: Could not deduct bet stake for arbId={}, bookmaker=M_SPORT: {} - Spend operation returned null",
                    arbId, betAmount);
        }
    }

    /**
     * Credit amount back to balance (for rollback scenarios)
     */
//    public static void creditAmount(double amount, String arbId, WalletService walletService) {
//        log.info("🔄 Crediting {} back to MSport balance (rollback) | ArbId: {}", amount, arbId);
//
//        BigDecimal creditAmount = BigDecimal.valueOf(amount);
//        Wallet updatedWallet = walletService.credit(BookMaker.MSPORT, creditAmount);
//
//        if (updatedWallet != null) {
//            log.info("SUCCESS: Rollback credit completed for arbId={}, bookmaker=M_SPORT: {} - New balance: {}",
//                    arbId, creditAmount, updatedWallet.getAvailableBalance());
//        } else {
//            log.error("FAILED: Could not credit rollback amount for arbId={}, bookmaker=M_SPORT: {}",
//                    arbId, creditAmount);
//        }
//    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Extracts the numeric balance amount from balance text
     * @param balanceText The balance text (e.g., "NGN 47.90")
     * @return BigDecimal balance or null if extraction fails
     */
    private static BigDecimal extractBalanceAmount(String balanceText) {
        try {
            String cleaned = balanceText
                    .replaceAll("NGN", "")
                    .replaceAll("[^0-9.]", "")
                    .trim();

            if (cleaned.isEmpty()) {
                return null;
            }

            return new BigDecimal(cleaned);

        } catch (NumberFormatException e) {
            log.error("Failed to parse balance amount: {}", balanceText, e);
            return null;
        }
    }

    /**
     * Types text with human-like behavior including random delays and occasional typos
     */
    public static void typeHumanLike(Locator locator, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        log.debug("Typing text with human-like behavior (length: {})", text.length());

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);

            // 5% chance of making a typo (only for letters, not numbers)
            if (ThreadLocalRandom.current().nextInt(100) < 5 && Character.isLetter(currentChar)) {
                // Type wrong character
                char wrongChar = getRandomWrongChar(currentChar);
                locator.pressSequentially(String.valueOf(wrongChar),
                        new Locator.PressSequentiallyOptions().setDelay(randomDelay(50, 150)));

                // Pause (realize mistake)
                sleepRandom(100, 300);

                // Backspace to correct
                locator.press("Backspace");
                sleepRandom(50, 150);
            }

            // Type the correct character
            locator.pressSequentially(String.valueOf(currentChar),
                    new Locator.PressSequentiallyOptions().setDelay(randomDelay(50, 200)));

            // Occasional longer pause (simulates thinking)
            if (i > 0 && i % randomInt(3, 6) == 0 && ThreadLocalRandom.current().nextInt(100) < 10) {
                sleepRandom(200, 500);
            }
        }
    }

    /**
     * Get a random wrong character near the correct one on QWERTY keyboard
     */
    private static char getRandomWrongChar(char correctChar) {
        String[][] keyboard = {
                {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
                {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
                {"z", "x", "c", "v", "b", "n", "m"}
        };

        char lower = Character.toLowerCase(correctChar);

        for (int row = 0; row < keyboard.length; row++) {
            for (int col = 0; col < keyboard[row].length; col++) {
                if (keyboard[row][col].charAt(0) == lower) {
                    int direction = ThreadLocalRandom.current().nextInt(4);
                    int newRow = row;
                    int newCol = col;

                    switch (direction) {
                        case 0: newCol++; break;
                        case 1: newCol--; break;
                        case 2: newRow--; break;
                        case 3: newRow++; break;
                    }

                    if (newRow >= 0 && newRow < keyboard.length &&
                            newCol >= 0 && newCol < keyboard[newRow].length) {
                        char wrongChar = keyboard[newRow][newCol].charAt(0);
                        return Character.isUpperCase(correctChar)
                                ? Character.toUpperCase(wrongChar)
                                : wrongChar;
                    }
                }
            }
        }

        int offset = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        char wrongChar = (char) (lower + offset);
        return Character.isUpperCase(correctChar)
                ? Character.toUpperCase(wrongChar)
                : wrongChar;
    }

    public static void typeFastHumanLike(Locator locator, String text) {
        // Optional: small random delay before starting (mimics human reaction)
        randomDelay(80, 250);

        // Focus the field first (critical for some betting sites)
        locator.evaluate("el => el.focus()");

        // Convert text to char array for per-character typing
        char[] chars = text.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            String charStr = String.valueOf(c);

            // 1. Type the character
            locator.press(charStr);

            // 2. Human-like typing speed: 80–220 ms per character (avg ~140ms = ~7 chars/sec)
            int baseDelay = 80 + (i % 3 == 0 ? 60 : 0); // slight rhythm variation
            randomDelay(baseDelay, baseDelay + 140);

            // 3. 3% chance of a tiny "thinking" pause (200–600ms) — makes it ultra-realistic
            if (Math.random() < 0.03) {
                randomDelay(200, 600);
            }

            // 4. 1% chance of a small backspace + retype (classic human typo fix)
            if (Math.random() < 0.01 && i > 0) {
                locator.press("Backspace");
                randomDelay(100, 300);
                locator.press(charStr); // retype the same char
                randomDelay(120, 280);
            }
        }

        // Final small pause after typing (human habit)
        randomDelay(100, 350);
    }

    /**
     * Generate random delay in milliseconds
     */
    private static int randomDelay(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Generate random integer between min and max (inclusive)
     */
    private static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Sleep for a random duration
     */
    private static void sleepRandom(int minMs, int maxMs) {
        try {
            Thread.sleep(randomDelay(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}