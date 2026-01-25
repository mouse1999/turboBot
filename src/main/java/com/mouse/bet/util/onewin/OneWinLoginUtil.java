package com.mouse.bet.util.onewin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class OneWinLoginUtil {
    public static boolean checkIfLoggedIn(Page page) {
        try {
            // Check if the Deposit button is visible (indicates user is logged in)
            // Use data-testid which is more stable than dynamic class names
            Locator depositButton = page.locator("button[data-testid='header-balance-deposit-button']");

            // Wait for a short time to see if element exists
            depositButton.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(3000));

            log.info("User is already logged in");
            return true;

        } catch (PlaywrightException e) {
            // Element not found - user is not logged in
            log.info("User is not logged in");
            return false;
        }
    }

    public static void performLogin(Page page, String oneUsername, String onePassword) {
        // Check if already logged in
        if (checkIfLoggedIn(page)) {
            log.info("Already logged in, skipping login process");
            return;
        }

        try {
            // Step 1: Click the main login button to open the modal
            page.locator("button[data-testid='header-auth-button']").click();
            sleepRandom(500, 1000); // Human-like pause after clicking

            // Step 2: Wait for the login modal to appear
            page.locator("div[data-testid='recoveryPassword-content-sign-in']")
                    .waitFor(new Locator.WaitForOptions().setTimeout(5000));

            sleepRandom(300, 700); // Brief pause to "read" the modal

            // Step 3: Enter phone number (username) with human-like typing
            Locator phoneInput = page.locator("input[data-testid='signInByPhone-form-phone']");
            phoneInput.waitFor();
            phoneInput.click(); // Click to focus
            sleepRandom(200, 400);
            typeHumanLike(phoneInput, oneUsername);

            sleepRandom(300, 600); // Pause between fields

            // Step 4: Enter password with human-like typing
            Locator passwordInput = page.locator("input[data-testid='signInByPhone-form-password']");
            passwordInput.waitFor();
            passwordInput.click(); // Click to focus
            sleepRandom(200, 400);
            typeHumanLike(passwordInput, onePassword);

            sleepRandom(400, 800); // Pause before submitting (human hesitation)

            // Step 5: Click the submit/login button inside the modal
            page.locator("button[data-testid='signInByPhone-form-submit']").click();

            // Step 6: Wait for login to complete - check for Deposit button
            page.locator("div.base_content-EW6Gm:has-text('Deposit')")
                    .waitFor(new Locator.WaitForOptions().setTimeout(10000));

            log.info("Login completed successfully");

        } catch (PlaywrightException e) {
            log.error("Login failed: " + e.getMessage());
            throw e;
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

    private static int randomDelay(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static void sleepRandom(int minMs, int maxMs) {
        try {
            Thread.sleep(randomDelay(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Generate random integer between min and max (inclusive)
     */
    private static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }





}
