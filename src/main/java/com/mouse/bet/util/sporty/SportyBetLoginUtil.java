package com.mouse.bet.util.sporty;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class SportyBetLoginUtil {

    /**
     * Checks if user is currently logged in by looking for account-specific elements
     * @param page Playwright page object
     * @return true if logged in, false otherwise
     */
    public static boolean checkIfLoggedIn(Page page) {
        try {
            log.info("Checking if user is logged in");

            // Quick check: If login form is visible, user is NOT logged in
            if (isLoginFormVisible(page)) {
                log.info("Login form detected - user is NOT logged in");
                return false;
            }

            // Check for logged-in indicators
            int visibleIndicators = countVisibleLoginIndicators(page);
            boolean isLoggedIn = visibleIndicators >= 2;

            log.info("User {} logged in ({} account elements detected)",
                    isLoggedIn ? "IS" : "is NOT", visibleIndicators);

            return isLoggedIn;

        } catch (Exception e) {
            log.error("Error checking login status", e);
            return false;
        }
    }

    /**
     * Performs login on Sporty website
     * @param page Playwright page object
     * @param username Phone number (without country code, e.g., "8012345678")
     * @param password User password
     */
    public static void performLogin(Page page, String username, String password) {
        log.info("🔐 Starting SportyBet login process");

        try {
            // Step 1: Check if already logged in
            if (checkIfLoggedIn(page)) {
                log.info("✅ Already logged in - skipping login");
                return;
            }

            // Step 2: Wait for login form
            log.info("⏳ Waiting for login form...");
            Locator phoneInput = page.locator("input[name='phone'][type='text'][placeholder='Mobile Number']");
            Locator passwordInput = page.locator("input[name='psd'][type='password']");
            Locator loginButton = page.locator("button[name='logIn'], button.m-btn-login");

            phoneInput.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(15000));

            log.info("✅ Login form detected");

            // Step 3: Fill in credentials with human-like typing
            sleepRandom(600, 1200);

            phoneInput.click();
            sleepRandom(300, 700);
            phoneInput.fill("");
            typeHumanLike(phoneInput, username);
            log.info("⌨️ Phone entered: {}", maskPhoneNumber(username));

            sleepRandom(400, 800);
            passwordInput.click();
            sleepRandom(300, 700);
            passwordInput.fill("");
            typeHumanLike(passwordInput, password);
            log.info("⌨️ Password entered");

            // Step 4: Wait for login button to become enabled
            log.info("⏳ Waiting for login button to become enabled...");
            try {
                page.waitForFunction(
                        "() => { const btn = document.querySelector('button[name=\"logIn\"], button.m-btn-login'); " +
                                "return btn && !btn.disabled && !btn.classList.contains('disabled') && btn.offsetParent !== null; }",
                        null,
                        new Page.WaitForFunctionOptions().setTimeout(8000)
                );
                log.info("✅ Login button is enabled");
            } catch (TimeoutError e) {
                log.warn("⚠️ Login button still disabled after timeout");
                return;
            }

            // Step 5: Click the login button
            if (!clickLoginButton(page, loginButton)) {
                log.error("❌ Failed to click login button");
                return;
            }

            // Step 6: Wait for login to complete
            log.info("⏳ Waiting for login to complete...");
            sleepRandom(3000, 6000);

            // Step 7: Check for error messages
            Locator errorToast = page.locator("div.m-error-toast, .toast-error, .error-message");
            if (errorToast.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                String errorText = errorToast.textContent();
                log.error("❌ Login failed - error shown: {}", errorText);
                return;
            }

            // Step 8: Final verification
            boolean success = checkIfLoggedIn(page);
            if (success) {
                log.info("✅ Login successful!");
            } else {
                log.error("❌ Login failed - user not detected as logged in");
            }

        } catch (TimeoutError e) {
            log.error("⏱️ Timeout during login: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error during login: {}", e.getMessage(), e);
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private static boolean isLoginFormVisible(Page page) {
        try {
            Locator loginButton = page.locator("button[name='logIn']");
            return loginButton.isVisible(new Locator.IsVisibleOptions().setTimeout(1000));
        } catch (TimeoutError e) {
            return false;
        }
    }

    private static int countVisibleLoginIndicators(Page page) {
        String[] indicators = {
                ".m-balance",
                "a[href*='deposit']",
                "a[href*='bet_history']",
                "#j_userInfo",
                ".m-user-center"
        };

        int count = 0;
        for (String selector : indicators) {
            if (isElementVisible(page, selector, 1000)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isElementVisible(Page page, String selector, int timeoutMs) {
        try {
            return page.locator(selector)
                    .isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
        } catch (TimeoutError e) {
            return false;
        }
    }

    private static boolean clickLoginButton(Page page, Locator loginButton) {
        log.info("🖱️ Attempting to click login button");

        try {
            loginButton.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(12000));

            loginButton.scrollIntoViewIfNeeded();
            sleepRandom(400, 900);
        } catch (Exception e) {
            log.warn("⚠️ Login button not visible: {}", e.getMessage());
            return false;
        }

        // Try standard click
        try {
            loginButton.click(new Locator.ClickOptions()
                    .setForce(true)
                    .setTimeout(6000));

            if (checkIfLoggedIn(page)) {
                log.info("✅ Login button clicked successfully");
                return true;
            }
        } catch (Exception e) {
            log.warn("⚠️ Standard click failed: {}", e.getMessage());
        }

        // JavaScript click fallback
        try {
            page.evaluate("""
            () => {
                const btn = document.querySelector('button[name="logIn"]') || 
                             document.querySelector('button.m-btn-login');
                if (btn) {
                    btn.style.pointerEvents = 'auto';
                    btn.style.opacity = '1';
                    btn.style.zIndex = '9999';
                    btn.click();
                    
                    const event = new MouseEvent('click', {
                        view: window,
                        bubbles: true,
                        cancelable: true
                    });
                    btn.dispatchEvent(event);
                }
            }
            """);

            log.info("✅ Login button clicked via JavaScript");
            sleepRandom(1500, 2500);
            return true;
        } catch (Exception e) {
            log.warn("⚠️ JavaScript click failed: {}", e.getMessage());
        }

        return false;
    }

    private static void typeHumanLike(Locator locator, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);

            // 5% chance of typo (only for letters)
            if (ThreadLocalRandom.current().nextInt(100) < 5 && Character.isLetter(currentChar)) {
                char wrongChar = getRandomWrongChar(currentChar);
                locator.pressSequentially(String.valueOf(wrongChar),
                        new Locator.PressSequentiallyOptions().setDelay(randomDelay(50, 150)));
                sleepRandom(100, 300);
                locator.press("Backspace");
                sleepRandom(50, 150);
            }

            locator.pressSequentially(String.valueOf(currentChar),
                    new Locator.PressSequentiallyOptions().setDelay(randomDelay(50, 200)));

            // Occasional pause
            if (i > 0 && i % randomInt(3, 6) == 0 && ThreadLocalRandom.current().nextInt(100) < 10) {
                sleepRandom(200, 500);
            }
        }
    }

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

    private static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return "****";
        }
        int visibleDigits = 4;
        String masked = "*".repeat(phoneNumber.length() - visibleDigits);
        return masked + phoneNumber.substring(phoneNumber.length() - visibleDigits);
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

    private static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static void sleepRandom(int minMs, int maxMs) {
        try {
            Thread.sleep(randomDelay(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}