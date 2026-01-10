//package com.mouse.bet.window;
//
//import com.microsoft.playwright.*;
//import com.mouse.bet.config.WindowConfig;
//import com.mouse.bet.exception.PageHealthException;
//import com.mouse.bet.interfaces.BettingTask;
//import com.mouse.bet.interfaces.BettingWindow;
//import com.mouse.bet.manager.ProfileManager;
//import com.mouse.bet.monitor.PageHealthMonitor;
//import com.mouse.bet.profile.UserAgentProfile;
//import jakarta.annotation.PostConstruct;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//@Slf4j
//@RequiredArgsConstructor
//@Component
//public class SportyBet implements BettingWindow, Runnable {
//
//    private static final String EMOJI_INIT = "🚀";
//    private static final String EMOJI_LOGIN = "🔐";
//    private static final String EMOJI_BET = "🎯";
//    private static final String EMOJI_SUCCESS = "✅";
//    private static final String EMOJI_ERROR = "❌";
//    private static final String EMOJI_WARNING = "⚠️";
//    private static final String EMOJI_SYNC = "🔄";
//    private static final String EMOJI_POLL = "📊";
//    private static final String EMOJI_HEALTH = "💚";
//    private static final String EMOJI_SHUTDOWN = "🛑";
//    private static final String EMOJI_START = "▶️";
//    private static final String EMOJI_SEARCH = "🔍";
//    private static final String EMOJI_INFO = "ℹ️";
//    private static final String EMOJI_TRASH = "🗑️";
//    private static final String EMOJI_TARGET = "🎯";
//    private static final String EMOJI_ROCKET = "🚀";
//    private static final String EMOJI_NAVIGATION = "🧭";
//    private static final String EMOJI_CLOCK = "⏰";
//    private static final String EMOJI_GAME = "🎮";
//    private static final String EMOJI_MARKET = "📈";
//    private static final String EMOJI_CART = "🛒";
//    private static final String EMOJI_MONEY = "💰";
//
//    private Playwright playwright;
//    private Browser browser;
//    private BrowserContext currentContext;
//    private UserAgentProfile profile;
//    private final WindowConfig windowConfig;
//    private static final String CONTEXT_FILE = "sporty-context.json";
//    private PageHealthMonitor healthMonitor;
//    private ProfileManager profileManager;
//
//    private final AtomicBoolean isRunning = new AtomicBoolean(false);
//    private final AtomicBoolean isWindowUpAndRunning = new AtomicBoolean(false);
//    private final AtomicBoolean isPaused = new AtomicBoolean(false);
//    private final AtomicBoolean isLoggedIn = new AtomicBoolean(false);
//
//    @Value("${sporty.username:}")
//    private String sportyUsername;
//
//    @Value("${sporty.password:}")
//    private String sportyPassword;
//
//    @Value("${sporty.context.path:./playwright-context}")
//    private String contextPath;
//
//    @Value("${sporty.max.retry.attempts:3}")
//    private int maxRetryAttempts;
//
//    @Value("${sporty.poll.interval.ms:2000}")
//    private long pollIntervalMs;
//
//    @Value("${bet.timeout.seconds:30}")
//    private int betTimeoutSeconds;
//
//    @Value("${partner.timeout.seconds:10}")
//    private int partnerTimeout;
//
//    @Value("${deploy.timeout.seconds:3}")
//    private int deployTimeout;
//
//    @Value("${fetch.enabled.football:false}")
//    private boolean fetchFootballEnabled;
//
//    @Value("${fetch.enabled.basketball:false}")
//    private boolean fetchBasketballEnabled;
//
//    @Value("${fetch.enabled.table-tennis:true}")
//    private boolean fetchTableTennisEnabled;
//
//    @Value("${onewin.base.url:https://1win.com}")
//    private String baseUrl;
//
//    @Value("${onewin.login.url:https://1win.com/login}")
//    private String loginUrl;
//
//    @Value("${onewin.live.events.url:https://1win.com/live}")
//    private String liveEventsUrl;
//
//    /**
//     * Initialize Playwright and browser
//     */
//    @PostConstruct
//    public void init() {
//        log.info("{} {} Initializing OneWin with Playwright...", EMOJI_INIT, EMOJI_BET);
//        try {
//            playwright = Playwright.create();
//            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
//                    .setHeadless(true)
//                    .setArgs(windowConfig.getBROWSER_FlAGS())
//                    .setSlowMo(0));
//
//            log.info("{} {} Playwright initialized successfully", EMOJI_SUCCESS, EMOJI_INIT);
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to initialize Playwright: {}", EMOJI_ERROR, EMOJI_INIT, e.getMessage(), e);
//            throw new RuntimeException("Playwright initialization failed", e);
//        }
//    }
//
//    // ========================================================================
//    // INITIAL NAVIGATION METHODS (Empty implementations)
//    // ========================================================================
//
//    /**
//     * Navigate to the bookmaker's homepage
//     * @param page The Playwright page instance
//     * @throws Exception if navigation fails
//     */
//    private void navigateToBookmaker(Page page) throws Exception {
//        log.info("{} {} Navigating to bookmaker homepage: {}", EMOJI_NAVIGATION, EMOJI_START, baseUrl);
//        // TODO: Implementation to navigate to bookmaker homepage
//    }
//
//    /**
//     * Perform login to the bookmaker site
//     * @param page The Playwright page instance
//     * @throws Exception if login fails
//     */
//    private void performLogin(Page page) throws Exception {
//        log.info("{} {} Attempting to login with username: {}", EMOJI_LOGIN, EMOJI_TARGET, sportyUsername);
//        // TODO: Implementation to perform login
//    }
//
//    /**
//     * Check if user is currently logged in
//     * @param page The Playwright page instance
//     * @return true if logged in, false otherwise
//     * @throws Exception if check fails
//     */
//    private boolean checkIfLoggedIn(Page page) throws Exception {
//        log.info("{} {} Checking login status...", EMOJI_SEARCH, EMOJI_LOGIN);
//        // TODO: Implementation to verify login status
//        return false;
//    }
//
//    /**
//     * Navigate to live events page
//     * @param page The Playwright page instance
//     * @throws Exception if navigation fails
//     */
//    private void navigateToLiveEvents(Page page) throws Exception {
//        log.info("{} {} Navigating to live events page: {}", EMOJI_NAVIGATION, EMOJI_BET, liveEventsUrl);
//        // TODO: Implementation to navigate to live events
//    }
//
//    /**
//     * Navigate to a specific sport page based on configuration
//     * @param page The Playwright page instance
//     * @param sportType The type of sport (FOOTBALL, BASKETBALL, TABLE_TENNIS)
//     * @throws Exception if navigation fails
//     */
//    private void navigateToSportPage(Page page, OneWin.SportType sportType) throws Exception {
//        log.info("{} {} Navigating to sport page: {}", EMOJI_NAVIGATION, EMOJI_SEARCH, sportType);
//        // TODO: Implementation to navigate to specific sport page
//    }
//
//    // ========================================================================
//    // BETTING TASK PROCESSING METHODS (Empty implementations)
//    // ========================================================================
//
//    /**
//     * Poll for a betting task from the dispatcher
//     * This method waits for and retrieves the next betting task to process
//     *
//     * @return BettingTask object containing game info, market, outcome details, or null if no task available
//     * @throws Exception if polling fails
//     */
//    private BettingTask pollTaskFromDispatcher() throws Exception {
//        log.info("{} {} Polling for betting task from dispatcher...", EMOJI_POLL, EMOJI_SEARCH);
//        // TODO: Implementation to poll task from dispatcher
//        // This should communicate with your dispatcher service to get the next betting task
//        return null;
//    }
//
//    /**
//     * Navigate to a specific game from the task
//     * Uses game identifiers from the betting task to locate and navigate to the game
//     *
//     * @param page The Playwright page instance
//     * @param task The betting task containing game information
//     * @throws Exception if game navigation fails
//     */
//    private void navigateToGame(Page page, BettingTask task) throws Exception {
//        log.info("{} {} Navigating to game: {} vs {}",
//                EMOJI_GAME, EMOJI_NAVIGATION, task.getHomeTeam(), task.getAwayTeam());
//        // TODO: Implementation to navigate to specific game
//        // Should locate the game on the page using selectors and click to open game detail
//    }
//
//    /**
//     * Find and locate the specified market within the game
//     * Markets could be: Match Winner, Over/Under, Handicap, etc.
//     *
//     * @param page The Playwright page instance
//     * @param task The betting task containing market information
//     * @return true if market is found and visible, false otherwise
//     * @throws Exception if market search fails
//     */
//    private boolean findMarket(Page page, BettingTask task) throws Exception {
//        log.info("{} {} Searching for market: {}",
//                EMOJI_MARKET, EMOJI_SEARCH, task.getMarketType());
//        // TODO: Implementation to find specific market
//        // Should scroll/navigate through markets and verify the target market exists
//        return false;
//    }
//
//    /**
//     * Locate and click the specific outcome within the market
//     * Outcomes could be: Home Win, Draw, Away Win, Over, Under, specific scores, etc.
//     *
//     * @param page The Playwright page instance
//     * @param task The betting task containing outcome information
//     * @throws Exception if outcome selection fails
//     */
//    private void selectOutcome(Page page, BettingTask task) throws Exception {
//        log.info("{} {} Selecting outcome: {} with odds: {}",
//                EMOJI_TARGET, EMOJI_BET, task.getOutcome(), task.getExpectedOdds());
//        // TODO: Implementation to click specific outcome
//        // Should verify odds match expected range before clicking
//    }
//
//    /**
//     * Verify that the selected game/bet has been added to the betslip
//     * Checks betslip contents, odds, and stake amount
//     *
//     * @param page The Playwright page instance
//     * @param task The betting task to verify against betslip
//     * @return true if bet is correctly added to betslip, false otherwise
//     * @throws Exception if verification fails
//     */
//    private boolean verifyBetslip(Page page, BettingTask task) throws Exception {
//        log.info("{} {} Verifying bet added to betslip...", EMOJI_CART, EMOJI_SEARCH);
//        // TODO: Implementation to verify betslip
//        // Should check that the correct game, market, outcome, and odds are in betslip
//        return false;
//    }
//
//    /**
//     * Place the bet from the betslip
//     * Enters stake amount, confirms bet details, and submits the bet
//     *
//     * @param page The Playwright page instance
//     * @param task The betting task containing stake and bet details
//     * @return true if bet is successfully placed, false otherwise
//     * @throws Exception if bet placement fails
//     */
//    private boolean placeBet(Page page, BettingTask task) throws Exception {
//        log.info("{} {} Placing bet with stake: {}",
//                EMOJI_MONEY, EMOJI_BET, task.getStakeAmount());
//        // TODO: Implementation to place bet
//        // Should enter stake, verify final odds, click place bet button, and confirm success
//        return false;
//    }
//
//    /**
//     * Return to the live sport page after bet placement
//     * Navigates back to the main sport listing to continue polling for new tasks
//     *
//     * @param page The Playwright page instance
//     * @param sportType The sport type to return to
//     * @throws Exception if navigation back fails
//     */
//    private void returnToSportPage(Page page, OneWin.SportType sportType) throws Exception {
//        log.info("{} {} Returning to {} live page...",
//                EMOJI_NAVIGATION, EMOJI_SYNC, sportType);
//        // TODO: Implementation to return to sport page
//        // Should navigate back to the sport listing page ready for next task
//    }
//
//    /**
//     * Clear the betslip after bet placement or on error
//     * Removes all selections from betslip to prepare for next bet
//     *
//     * @param page The Playwright page instance
//     * @throws Exception if betslip clearing fails
//     */
//    private void clearBetslip(Page page) throws Exception {
//        log.info("{} {} Clearing betslip...", EMOJI_TRASH, EMOJI_CART);
//        // TODO: Implementation to clear betslip
//        // Should remove all selections and reset betslip state
//    }
//
//    /**
//     * Handle bet placement failure
//     * Logs error, clears betslip, and prepares for next task
//     *
//     * @param page The Playwright page instance
//     * @param task The failed betting task
//     * @param error The exception that caused the failure
//     */
//    private void handleBetFailure(Page page, BettingTask task, Exception error) {
//        log.error("{} {} Bet placement failed for task {}: {}",
//                EMOJI_ERROR, EMOJI_BET, task.getTaskId(), error.getMessage());
//
//        try {
//            clearBetslip(page);
//        } catch (Exception e) {
//            log.warn("{} {} Failed to clear betslip after error: {}",
//                    EMOJI_WARNING, EMOJI_CART, e.getMessage());
//        }
//
//        // TODO: Notify dispatcher of failure
//        // TODO: Update task status to failed
//    }
//
//    /**
//     * Handle successful bet placement
//     * Logs success and notifies dispatcher
//     *
//     * @param task The successfully placed betting task
//     */
//    private void handleBetSuccess(BettingTask task) {
//        log.info("{} {} Bet successfully placed for task {}",
//                EMOJI_SUCCESS, EMOJI_MONEY, task.getTaskId());
//        // TODO: Notify dispatcher of success
//        // TODO: Update task status to completed
//    }
//
//    // ========================================================================
//    // SUPPORTING METHODS
//    // ========================================================================
//
//    /**
//     * Wait for page to be fully loaded and ready
//     * @param page The Playwright page instance
//     * @throws Exception if page doesn't load properly
//     */
//    private void waitForPageReady(Page page) throws Exception {
//        log.info("{} {} Waiting for page to be ready...", EMOJI_CLOCK, EMOJI_HEALTH);
//        // TODO: Implementation to wait for page readiness
//    }
//
//    /**
//     * Determine which sport to navigate to based on configuration
//     * @return The configured sport type
//     */
//    private OneWin.SportType determineConfiguredSport() {
//        log.info("{} {} Determining configured sport from settings...", EMOJI_INFO, EMOJI_SEARCH);
//
//        if (fetchTableTennisEnabled) {
//            log.info("{} Table Tennis is enabled", EMOJI_TARGET);
//            return OneWin.SportType.TABLE_TENNIS;
//        } else if (fetchFootballEnabled) {
//            log.info("{} Football is enabled", EMOJI_TARGET);
//            return OneWin.SportType.FOOTBALL;
//        } else if (fetchBasketballEnabled) {
//            log.info("{} Basketball is enabled", EMOJI_TARGET);
//            return OneWin.SportType.BASKETBALL;
//        }
//
//        log.warn("{} {} No sport enabled in configuration, defaulting to TABLE_TENNIS",
//                EMOJI_WARNING, EMOJI_INFO);
//        return OneWin.SportType.TABLE_TENNIS;
//    }
//
//    /**
//     * Handle captcha scenario if detected
//     */
//    private void handleCaptchaScenario() {
//        log.warn("{} {} CAPTCHA detected - implementing recovery strategy", EMOJI_WARNING, EMOJI_SYNC);
//
//        try {
//            Thread.sleep(5000);
//            recreateContext();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            log.error("{} {} Interrupted during captcha handling", EMOJI_ERROR, EMOJI_WARNING);
//        }
//    }
//
//    /**
//     * Wait between retry attempts with exponential backoff
//     * @param attempt Current attempt number
//     */
//    private void waitBetweenRetries(int attempt) {
//        long waitTime = Math.min(2000L * attempt, 10000L);
//        log.info("{} {} Waiting {}ms before retry attempt {}...",
//                EMOJI_CLOCK, EMOJI_SYNC, waitTime, attempt + 1);
//
//        try {
//            Thread.sleep(waitTime);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            log.warn("{} {} Wait interrupted", EMOJI_WARNING, EMOJI_CLOCK);
//        }
//    }
//
//    // ========================================================================
//    // CONTEXT MANAGEMENT
//    // ========================================================================
//
//    /**
//     * Create new browser context
//     */
//    private BrowserContext newContext(Browser browser, UserAgentProfile profile) {
//        log.info("ViewPort size {} x {}", profile.getViewport().getWidth(), profile.getViewport().getHeight());
//        log.info("Headers: {}", getAllHeaders(profile));
//
//        return browser.newContext(new Browser.NewContextOptions()
//                .setUserAgent(profile.getUserAgent())
//                .setLocale("en-US")
//                .setViewportSize(profile.getViewport().getWidth(), profile.getViewport().getHeight())
//        );
//    }
//
//    /**
//     * Get all headers from profile
//     */
//    private Map<String, String> getAllHeaders(UserAgentProfile profile) {
//        Map<String, String> all = new HashMap<>();
//        if (profile.getHeaders().getStandardHeaders() != null) {
//            all.putAll(profile.getHeaders().getStandardHeaders());
//        }
//        if (profile.getHeaders().getClientHintsHeaders() != null) {
//            all.putAll(profile.getHeaders().getClientHintsHeaders());
//        }
//        return all;
//    }
//
//    /**
//     * Load or create browser context
//     */
//    private BrowserContext loadOrCreateContext() {
//        profile = profileManager.getNextProfile();
//        Path contextFilePath = Paths.get(contextPath, CONTEXT_FILE);
//
//        if (Files.exists(contextFilePath)) {
//            try {
//                log.info("Loading existing browser context from: {}", contextFilePath);
//
//                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
//                        .setUserAgent(profile.getUserAgent())
//                        .setLocale("en-US")
//                        .setStorageStatePath(contextFilePath));
//
//                if (context != null) {
//                    log.info("Existing context loaded successfully");
//                    return context;
//                }
//
//            } catch (Exception e) {
//                log.warn("Failed to load existing context: {}", e.getMessage());
//
//                try {
//                    Files.deleteIfExists(contextFilePath);
//                    log.info("Deleted corrupted context file");
//                } catch (Exception deleteEx) {
//                    log.warn("Could not delete context file: {}", deleteEx.getMessage());
//                }
//            }
//        }
//
//        log.info("Creating new browser context");
//        return newContext(browser, profile);
//    }
//
//    /**
//     * Save browser context
//     */
//    private void saveContext(BrowserContext context) {
//        if (context == null) return;
//
//        try {
//            Path contextDir = Paths.get(contextPath);
//            if (!Files.exists(contextDir)) {
//                Files.createDirectories(contextDir);
//            }
//
//            Path contextFilePath = contextDir.resolve(CONTEXT_FILE);
//            context.storageState(new BrowserContext.StorageStateOptions()
//                    .setPath(contextFilePath));
//            log.info("Browser context saved to: {}", contextFilePath);
//        } catch (Exception e) {
//            log.error("Failed to save context: {}", e.getMessage(), e);
//        }
//    }
//
//    /**
//     * Recreate browser context
//     * Proper cleanup of all resources before recreating
//     */
//    private void recreateContext() {
//        log.info("{} {} Recreating browser context...", EMOJI_SYNC, EMOJI_INIT);
//        isLoggedIn.set(false);
//
//        if (healthMonitor != null) {
//            try {
//                healthMonitor.stop();
//            } catch (Exception e) {
//                log.warn("Error stopping health monitor: {}", e.getMessage());
//            }
//            healthMonitor = null;
//        }
//
//        if (currentContext != null) {
//            try {
//                for (Page page : currentContext.pages()) {
//                    try {
//                        if (!page.isClosed()) {
//                            page.close();
//                        }
//                    } catch (Exception e) {
//                        log.debug("Error closing page: {}", e.getMessage());
//                    }
//                }
//
//                currentContext.close();
//                log.info("Old context closed successfully");
//
//            } catch (Exception e) {
//                log.warn("Error closing context: {}", e.getMessage());
//            } finally {
//                currentContext = null;
//            }
//        }
//
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    // ========================================================================
//    // MAIN ENTRY POINTS
//    // ========================================================================
//
//    /**
//     * Main entry point - runs the betting window with retry logic
//     */
//    @Override
//    public void run() {
//        int attempt = 0;
//        Exception lastException = null;
//
//        while (attempt < maxRetryAttempts) {
//            attempt++;
//            log.info("{} {} Starting OneWin attempt {}/{}",
//                    EMOJI_INIT, EMOJI_BET, attempt, maxRetryAttempts);
//
//            try {
//                windowEntry();
//                log.info("{} {} OneWin completed successfully", EMOJI_SUCCESS, EMOJI_BET);
//                break;
//
//            } catch (OneWin.CaptchaDetectedException e) {
//                log.error("{} {} CAPTCHA detected on attempt {}: {}",
//                        EMOJI_ERROR, EMOJI_WARNING, attempt, e.getMessage());
//                lastException = e;
//                handleCaptchaScenario();
//
//            } catch (PageHealthException e) {
//                log.error("{} {} Page health check failed on attempt {}: {}",
//                        EMOJI_ERROR, EMOJI_HEALTH, attempt, e.getMessage());
//                lastException = e;
//                if (attempt < maxRetryAttempts) {
//                    recreateContext();
//                    waitBetweenRetries(attempt);
//                }
//
//            } catch (PlaywrightException e) {
//                String msg = e.getMessage();
//
//                if (msg.contains("Object doesn't exist") ||
//                        msg.contains("frame was detached") ||
//                        msg.contains("ERR_ABORTED") ||
//                        msg.contains("Target closed")) {
//
//                    log.error("{} {} Playwright object lifecycle error on attempt {}: {}",
//                            EMOJI_ERROR, EMOJI_WARNING, attempt, msg);
//                    lastException = e;
//
//                    if (attempt < maxRetryAttempts) {
//                        recreateContext();
//                        waitBetweenRetries(attempt);
//                    }
//                } else {
//                    log.error("{} {} Unexpected Playwright error: {}",
//                            EMOJI_ERROR, EMOJI_BET, msg, e);
//                    lastException = e;
//                    if (attempt < maxRetryAttempts) {
//                        recreateContext();
//                        waitBetweenRetries(attempt);
//                    }
//                }
//
//            } catch (Exception e) {
//                log.error("{} {} Unexpected error on attempt {}: {}",
//                        EMOJI_ERROR, EMOJI_WARNING, attempt, e.getMessage(), e);
//                lastException = e;
//                if (attempt < maxRetryAttempts) {
//                    recreateContext();
//                    waitBetweenRetries(attempt);
//                }
//            }
//        }
//
//        if (lastException != null) {
//            String errorMsg = String.format("Failed after %d attempts. Last error: %s",
//                    maxRetryAttempts, lastException.getMessage());
//            log.error("{} {} All retry attempts exhausted for profile {}: {}",
//                    EMOJI_ERROR, EMOJI_BET, profile != null ? profile.getId() : "unknown", errorMsg);
//        }
//    }
//
//    /**
//     * Main window entry method - orchestrates the complete betting flow
//     *
//     * Flow:
//     * 1. Initial setup: Navigate to bookmaker, login, go to sport page
//     * 2. Continuous loop: Poll tasks -> Navigate to game -> Place bet -> Return to sport page
//     * 3. Loop continues until window is stopped or max retries reached
//     */
//    private void windowEntry() throws Exception {
//        Page page = null;
//
//        try {
//            log.info("{} {} Starting window entry process...", EMOJI_START, EMOJI_ROCKET);
//            isRunning.set(true);
//
//            // ===== PHASE 1: INITIAL SETUP =====
//
//            // Load or create context
//            if (currentContext == null) {
//                currentContext = loadOrCreateContext();
//            }
//
//            if (currentContext == null) {
//                throw new RuntimeException("Failed to create valid browser context");
//            }
//
//            // Close any existing pages to prevent multiple pages
//            for (Page existingPage : currentContext.pages()) {
//                if (!existingPage.isClosed()) {
//                    log.warn("Closing existing page: {}", existingPage.url());
//                    try {
//                        existingPage.close();
//                    } catch (Exception e) {
//                        log.debug("Error closing existing page: {}", e.getMessage());
//                    }
//                }
//            }
//
//            // Create new page
//            page = currentContext.newPage();
//            log.info("{} {} New page created successfully", EMOJI_SUCCESS, EMOJI_INIT);
//
//            // Step 1: Navigate to bookmaker
//            try {
//                navigateToBookmaker(page);
//                waitForPageReady(page);
//                log.info("{} {} Navigation to bookmaker completed", EMOJI_SUCCESS, EMOJI_NAVIGATION);
//            } catch (Exception e) {
//                log.error("{} {} Failed to navigate to bookmaker: {}",
//                        EMOJI_ERROR, EMOJI_NAVIGATION, e.getMessage());
//                throw new RuntimeException("Bookmaker navigation failed", e);
//            }
//
//            // Step 2: Check login status and login if needed
//            try {
//                boolean loggedIn = checkIfLoggedIn(page);
//
//                if (!loggedIn) {
//                    log.info("{} {} User not logged in, attempting login...",
//                            EMOJI_INFO, EMOJI_LOGIN);
//                    performLogin(page);
//                    waitForPageReady(page);
//
//                    // Verify login was successful
//                    if (checkIfLoggedIn(page)) {
//                        isLoggedIn.set(true);
//                        log.info("{} {} Login successful", EMOJI_SUCCESS, EMOJI_LOGIN);
//                    } else {
//                        throw new RuntimeException("Login verification failed");
//                    }
//                } else {
//                    isLoggedIn.set(true);
//                    log.info("{} {} User already logged in", EMOJI_SUCCESS, EMOJI_LOGIN);
//                }
//            } catch (Exception e) {
//                log.error("{} {} Login process failed: {}",
//                        EMOJI_ERROR, EMOJI_LOGIN, e.getMessage());
//                throw new RuntimeException("Login failed", e);
//            }
//
//            // Step 3: Navigate to live events
//            try {
//                navigateToLiveEvents(page);
//                waitForPageReady(page);
//                log.info("{} {} Navigation to live events completed",
//                        EMOJI_SUCCESS, EMOJI_BET);
//            } catch (Exception e) {
//                log.error("{} {} Failed to navigate to live events: {}",
//                        EMOJI_ERROR, EMOJI_BET, e.getMessage());
//                throw new RuntimeException("Live events navigation failed", e);
//            }
//
//            // Step 4: Navigate to configured sport page
//            OneWin.SportType configuredSport = determineConfiguredSport();
//            try {
//                navigateToSportPage(page, configuredSport);
//                waitForPageReady(page);
//                log.info("{} {} Navigation to {} page completed",
//                        EMOJI_SUCCESS, EMOJI_TARGET, configuredSport);
//            } catch (Exception e) {
//                log.error("{} {} Failed to navigate to sport page: {}",
//                        EMOJI_ERROR, EMOJI_TARGET, e.getMessage());
//                throw new RuntimeException("Sport page navigation failed", e);
//            }
//
//            // Mark window as up and running
//            isWindowUpAndRunning.set(true);
//            log.info("{} {} Window is now up and running - entering betting loop",
//                    EMOJI_SUCCESS, EMOJI_ROCKET);
//
//            // ===== PHASE 2: CONTINUOUS BETTING LOOP =====
//
//            int consecutiveFailures = 0;
//            int maxConsecutiveFailures = 5;
//
//            while (isRunning.get() && !isPaused.get()) {
//                BettingTask task = null;
//
//                try {
//                    // Poll for next task from dispatcher
//                    log.info("{} {} Polling for new betting task...", EMOJI_POLL, EMOJI_CLOCK);
//                    task = pollTaskFromDispatcher();
//
//                    // If no task available, wait and continue
//                    if (task == null) {
//                        log.debug("{} No task available, waiting {}ms...",
//                                EMOJI_CLOCK, pollIntervalMs);
//                        Thread.sleep(pollIntervalMs);
//                        continue;
//                    }
//
//                    log.info("{} {} Received betting task: {}",
//                            EMOJI_SUCCESS, EMOJI_POLL, task.getTaskId());
//
//                    // Navigate to the specific game
//                    try {
//                        navigateToGame(page, task);
//                        waitForPageReady(page);
//                        log.info("{} {} Game navigation successful", EMOJI_SUCCESS, EMOJI_GAME);
//                    } catch (Exception e) {
//                        log.error("{} {} Failed to navigate to game: {}",
//                                EMOJI_ERROR, EMOJI_GAME, e.getMessage());
//                        handleBetFailure(page, task, e);
//                        returnToSportPage(page, configuredSport);
//                        consecutiveFailures++;
//                        continue;
//                    }
//
//                    // Find the market
//                    try {
//                        boolean marketFound = findMarket(page, task);
//                        if (!marketFound) {
//                            log.warn("{} {} Market not found: {}",
//                                    EMOJI_WARNING, EMOJI_MARKET, task.getMarketType());
//                            handleBetFailure(page, task,
//                                    new Exception("Market not found: " + task.getMarketType()));
//                            returnToSportPage(page, configuredSport);
//                            consecutiveFailures++;
//                            continue;
//                        }
//                        log.info("{} {} Market found successfully", EMOJI_SUCCESS, EMOJI_MARKET);
//                    } catch (Exception e) {
//                        log.error("{} {} Failed to find market: {}",
//                                EMOJI_ERROR, EMOJI_MARKET, e.getMessage());
//                        handleBetFailure(page, task, e);
//                        returnToSportPage(page, configuredSport);
//                        consecutiveFailures++;
//                        continue;
//                    }
//
//                    // Select the outcome
//                    try {
//                        selectOutcome(page, task);
//                        waitForPageReady(page);
//                        log.info("{} {} Outcome selected successfully", EMOJI_SUCCESS, EMOJI_TARGET);
//                    } catch (Exception e) {
//                        log.error("{} {} Failed to select outcome: {}",
//                                EMOJI_ERROR, EMOJI_TARGET, e.getMessage());
//                        handleBetFailure(page, task, e);
//                        returnToSportPage(page, configuredSport);
//                        consecutiveFailures++;
//                        continue;
//                    }
//
//                    // Verify betslip
//                    try {
//                        boolean betslipValid = verifyBetslip(page, task);
//                        if (!betslipValid) {
//                            log.warn("{} {} Betslip verification failed",
//                                    EMOJI_WARNING, EMOJI_CART);
//                            clearBetslip(page);
//                            handleBetFailure(page, task,
//                                    new Exception("Betslip verification failed"));
//                            returnToSportPage(page, configuredSport);
//                            consecutiveFailures++;
//                            continue;
//                        }
//                        log.info("{} {} Betslip verified successfully", EMOJI_SUCCESS, EMOJI_CART);
//                    } catch (Exception e) {
//                        log.error("{} {} Failed to verify betslip: {}",
//                                EMOJI_ERROR, EMOJI_CART, e.getMessage());
//                        clearBetslip(page);
//                        handleBetFailure(page, task, e);
//                        returnToSportPage(page, configuredSport);
//                        consecutiveFailures++;
//                        continue;
//                    }
//
//                    // Place the bet
//                    try {
//                        boolean betPlaced = placeBet(page, task);
//                        if (!betPlaced) {
//                            log.warn("{} {} Bet placement failed",
//                                    EMOJI_WARNING, EMOJI_MONEY);
//                            clearBetslip(page);
//                            handleBetFailure(page, task,
//                                    new Exception("Bet placement failed"));
//                            returnToSportPage(page, configuredSport);
//                            consecutiveFailures++;
//                            continue;
//                        }
//
//                        log.info("{} {} Bet placed successfully for task: {}",
//                                EMOJI_SUCCESS, EMOJI_MONEY, task.getTaskId());
//                        handleBetSuccess(task);
//                        consecutiveFailures = 0; // Reset failure counter on success
//
//                    } catch (Exception e) {
//                        log.error("{} {} Failed to place bet: {}",
//                                EMOJI_ERROR, EMOJI_MONEY, e.getMessage());
//                        clearBetslip(page);
//                        handleBetFailure(page, task, e);
//                        returnToSportPage(page, configuredSport);
//                        consecutiveFailures++;
//                        continue;
//                    }
//
//                    // Return to sport page for next task
//                    try {
//                        returnToSportPage(page, configuredSport);
//                        waitForPageReady(page);
//                        log.info("{} {} Returned to sport page, ready for next task",
//                                EMOJI_SUCCESS, EMOJI_NAVIGATION);
//                    } catch (Exception e) {
//                        log.error("{} {} Failed to return to sport page: {}",
//                                EMOJI_ERROR, EMOJI_NAVIGATION, e.getMessage());
//                        throw new RuntimeException("Failed to return to sport page", e);
//                    }
//
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    log.warn("{} {} Betting loop interrupted", EMOJI_WARNING, EMOJI_SHUTDOWN);
//                    break;
//
//                } catch (Exception e) {
//                    log.error("{} {} Unexpected error in betting loop: {}",
//                            EMOJI_ERROR, EMOJI_WARNING, e.getMessage(), e);
//
//                    if (task != null) {
//                        handleBetFailure(page, task, e);
//                    }
//
//                    consecutiveFailures++;
//
//                    // If too many consecutive failures, try to recover
//                    if (consecutiveFailures >= maxConsecutiveFailures) {
//                        log.error("{} {} Too many consecutive failures ({}), attempting recovery...",
//                                EMOJI_ERROR, EMOJI_WARNING, consecutiveFailures);
//
//                        try {
//                            clearBetslip(page);
//                            returnToSportPage(page, configuredSport);
//                            waitForPageReady(page);
//                            consecutiveFailures = 0;
//                            log.info("{} {} Recovery successful", EMOJI_SUCCESS, EMOJI_SYNC);
//                        } catch (Exception recoveryError) {
//                            log.error("{} {} Recovery failed: {}",
//                                    EMOJI_ERROR, EMOJI_SYNC, recoveryError.getMessage());
//                            throw new RuntimeException("Failed to recover from errors", recoveryError);
//                        }
//                    }
//
//                    // Wait before next iteration
//                    Thread.sleep(pollIntervalMs);
//                }
//
//                // Check if paused
//                if (isPaused.get()) {
//                    log.info("{} {} Betting loop paused", EMOJI_WARNING, EMOJI_CLOCK);
//                    while (isPaused.get() && isRunning.get()) {
//                        Thread.sleep(1000);
//                    }
//                    log.info("{} {} Betting loop resumed", EMOJI_SUCCESS, EMOJI_START);
//                }
//            }
//
//            log.info("{} {} Betting loop ended", EMOJI_INFO, EMOJI_SHUTDOWN);
//
//        } catch (Exception e) {
//            log.error("{} {} Error in windowEntry: {}", EMOJI_ERROR, EMOJI_WARNING, e.getMessage(), e);
//            isWindowUpAndRunning.set(false);
//            throw e;
//
//        } finally {
//            isRunning.set(false);
//            isWindowUpAndRunning.set(false);
//
//            // Stop health monitor before closing page
//            if (healthMonitor != null) {
//                try {
//                    healthMonitor.stop();
//                } catch (Exception e) {
//                    log.debug("Error stopping health monitor: {}", e.getMessage());
//                }
//            }
//
//            // Close the page explicitly
//            if (page != null && !page.isClosed()) {
//                try {
//                    page.close();
//                    log.info("Page closed successfully");
//                } catch (Exception e) {
//                    log.warn("Error closing page: {}", e.getMessage());
//                }
//            }
//
//            // Save context (if still valid)
//            if (currentContext != null) {
//                try {
//                    saveContext(currentContext);
//                    log.info("{} {} Context saved successfully", EMOJI_SUCCESS, EMOJI_INIT);
//                } catch (Exception e) {
//                    log.warn("Failed to save context: {}", e.getMessage());
//                }
//            }
//
//            log.info("{} {} Window entry completed", EMOJI_SUCCESS, EMOJI_INIT);
//        }
//    }
//
//    // ========================================================================
//    // INTERFACE IMPLEMENTATIONS
//    // ========================================================================
//
//    @Override
//    public void pause() {
//        log.info("{} {} Pausing OneWin window...", EMOJI_WARNING, EMOJI_CLOCK);
//        isPaused.set(true);
//        // TODO: Implementation for pause functionality
//    }
//
//    @Override
//    public void resume() {
//        log.info("{} {} Resuming OneWin window...", EMOJI_SUCCESS, EMOJI_START);
//        isPaused.set(false);
//        // TODO: Implementation for resume functionality
//    }
//
//    @Override
//    public void stop() {
//        log.info("{} {} Stopping OneWin window...", EMOJI_SHUTDOWN, EMOJI_WARNING);
//        isRunning.set(false);
//        isWindowUpAndRunning.set(false);
//        // TODO: Implementation for stop functionality
//    }
//
//    @Override
//    public boolean isRunning() {
//        return isRunning.get();
//    }
//
//    @Override
//    public boolean isPaused() {
//        return isPaused.get();
//    }
//
//    @Override
//    public void shutdown() {
//        log.info("{} {} Shutting down OneWin window...", EMOJI_SHUTDOWN, EMOJI_TRASH);
//
//        try {
//            stop();
//
//            if (healthMonitor != null) {
//                healthMonitor.stop();
//            }
//
//            if (currentContext != null) {
//                saveContext(currentContext);
//                currentContext.close();
//            }
//
//            if (browser != null) {
//                browser.close();
//            }
//
//            if (playwright != null) {
//                playwright.close();
//            }
//
//            log.info("{} {} Shutdown completed successfully", EMOJI_SUCCESS, EMOJI_SHUTDOWN);
//        } catch (Exception e) {
//            log.error("{} {} Error during shutdown: {}", EMOJI_ERROR, EMOJI_SHUTDOWN, e.getMessage(), e);
//        }
//    }
//
//    // ========================================================================
//    // BETTING TASK DATA CLASS
//    // ========================================================================
//
//
//    /**
//     * Custom exception for captcha detection
//     */
//    public static class CaptchaDetectedException extends Exception {
//        public CaptchaDetectedException(String message) {
//            super(message);
//        }
//
//        public CaptchaDetectedException(String message, Throwable cause) {
//            super(message, cause);
//        }
//    }
//}
