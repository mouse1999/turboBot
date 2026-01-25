package com.mouse.bet.window;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class OneXBetLoginTest {

    private static final String ONEXBET_URL = "https://1xbet.com";
    private static final boolean HEADLESS = false;
    private static final int SLOW_MO = 50;

    public static void main(String[] args) {
        testLogin();
    }

    /**
     * Test the login functionality
     */
    public static void testLogin() {
        String username = System.getenv("ONEXBET_USERNAME");
        String password = System.getenv("ONEXBET_PASSWORD");

        if (username == null || password == null) {
            System.err.println("❌ Please set ONEXBET_USERNAME and ONEXBET_PASSWORD environment variables");
            System.out.println("💡 Example: export ONEXBET_USERNAME=your_username");
            System.out.println("💡 Example: export ONEXBET_PASSWORD=your_password");

            username = "kufreedwarde26@gmail.com";
            password = "Victor?!$#070";
            System.out.println("⚠️ Using default test credentials (will likely fail)");
        }

        System.out.println("\n🚀 Starting 1xBet Login Test");
        System.out.println("📝 Username: " + username);
        System.out.println("🔑 Password: " + password.substring(0, Math.min(4, password.length())) + "********");

        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;
        Page page = null;

        try {
            System.out.println("\n🎭 Launching Playwright...");
            playwright = Playwright.create();

            System.out.println("🌐 Launching browser...");
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(HEADLESS)
                    .setSlowMo(SLOW_MO)
                    .setArgs(java.util.Arrays.asList(
                            "--start-maximized",
                            "--window-size=2560,1440",
                            "--force-device-scale-factor=1",
                            "--disable-blink-features=AutomationControlled"
                    ))
            );

            System.out.println("📄 Creating browser context...");
            context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(1920, 1080)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setLocale("en-US")
                    .setTimezoneId("America/New_York")
            );

            System.out.println("📃 Creating new page...");
            page = context.newPage();

            System.out.println("🌍 Navigating to " + ONEXBET_URL + "...");
            page.navigate(ONEXBET_URL, new Page.NavigateOptions().setTimeout(30000));
            System.out.println("✅ Page loaded successfully");

            // Wait for DOM to be ready (don't use NETWORKIDLE - it times out on betting sites)
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            Thread.sleep(4000); // Wait for Vue to render

            System.out.println("\n🔍 Checking login status...");
            boolean alreadyLoggedIn = checkIfLoggedIn(page);

            if (alreadyLoggedIn) {
                System.out.println("✅ Already logged in!");
                return;
            }

            System.out.println("\n🔐 Attempting login...");
            boolean loginSuccess = performLogin(page, username, password);

            if (loginSuccess) {
                System.out.println("\n==================================================");
                System.out.println("✅✅✅ LOGIN TEST PASSED ✅✅✅");
                System.out.println("🎉 Successfully logged in to 1xBet");
                System.out.println("==================================================\n");

                takeScreenshot(page, "login_success.png");

                System.out.println("⏳ Keeping browser open for 5 seconds...");
                Thread.sleep(5000);

            } else {
                System.err.println("\n==================================================");
                System.err.println("❌❌❌ LOGIN TEST FAILED ❌❌❌");
                System.err.println("❌ Failed to log in to 1xBet");
                System.err.println("==================================================\n");

                takeScreenshot(page, "login_failure.png");

                System.out.println("⏳ Keeping browser open for 10 seconds for investigation...");
                Thread.sleep(10000);
            }

        } catch (Exception e) {
            System.err.println("\n❌ Test failed with exception: " + e.getMessage());
            e.printStackTrace();

            if (page != null) {
                takeScreenshot(page, "login_error.png");
            }

        } finally {
            cleanup(page, context, browser, playwright);
        }
    }

    /**
     * Check if user is already logged in
     */
    private static boolean checkIfLoggedIn(Page page) {
        try {
            System.out.println("🔍 Checking if already logged in...");

            Locator loginButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in"));

            try {
                boolean isVisible = loginButton.isVisible(new Locator.IsVisibleOptions().setTimeout(3000));

                if (isVisible) {
                    System.out.println("❌ User is NOT logged in - Login button is visible");
                    return false;
                } else {
                    System.out.println("✅ User is logged in - Login button is not visible");
                    return true;
                }
            } catch (Exception e) {
                System.out.println("✅ User appears to be logged in - No login button found");
                return true;
            }

        } catch (Exception e) {
            System.err.println("❌ Error checking login status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform login
     */
    private static boolean performLogin(Page page, String username, String password) {
        try {
            System.out.println("\n==================================================");
            System.out.println("🔐 Starting 1xBet login process...");
            System.out.println("==================================================");

            if (checkIfLoggedIn(page)) {
                System.out.println("✅ Already logged in - skipping login");
                return true;
            }

            // Find login button
            System.out.println("\n📱 Looking for login button...");
            System.out.println("--------------------------------------------------");

            Locator loginButton = null;

            // Strategy 1: By role and name
            try {
                System.out.println("🔍 Strategy 1: Finding by role and name 'Log in'...");
                loginButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in"));

                int count = loginButton.count();
                System.out.println("   Found " + count + " button(s) with role and name 'Log in'");

                if (count > 0 && loginButton.first().isVisible()) {
                    System.out.println("   ✅ Login button found via role+name strategy");
                } else {
                    System.out.println("   ⚠️ Button found but not visible");
                    loginButton = null;
                }
            } catch (Exception e) {
                System.out.println("   ❌ Strategy 1 failed: " + e.getMessage());
                loginButton = null;
            }

            // Strategy 2: By text content
            if (loginButton == null) {
                try {
                    System.out.println("🔍 Strategy 2: Finding by text 'Log in'...");
                    loginButton = page.getByText("Log in", new Page.GetByTextOptions().setExact(true));

                    int count = loginButton.count();
                    System.out.println("   Found " + count + " element(s) with exact text 'Log in'");

                    if (count > 0) {
                        loginButton = loginButton.locator("xpath=ancestor-or-self::button").first();

                        if (loginButton.isVisible()) {
                            System.out.println("   ✅ Login button found via text strategy");
                        } else {
                            System.out.println("   ⚠️ Element found but not a visible button");
                            loginButton = null;
                        }
                    } else {
                        loginButton = null;
                    }
                } catch (Exception e) {
                    System.out.println("   ❌ Strategy 2 failed: " + e.getMessage());
                    loginButton = null;
                }
            }

            // Strategy 3: By class name
            if (loginButton == null) {
                try {
                    System.out.println("🔍 Strategy 3: Finding by class 'auth-dropdown-trigger'...");
                    loginButton = page.locator("button.auth-dropdown-trigger");

                    int count = loginButton.count();
                    System.out.println("   Found " + count + " button(s) with class 'auth-dropdown-trigger'");

                    if (count > 0 && loginButton.first().isVisible()) {
                        loginButton = loginButton.first();
                        System.out.println("   ✅ Login button found via class strategy");
                    } else {
                        System.out.println("   ⚠️ Button found but not visible");
                        loginButton = null;
                    }
                } catch (Exception e) {
                    System.out.println("   ❌ Strategy 3 failed: " + e.getMessage());
                    loginButton = null;
                }
            }

            // Strategy 4: Find all buttons and check text
            if (loginButton == null) {
                try {
                    System.out.println("🔍 Strategy 4: Checking all buttons for 'Log in' text...");
                    Locator allButtons = page.locator("button");

                    int totalButtons = allButtons.count();
                    System.out.println("   Total buttons on page: " + totalButtons);

                    System.out.println("   Checking button texts:");
                    for (int i = 0; i < totalButtons; i++) {
                        Locator button = allButtons.nth(i);
                        String buttonText = button.textContent();

                        if (i < 10) {
                            System.out.println("   Button " + i + ": text='" + (buttonText != null ? buttonText.trim() : "null") + "'");
                        }

                        if (buttonText != null && buttonText.trim().equals("Log in")) {
                            if (button.isVisible()) {
                                loginButton = button;
                                System.out.println("   ✅ Login button found at index " + i + " via all-buttons strategy");
                                break;
                            }
                        }
                    }

                    if (loginButton == null) {
                        System.err.println("   ❌ Login button not found in " + totalButtons + " buttons");

                        try {
                            page.screenshot(new Page.ScreenshotOptions()
                                    .setPath(Paths.get("debug_no_login_button.png"))
                                    .setFullPage(true));
                            System.out.println("   📸 Screenshot saved: debug_no_login_button.png");
                        } catch (Exception ex) {
                            System.out.println("   ⚠️ Could not save screenshot: " + ex.getMessage());
                        }

                        return false;
                    }
                } catch (Exception e) {
                    System.err.println("   ❌ Strategy 4 failed: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }

            // Click login button
            System.out.println("\n📱 Clicking login button...");
            System.out.println("--------------------------------------------------");

            try {
                loginButton.scrollIntoViewIfNeeded();
                sleepRandom(300, 500);

                String buttonClass = loginButton.getAttribute("class");
                System.out.println("   Button class: " + buttonClass);

                loginButton.click();
                System.out.println("   ✅ Login button clicked successfully");

            } catch (Exception e) {
                System.err.println("   ❌ Failed to click login button: " + e.getMessage());
                e.printStackTrace();
                return false;
            }

            System.out.println("   ⏳ Waiting for dropdown to appear...");
            sleepRandom(2500, 3500);

            // Find username field
            System.out.println("\n📝 Looking for username field...");
            System.out.println("--------------------------------------------------");

            Locator usernameField = null;

            String[] usernameSelectors = {
                    "input#username",
                    "input[placeholder*='mail' i]",
                    "input[placeholder*='ID' i]",
                    "input[type='text'][id*='username' i]",
                    "input[type='text']"
            };

            for (String selector : usernameSelectors) {
                try {
                    System.out.println("   🔍 Trying username selector: " + selector);
                    Locator field = page.locator(selector).first();

                    if (field.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        usernameField = field;
                        System.out.println("   ✅ Username field found with selector: " + selector);
                        break;
                    } else {
                        System.out.println("   ⚠️ Field found but not visible");
                    }
                } catch (Exception e) {
                    System.out.println("   ⚠️ Selector '" + selector + "' not found or not visible");
                }
            }

            if (usernameField == null) {
                System.err.println("   ❌ Username field not found");

                try {
                    Locator allInputs = page.locator("input");
                    int inputCount = allInputs.count();
                    System.out.println("   📊 Total inputs on page: " + inputCount);

                    System.out.println("   Input details:");
                    for (int i = 0; i < Math.min(inputCount, 10); i++) {
                        Locator input = allInputs.nth(i);
                        String id = input.getAttribute("id");
                        String placeholder = input.getAttribute("placeholder");
                        String type = input.getAttribute("type");
                        boolean visible = input.isVisible();

                        System.out.println("   Input " + i + ": id='" + id + "', placeholder='" + placeholder +
                                "', type='" + type + "', visible=" + visible);
                    }
                } catch (Exception e) {
                    System.err.println("   ❌ Could not debug inputs: " + e.getMessage());
                }

                return false;
            }

            // Type username
            System.out.println("\n✍️ Typing username...");
            try {
                usernameField.scrollIntoViewIfNeeded();
                usernameField.click();
                usernameField.fill(username);
                System.out.println("   ✅ Username entered: " + username);
            } catch (Exception e) {
                System.err.println("   ❌ Failed to type username: " + e.getMessage());
                e.printStackTrace();
                return false;
            }

            sleepRandom(600, 1200);

            // Find password field
            System.out.println("\n🔑 Looking for password field...");
            System.out.println("--------------------------------------------------");

            Locator passwordField = null;

            String[] passwordSelectors = {
                    "input#username-password",
                    "input[type='password']",
                    "input[placeholder*='password' i]"
            };

            for (String selector : passwordSelectors) {
                try {
                    System.out.println("   🔍 Trying password selector: " + selector);
                    Locator field = page.locator(selector).first();

                    if (field.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                        passwordField = field;
                        System.out.println("   ✅ Password field found with selector: " + selector);
                        break;
                    } else {
                        System.out.println("   ⚠️ Field found but not visible");
                    }
                } catch (Exception e) {
                    System.out.println("   ⚠️ Selector '" + selector + "' not found or not visible");
                }
            }

            if (passwordField == null) {
                System.err.println("   ❌ Password field not found");
                return false;
            }

            // Type password
            System.out.println("\n✍️ Typing password...");
            try {
                passwordField.scrollIntoViewIfNeeded();
                passwordField.click();
                passwordField.fill(password);
                System.out.println("   ✅ Password entered (hidden for security)");
            } catch (Exception e) {
                System.err.println("   ❌ Failed to type password: " + e.getMessage());
                e.printStackTrace();
                return false;
            }

            sleepRandom(1000, 1500);

            // Find submit button
            System.out.println("\n🚀 Looking for submit button...");
            System.out.println("--------------------------------------------------");

            Locator submitButton = null;

            try {
                System.out.println("   🔍 Looking for button with type='submit'...");
                submitButton = page.locator("button[type='submit']").first();

                if (submitButton.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                    System.out.println("   ✅ Submit button found (type=submit)");
                } else {
                    System.out.println("   ⚠️ Submit button found but not visible");
                    submitButton = null;
                }
            } catch (Exception e) {
                System.out.println("   ⚠️ Submit button with type=submit not found");
                submitButton = null;
            }

            if (submitButton == null) {
                try {
                    System.out.println("   🔍 Looking for buttons inside forms...");
                    Locator formButtons = page.locator("form button");
                    int count = formButtons.count();

                    System.out.println("   Found " + count + " buttons inside forms");

                    for (int i = 0; i < count; i++) {
                        Locator btn = formButtons.nth(i);
                        String text = btn.textContent();

                        System.out.println("   Form button " + i + ": text='" + (text != null ? text.trim() : "null") + "'");

                        if (text != null && (text.trim().equals("Log in") || text.trim().equals("LOGIN"))) {
                            if (btn.isVisible()) {
                                submitButton = btn;
                                System.out.println("   ✅ Submit button found (text='" + text.trim() + "') at index " + i);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("   ⚠️ Could not find submit button in forms: " + e.getMessage());
                }
            }

            if (submitButton == null) {
                System.err.println("   ❌ Submit button not found");
                return false;
            }

            // Click submit
            System.out.println("\n🚀 Clicking submit button...");
            try {
                submitButton.scrollIntoViewIfNeeded();
                sleepRandom(300, 500);
                submitButton.click();
                System.out.println("   ✅ Submit button clicked");
            } catch (Exception e) {
                System.err.println("   ❌ Failed to click submit: " + e.getMessage());
                e.printStackTrace();
                return false;
            }

            System.out.println("\n⏳ Waiting for login to process...");
            sleepRandom(5000, 7000);

            // Verify login
            System.out.println("\n🔍 Verifying login status...");
            System.out.println("--------------------------------------------------");
            boolean loggedIn = checkIfLoggedIn(page);

            if (loggedIn) {
                System.out.println("\n==================================================");
                System.out.println("✅✅✅ LOGIN SUCCESSFUL! ✅✅✅");
                System.out.println("==================================================\n");
                return true;
            } else {
                System.err.println("\n==================================================");
                System.err.println("❌ Login failed - still showing login button");
                System.err.println("==================================================\n");

                try {
                    Locator errorMessages = page.locator(".error, .alert, [class*='error']");
                    int errorCount = errorMessages.count();

                    if (errorCount > 0) {
                        System.out.println("⚠️ Found " + errorCount + " error message(s):");
                        for (int i = 0; i < errorCount; i++) {
                            Locator error = errorMessages.nth(i);
                            if (error.isVisible()) {
                                String errorText = error.textContent();
                                System.err.println("   Error " + i + ": " + errorText);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Could not check for errors: " + e.getMessage());
                }

                return false;
            }

        } catch (Exception e) {
            System.err.println("\n❌ Exception during login: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Take screenshot
     */
    private static void takeScreenshot(Page page, String filename) {
        try {
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get("screenshots", filename))
                    .setFullPage(true)
            );
            System.out.println("📸 Screenshot saved: screenshots/" + filename);
        } catch (Exception e) {
            System.out.println("⚠️ Failed to take screenshot: " + e.getMessage());
        }
    }

    /**
     * Cleanup resources
     */
    private static void cleanup(Page page, BrowserContext context, Browser browser, Playwright playwright) {
        System.out.println("\n🧹 Cleaning up resources...");

        try {
            if (page != null) page.close();
        } catch (Exception e) {
            System.out.println("⚠️ Failed to close page: " + e.getMessage());
        }

        try {
            if (context != null) context.close();
        } catch (Exception e) {
            System.out.println("⚠️ Failed to close context: " + e.getMessage());
        }

        try {
            if (browser != null) browser.close();
        } catch (Exception e) {
            System.out.println("⚠️ Failed to close browser: " + e.getMessage());
        }

        try {
            if (playwright != null) playwright.close();
        } catch (Exception e) {
            System.out.println("⚠️ Failed to close playwright: " + e.getMessage());
        }

        System.out.println("✅ Cleanup complete\n");
    }

    // Helper methods
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
}