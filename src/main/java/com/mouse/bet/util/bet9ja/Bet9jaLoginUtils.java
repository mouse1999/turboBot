package com.mouse.bet.util.bet9ja;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class Bet9jaLoginUtils {
    private static final String EMOJI_LOGIN = "🔐";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_SEARCH = "🔍";
    private static final String EMOJI_INFO = "ℹ️";

    /**
     * Check if user is already logged in
     *
     * @param page The Playwright page instance
     * @return true if user is logged in, false otherwise
     */
    public static boolean checkIfLoggedIn(Page page) {
        try {
            log.debug("{} {} Checking if user is already logged in...", EMOJI_SEARCH, EMOJI_LOGIN);

            // Look for login button - if visible, user is NOT logged in
            Locator loginButton = findLoginButton(page);

            try {
                boolean isVisible = loginButton.isVisible(
                        new Locator.IsVisibleOptions().setTimeout(30000));

                if (isVisible) {
                    log.info("{} {} User is NOT logged in - Login button is visible",
                            EMOJI_WARNING, EMOJI_LOGIN);
                    return false;
                } else {
                    log.info("{} {} User is logged in - Login button is not visible",
                            EMOJI_SUCCESS, EMOJI_LOGIN);
                    return true;
                }
            } catch (Exception e) {
                // If login button not found, assume user is logged in
                log.info("{} {} User appears to be logged in - No login button found",
                        EMOJI_SUCCESS, EMOJI_LOGIN);
                return true;
            }

        } catch (Exception e) {
            log.error("{} {} Error checking login status: {}",
                    EMOJI_ERROR, EMOJI_LOGIN, e.getMessage());
            return false;
        }
    }

    /**
     * Perform login to 1xBet
     *
     * @param page The Playwright page instance
     * @param username User's username/email
     * @param password User's password
     * @return true if login successful, false otherwise
     */
    public static boolean performLogin(Page page, String username, String password) {
        try {
            log.info("{} {} Starting 1xBet login process for user: {}",
                    EMOJI_LOGIN, EMOJI_INFO, username);

            // Check if already logged in
            if (checkIfLoggedIn(page)) {
                log.info("{} {} Already logged in - skipping login",
                        EMOJI_SUCCESS, EMOJI_LOGIN);
                return true;
            }

            // ========================================
            // STEP 1: FIND AND CLICK LOGIN BUTTON
            // ========================================
            log.info("{} {} [1/4] Looking for login button...", EMOJI_SEARCH, EMOJI_LOGIN);

            Locator loginButton = findLoginButton(page);

            if (loginButton == null) {
                log.error("{} {} Login button not found", EMOJI_ERROR, EMOJI_LOGIN);
                return false;
            }

            log.info("{} {} Clicking login button...", EMOJI_INFO, EMOJI_LOGIN);
            loginButton.scrollIntoViewIfNeeded();
            randomHumanDelay(300, 500);
            loginButton.click();
            log.info("{} {} Login button clicked successfully", EMOJI_SUCCESS, EMOJI_LOGIN);

            // Wait for dropdown to appear
            randomHumanDelay(2500, 3500);

            // ========================================
            // STEP 2: FIND AND FILL USERNAME FIELD
            // ========================================
            log.info("{} {} [2/4] Looking for username field...", EMOJI_SEARCH, EMOJI_LOGIN);

            Locator usernameField = findUsernameField(page);

            if (usernameField == null) {
                log.error("{} {} Username field not found", EMOJI_ERROR, EMOJI_LOGIN);
                return false;
            }

            log.info("{} {} Typing username...", EMOJI_INFO, EMOJI_LOGIN);
            usernameField.scrollIntoViewIfNeeded();
            usernameField.click();
//            usernameField.fill(username);
            typeHumanLike(usernameField, username);
            log.info("{} {} Username entered: {}", EMOJI_SUCCESS, EMOJI_LOGIN, username);

            randomHumanDelay(600, 1200);

            // ========================================
            // STEP 3: FIND AND FILL PASSWORD FIELD
            // ========================================
            log.info("{} {} [3/4] Looking for password field...", EMOJI_SEARCH, EMOJI_LOGIN);

            Locator passwordField = findPasswordField(page);

            if (passwordField == null) {
                log.error("{} {} Password field not found", EMOJI_ERROR, EMOJI_LOGIN);
                return false;
            }

            log.info("{} {} Typing password...", EMOJI_INFO, EMOJI_LOGIN);
            passwordField.scrollIntoViewIfNeeded();
            passwordField.click();
//            passwordField.fill(password);
            typeHumanLike(passwordField, password);
            log.info("{} {} Password entered (hidden for security)", EMOJI_SUCCESS, EMOJI_LOGIN);

            randomHumanDelay(1000, 1500);

            // ========================================
            // STEP 4: FIND AND CLICK SUBMIT BUTTON
            // ========================================
            log.info("{} {} [4/4] Looking for submit button...", EMOJI_SEARCH, EMOJI_LOGIN);

            Locator submitButton = findSubmitButton(page);

            if (submitButton == null) {
                log.error("{} {} Submit button not found", EMOJI_ERROR, EMOJI_LOGIN);
                return false;
            }

            log.info("{} {} Clicking submit button...", EMOJI_INFO, EMOJI_LOGIN);
            submitButton.scrollIntoViewIfNeeded();
            randomHumanDelay(300, 500);
            submitButton.click();
            log.info("{} {} Submit button clicked", EMOJI_SUCCESS, EMOJI_LOGIN);

            // Wait for login to process
            log.info("{} {} Waiting for login to process...", EMOJI_INFO, EMOJI_LOGIN);
            randomHumanDelay(5000, 7000);

            // ========================================
            // STEP 5: VERIFY LOGIN
            // ========================================
            log.info("{} {} Verifying login status...", EMOJI_SEARCH, EMOJI_LOGIN);
            boolean loggedIn = checkIfLoggedIn(page);

            if (loggedIn) {
                log.info("{} {} LOGIN SUCCESSFUL for user: {}",
                        EMOJI_SUCCESS, EMOJI_LOGIN, username);
                return true;
            } else {
                log.error("{} {} Login failed - still showing login button",
                        EMOJI_ERROR, EMOJI_LOGIN);

                // Try to capture error messages
                captureLoginErrors(page);
                return false;
            }

        } catch (Exception e) {
            log.error("{} {} Exception during login: {}",
                    EMOJI_ERROR, EMOJI_LOGIN, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Find login button using multiple strategies
     */
    private static Locator findLoginButton(Page page) {
        Locator loginButton = null;

        // Strategy 1: By specific class name 'btn-login'
        try {
            log.debug("Strategy 1: Finding by class 'btn-login'...");
            loginButton = page.locator("div.btn-login");

            if (loginButton.count() > 0 && loginButton.first().isVisible()) {
                log.debug("✅ Login button found via btn-login class strategy");
                return loginButton.first();
            }
        } catch (Exception e) {
            log.debug("Strategy 1 failed: {}", e.getMessage());
        }

        // Strategy 2: By combined classes 'btn-primary-m' and 'btn-login'
        try {
            log.debug("Strategy 2: Finding by classes 'btn-primary-m.btn-login'...");
            loginButton = page.locator("div.btn-primary-m.btn-login");

            if (loginButton.count() > 0 && loginButton.first().isVisible()) {
                log.debug("✅ Login button found via combined classes strategy");
                return loginButton.first();
            }
        } catch (Exception e) {
            log.debug("Strategy 2 failed: {}", e.getMessage());
        }

        // Strategy 3: By title attribute
        try {
            log.debug("Strategy 3: Finding by title attribute 'Login'...");
            loginButton = page.locator("div[title='Login']");

            if (loginButton.count() > 0 && loginButton.first().isVisible()) {
                log.debug("✅ Login button found via title attribute strategy");
                return loginButton.first();
            }
        } catch (Exception e) {
            log.debug("Strategy 3 failed: {}", e.getMessage());
        }

        // Strategy 4: By text content "Login" (exact match)
        try {
            log.debug("Strategy 4: Finding by text 'Login'...");
            loginButton = page.getByText("Login",
                    new Page.GetByTextOptions().setExact(true));

            if (loginButton.count() > 0 && loginButton.first().isVisible()) {
                log.debug("✅ Login button found via text strategy");
                return loginButton.first();
            }
        } catch (Exception e) {
            log.debug("Strategy 4 failed: {}", e.getMessage());
        }


        // Strategy 6: CSS selector with all identifiers
        try {
            log.debug("Strategy 6: Finding by full CSS selector...");
            loginButton = page.locator("div.h-ml__acc-item > div.btn-primary-m.btn-login[title='Login']");

            if (loginButton.count() > 0 && loginButton.first().isVisible()) {
                log.debug("✅ Login button found via full CSS selector strategy");
                return loginButton.first();
            }
        } catch (Exception e) {
            log.debug("Strategy 6 failed: {}", e.getMessage());
        }

        log.warn("{} {} Login button not found after trying all strategies",
                EMOJI_WARNING, EMOJI_SEARCH);
        return null;
    }

    /**
     * Find username field using multiple strategies
     */
    /**
     * Find username field using multiple strategies for Bet9ja
     */
    private static Locator findUsernameField(Page page) {
        String[] usernameSelectors = {
                "input#username",
                "input[placeholder='Mobile Number or Username']",
                "div.login__popup input#username",
                "div.form input[type='text']#username",
                "input[type='text'][placeholder*='Mobile Number' i]",
                "input[type='text'][placeholder*='Username' i]",
                "div.form__row input[type='text']"
        };

        for (String selector : usernameSelectors) {
            try {
                log.debug("Trying username selector: {}", selector);
                Locator field = page.locator(selector).first();

                if (field.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                    log.debug("✅ Username field found with selector: {}", selector);
                    return field;
                }
            } catch (Exception e) {
                log.debug("Selector '{}' not found or not visible", selector);
            }
        }

        log.warn("{} {} Username field not found", EMOJI_WARNING, EMOJI_SEARCH);
        return null;
    }

    /**
     * Find password field using multiple strategies for Bet9ja
     */
    private static Locator findPasswordField(Page page) {
        String[] passwordSelectors = {
                "input#password",
                "input[type='password']#password",
                "div.login__popup input[type='password']",
                "div.input__holder input[type='password']",
                "div.form__row input[type='password']",
                "input[type='password'][placeholder='Password']",
                "input[type='password']"
        };

        for (String selector : passwordSelectors) {
            try {
                log.debug("Trying password selector: {}", selector);
                Locator field = page.locator(selector).first();

                if (field.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                    log.debug("✅ Password field found with selector: {}", selector);
                    return field;
                }
            } catch (Exception e) {
                log.debug("Selector '{}' not found or not visible", selector);
            }
        }

        log.warn("{} {} Password field not found", EMOJI_WARNING, EMOJI_SEARCH);
        return null;
    }

    /**
     * Find submit button using multiple strategies for Bet9ja
     */
    private static Locator findSubmitButton(Page page) {
        Locator submitButton = null;

        // Strategy 1: By specific Bet9ja class 'btn-primary-l'
        try {
            log.debug("Looking for div with class 'btn-primary-l'...");
            submitButton = page.locator("div.btn-primary-l").first();

            if (submitButton.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                log.debug("✅ Submit button found (btn-primary-l class)");
                return submitButton;
            }
        } catch (Exception e) {
            log.debug("Submit button with btn-primary-l class not found");
        }

        // Strategy 2: By class and text content
        try {
            log.debug("Looking for div with class 'btn-primary-l' and text 'Log In'...");
            submitButton = page.locator("div.btn-primary-l.mt20").first();

            if (submitButton.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                String text = submitButton.textContent();
                if (text != null && text.trim().equals("Log In")) {
                    log.debug("✅ Submit button found (btn-primary-l with 'Log In' text)");
                    return submitButton;
                }
            }
        } catch (Exception e) {
            log.debug("Submit button with btn-primary-l.mt20 not found");
        }

        // Strategy 3: Within login popup container
        try {
            log.debug("Looking for submit button inside login__popup...");
            Locator loginPopup = page.locator("div.login__popup");

            if (loginPopup.count() > 0) {
                submitButton = loginPopup.locator("div.btn-primary-l").first();

                if (submitButton.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                    log.debug("✅ Submit button found inside login__popup");
                    return submitButton;
                }
            }
        } catch (Exception e) {
            log.debug("Could not find submit button in login__popup: {}", e.getMessage());
        }

        // Strategy 4: By text content "Log In" within form
        try {
            log.debug("Looking for elements with text 'Log In' in form...");
            Locator form = page.locator("div.form");

            if (form.count() > 0) {
                submitButton = form.getByText("Log In"
                ).first();

                if (submitButton.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                    log.debug("✅ Submit button found by text in form");
                    return submitButton;
                }
            }
        } catch (Exception e) {
            log.debug("Could not find submit button by text in form: {}", e.getMessage());
        }

        // Strategy 5: Any div with btn-primary-l class
        try {
            log.debug("Looking for any div with 'btn-primary' class...");
            Locator allButtons = page.locator("div[class*='btn-primary']");
            int count = allButtons.count();

            for (int i = 0; i < count; i++) {
                Locator btn = allButtons.nth(i);
                String text = btn.textContent();

                if (text != null && text.trim().equals("Log In")) {
                    if (btn.isVisible()) {
                        log.debug("✅ Submit button found (text='Log In') at index {}", i);
                        return btn;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not find submit button with btn-primary: {}", e.getMessage());
        }

        log.warn("{} {} Submit button not found", EMOJI_WARNING, EMOJI_SEARCH);
        return null;
    }

    /**
     * Capture and log any error messages on the page
     */
    private static void captureLoginErrors(Page page) {
        try {
            Locator errorMessages = page.locator(".error, .alert, [class*='error']");
            int errorCount = errorMessages.count();

            if (errorCount > 0) {
                log.warn("{} {} Found {} error message(s):",
                        EMOJI_WARNING, EMOJI_ERROR, errorCount);

                for (int i = 0; i < errorCount; i++) {
                    Locator error = errorMessages.nth(i);
                    if (error.isVisible()) {
                        String errorText = error.textContent();
                        log.error("Error {}: {}", i, errorText);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not check for errors: {}", e.getMessage());
        }
    }


    public static void waitForPageReady(Page page) {
        try {
            log.info("⏳ Waiting for page to be ready...");

            // Just wait for DOM to be ready (don't use NETWORKIDLE - betting sites never reach it)
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Give Vue.js time to render
            randomHumanDelay(3000, 6000);

            log.info("✅ Page DOM ready");

        } catch (Exception e) {
            log.warn("Page ready check issue: {}", e.getMessage());
            randomHumanDelay(2000, 3000);
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
                randomHumanDelay(100, 300);

                // Backspace to correct
                locator.press("Backspace");
                randomHumanDelay(50, 150);
            }

            // Type the correct character
            locator.pressSequentially(String.valueOf(currentChar),
                    new Locator.PressSequentiallyOptions().setDelay(randomDelay(50, 200)));

            // Occasional longer pause (simulates thinking)
            if (i > 0 && i % randomInt(3, 6) == 0 && ThreadLocalRandom.current().nextInt(100) < 10) {
                randomHumanDelay(200, 500);
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

    private static int randomDelay(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }




    /**
     * Human-like random delay to avoid bot detection
     *
     * @param minMs Minimum delay in milliseconds
     * @param maxMs Maximum delay in milliseconds
     */
    private static void randomHumanDelay(long minMs, long maxMs) {
        try {
            long delay = minMs + ThreadLocalRandom.current().nextLong(maxMs - minMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Delay interrupted");
        }
    }

    /**
     * Generate random integer between min and max (inclusive)
     */
    private static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }





}
