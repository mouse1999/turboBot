package com.mouse.bet.window;

import com.microsoft.playwright.*;
import com.mouse.bet.config.WindowConfig;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.exception.CaptchaDetectedException;
import com.mouse.bet.exception.PageHealthException;
import com.mouse.bet.interfaces.BettingTask;
import com.mouse.bet.interfaces.BettingWindow;
import com.mouse.bet.manager.ProfileManager;
import com.mouse.bet.manager.WindowSyncManager;
import com.mouse.bet.mock.MockBettingTask;
import com.mouse.bet.monitor.PageHealthMonitor;
import com.mouse.bet.profile.UserAgentProfile;
import com.mouse.bet.service.ArbOutcomeService;

import com.mouse.bet.util.onewin.OneWinLoginUtil;
import com.mouse.bet.util.onewin.OneWinMarketUtil;
import com.mouse.bet.util.onewin.OneWinNavigationUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class OneWin implements BettingWindow, Runnable {
    private static final String EMOJI_INIT = "🚀";
    private static final String EMOJI_LOGIN = "🔐";
    private static final String EMOJI_BET = "🎯";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_SYNC = "🔄";
    private static final String EMOJI_POLL = "📊";
    private static final String EMOJI_HEALTH = "💚";
    private static final String EMOJI_SHUTDOWN = "🛑";
    private static final String EMOJI_START = "▶️";
    private static final String EMOJI_SEARCH = "🔍";
    private static final String EMOJI_INFO = "ℹ️";
    private static final String EMOJI_TRASH = "🗑️";
    private static final String EMOJI_TARGET = "🎯";
    private static final String EMOJI_ROCKET = "🚀";
    private static final String EMOJI_NAVIGATION = "🧭";
    private static final String EMOJI_CLOCK = "⏰";
    private static final String EMOJI_GAME = "🎮";
    private static final String EMOJI_MARKET = "📈";
    private static final String EMOJI_CART = "🛒";
    private static final String EMOJI_MONEY = "💰";

    private Playwright playwright;
    private Browser browser;
    private BrowserContext currentContext;
    private UserAgentProfile profile;
    private final WindowConfig windowConfig;
    private static final String CONTEXT_FILE = "onewin-context.json";
    private PageHealthMonitor healthMonitor;
    private final ProfileManager profileManager;
    private final ArbOutcomeService arbOutcomeService;
    private final WindowSyncManager syncManager;
    


    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isWindowUpAndRunning = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isLoggedIn = new AtomicBoolean(false);

    // BETTING INDICATOR - Shows when a bet placement is in progress
    private final AtomicBoolean isBetInProgress = new AtomicBoolean(false);

    @Value("${onewin.username:7063442030}")
    private String oneUsername;

    @Value("${onewin.password:Victor?!$#070}")
    private String onePassword;


    @Value("${onewin.context.path:./playwright-context}")
    private String contextPath;

    @Value("${onewin.max.retry.attempts:3}")
    private int maxRetryAttempts;

    @Value("${onewin.poll.interval.ms:2000}")
    private long pollIntervalMs;

    @Value("${bet.timeout.seconds:30}")
    private int betTimeoutSeconds;

    @Value("${partner.timeout.seconds:10}")
    private int partnerTimeout;

    @Value("${deploy.timeout.seconds:3}")
    private int deployTimeout;

    @Value("${fetch.enabled.football:true}")
    private boolean fetchFootballEnabled;

    @Value("${fetch.enabled.basketball:false}")
    private boolean fetchBasketballEnabled;

    @Value("${fetch.enabled.table-tennis:false}")
    private boolean fetchTableTennisEnabled;

    @Value("${onewin.base.url:https://1win.ng/betting/live}")
    private String baseUrl;

    @Value("${onewin.login.url:https://1win.com/login}")
    private String loginUrl;

    @Value("${onewin.live.events.url:https://1win.com/live}")
    private String liveEventsUrl;

    /**
     * Initialize Playwright and browser
     */
    @PostConstruct
    public void init() {
        log.info("{} {} Initializing MSport with Playwright...", EMOJI_INIT, EMOJI_BET);
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setArgs(Arrays.asList(
                            "--start-maximized",  // Start maximized
                            "--window-size=2560,1440",  // Larger window size to prevent wrapping
                            "--force-device-scale-factor=1",  // Prevent scaling issues
                            "--disable-blink-features=AutomationControlled"  // Hide automation indicators
                    ))
                    .setSlowMo(0));

            log.info("{} {} Playwright initialized successfully", EMOJI_SUCCESS, EMOJI_INIT);

        } catch (Exception e) {
            log.error("{} {} Failed to initialize Playwright: {}", EMOJI_ERROR, EMOJI_INIT, e.getMessage(), e);
            throw new RuntimeException("Playwright initialization failed", e);
        }


//        run();
    }


    /**
     * Poll for a betting task from the dispatcher
     * This method waits for and retrieves the next betting task to process
     *
     * @return BettingTask object containing game info, market, outcome details, or null if no task available
     * @throws Exception if polling fails
     */
    private BettingTask pollTaskFromDispatcher() throws Exception {
        log.info("{} {} Polling for betting task from dispatcher...", EMOJI_POLL, EMOJI_SEARCH);
        // TODO: Implementation to poll task from dispatcher
        // This should communicate with your dispatcher service to get the next betting task
        return MockBettingTask.createSampleSoftBookTask();
    }

    // ========================================================================
    // BET PLACEMENT WORKFLOW WITH INDICATOR
    // ========================================================================

    /**
     * Deploy bet - orchestrates the complete bet deployment flow with synchronization
     * 1. Register intent with sync manager
     * 2. Navigate to game
     * 3. Find market
     * 4. Select outcome
     * 5. Verify betslip
     * 6. Mark deployment success
     * 7. Wait for partner deployment
     *
     * @param page The Playwright page instance
     * @param task The betting task containing all bet details
     * @return true if bet is successfully deployed to betslip, false otherwise
     * @throws Exception if deployment fails
     */
    private boolean deployBet(Page page, BettingTask task) throws Exception {
        String arbId = task.taskId();
        log.info("{} {} Starting bet deployment for task: {}",
                EMOJI_START, EMOJI_TARGET, arbId);

        try {
            // ========================================
            // STEP 1: REGISTER INTENT
            // ========================================
            boolean intentRegistered = syncManager.registerIntent(
                    arbId,
                    BookMaker.MSPORT,
                    task.expectedOdds()
            );

            if (!intentRegistered) {
                log.warn("{} {} Arb cancelled during intent registration: {}",
                        EMOJI_WARNING, EMOJI_SYNC, arbId);
                return false;
            }

            log.info("{} {} Intent registered for arb: {}", EMOJI_SUCCESS, EMOJI_SYNC, arbId);

            // ========================================
            // STEP 2: NAVIGATE TO GAME
            // ========================================
            log.info("{} {} [1/4] Navigating to game: {} vs {}",
                    EMOJI_GAME, EMOJI_NAVIGATION, task.homeTeam(), task.awayTeam());

            boolean gameAvailable = OneWinNavigationUtil.navigateToGame(page, task);
            randomHumanDelay(800, 1500);
            OneWinNavigationUtil.waitForPageReady(page);


            if (!gameAvailable) {
                log.warn("{} {} Game not available: {}", EMOJI_WARNING, EMOJI_GAME, arbId);
                syncManager.notifyBetFailure(arbId, com.mouse.bet.enums.BookMaker.MSPORT,
                        "Game not available");
                syncManager.skipArbAndSync(arbId);
                return false;
            }

            log.info("{} {} Game navigation successful", EMOJI_SUCCESS, EMOJI_GAME);

            randomHumanDelay(500, 1000);

            // ========================================
            // STEP 3: FIND MARKET
            // ========================================

            boolean selectAndVerify = OneWinMarketUtil.selectAndVerifyBetJS(page,task, arbOutcomeService);
            if(!selectAndVerify) {
                log.warn("{} {} Bet selection and verification failed", EMOJI_WARNING, EMOJI_CART);

                OneWinMarketUtil.clearBetSlip(page);
                syncManager.notifyBetFailure(arbId, com.mouse.bet.enums.BookMaker.MSPORT,
                        "Bet selection and verification failed");
                syncManager.skipArbAndSync(arbId);
                return false;

            }

            // ========================================
            // STEP 6: MARK DEPLOYMENT SUCCESS
            // ========================================
//            boolean markedDeployed = syncManager.markDeploymentSuccess(
//                    arbId,
//                    BookMaker.MSPORT
//            );
//
//            if (!markedDeployed) {
//                log.warn("{} {} Arb cancelled after deployment: {}",
//                        EMOJI_WARNING, EMOJI_SYNC, arbId);
//                OneWinMarketUtil.clearBetSlip(page);
//                return false;
//            }

            log.info("{} {} Deployment marked as successful", EMOJI_SUCCESS, EMOJI_SYNC);

            // ========================================
            // STEP 7: WAIT FOR PARTNER DEPLOYMENT
            // ========================================
//            log.info("{} {} Waiting for partner to deploy...", EMOJI_SYNC, EMOJI_CLOCK);
//
//            boolean partnerDeployed = syncManager.waitForPartnerDeploymentOrTimeout(
//                    arbId,
//                    com.mouse.bet.enums.BookMaker.MSPORT,
//                    Duration.ofSeconds(deployTimeout)
//            );
//
//            if (!partnerDeployed) {
//                log.warn("{} {} Partner deployment failed or timeout", EMOJI_WARNING, EMOJI_SYNC);
//                OneWinMarketUtil.clearBetSlip(page);
//                // Partner will handle cleanup and arb killing
//                return false;
//            }

            log.info("{} {} Both windows DEPLOYED - ready for simultaneous placement!",
                    EMOJI_SUCCESS, EMOJI_ROCKET);

            randomHumanDelay(200, 400);

            log.info("{} {} Bet deployment completed successfully for task: {}",
                    EMOJI_SUCCESS, EMOJI_ROCKET, arbId);
            return true;

        } catch (Exception e) {
            log.error("{} {} Bet deployment failed: {}",
                    EMOJI_ERROR, EMOJI_BET, e.getMessage(), e);

            // Notify failure and sync
            syncManager.notifyBetFailure(arbId, com.mouse.bet.enums.BookMaker.MSPORT,
                    "Deployment exception: " + e.getMessage());
            syncManager.skipArbAndSync(arbId);

            // Clean up betslip on error
            try {
                OneWinMarketUtil.clearBetSlip(page);
            } catch (Exception clearEx) {
                log.warn("{} {} Failed to clear betslip after error: {}",
                        EMOJI_WARNING, EMOJI_CART, clearEx.getMessage());
            }

            throw e;
        }
    }

    /**
     * Human-like random delay to avoid bot detection
     *
     * @param minMs Minimum delay in milliseconds
     * @param maxMs Maximum delay in milliseconds
     */
    private void randomHumanDelay(long minMs, long maxMs) {
        try {
            long delay = minMs + ThreadLocalRandom.current().nextLong(maxMs - minMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Delay interrupted");
        }
    }

    /**
     * Handle successful bet placement
     * Logs success and notifies dispatcher
     *
     * @param task The successfully placed betting task
     */
    private void handleBetSuccess(BettingTask task) {
        log.info("{} {} Bet successfully placed for task {}",
                EMOJI_SUCCESS, EMOJI_MONEY, task.taskId());
        // TODO: Notify dispatcher of success
        // TODO: Update task status to completed
    }

    /**
     * Handle bet placement failure
     * Logs error, clears betslip, and prepares for next task
     *
     * @param page The Playwright page instance
     * @param task The failed betting task
     * @param error The exception that caused the failure
     */
    private void handleBetFailure(Page page, BettingTask task, Exception error) {
        log.error("{} {} Bet placement failed for task {}: {}",
                EMOJI_ERROR, EMOJI_BET, task.taskId(), error.getMessage());

        try {
            OneWinMarketUtil.clearBetSlip(page);
        } catch (Exception e) {
            log.warn("{} {} Failed to clear betslip after error: {}",
                    EMOJI_WARNING, EMOJI_CART, e.getMessage());
        }

        // TODO: Notify dispatcher of failure
        // TODO: Update task status to failed
    }

    /**
     * Perform rollback - cancel/cash out the bet
     * Called when partner bet fails and this bet succeeded
     *
     * @param page The Playwright page instance
     * @param arbId The arb ID
     * @param betId The bet ID to rollback
     * @return true if rollback successful, false otherwise
     */
    private boolean performRollback(Page page, String arbId, String betId) {
        log.info("🔄 Starting rollback for ArbId: {} | BetId: {}", arbId, betId);

        try {
            // Navigate to bet history/my bets page
            String myBetsUrl = "https://www.msport.com/ng/web/mybets";
            page.navigate(myBetsUrl);
            page.waitForTimeout(2000);

            // Look for the specific bet
            String betSelector = String.format(
                    "//div[contains(@class, 'bet-item')]//span[contains(text(), '%s')]", betId
            );

            if (page.locator(betSelector).count() > 0) {
                log.info("✅ Bet found in history: {}", betId);

                // Try to find and click cash out button
                String cashOutSelector = String.format(
                        "%s//ancestor::div[contains(@class, 'bet-item')]//button[contains(text(), 'Cash Out')]",
                        betSelector
                );

                if (page.locator(cashOutSelector).count() > 0) {
                    log.info("💰 Cash out available for bet: {}", betId);
                    page.locator(cashOutSelector).first().click();
                    page.waitForTimeout(1000);

                    // Confirm cash out
                    String confirmSelector = "button:has-text('Confirm')";
                    if (page.locator(confirmSelector).count() > 0) {
                        page.locator(confirmSelector).first().click();
                        page.waitForTimeout(2000);

                        log.info("✅ Cash out executed for bet: {}", betId);
                        return true;
                    }
                } else {
                    log.warn("⚠️ Cash out not available for bet: {}", betId);
                    // TODO: Implement hedge betting logic if cash out not available
                    return false;
                }
            } else {
                log.warn("❌ Bet not found in history: {}", betId);
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Rollback exception for bet {}: {}", betId, e.getMessage(), e);
            return false;
        }

        return false;
    }


    /**
     * Determine which sport to navigate to based on configuration
     * @return The configured sport type
     */
    private Sport determineConfiguredSport() {
        log.info("{} {} Determining configured sport from settings...", EMOJI_INFO, EMOJI_SEARCH);

        if (fetchTableTennisEnabled) {
            log.info("{} Table Tennis is enabled", EMOJI_TARGET);
            return Sport.TABLE_TENNIS;
        } else if (fetchFootballEnabled) {
            log.info("{} Football is enabled", EMOJI_TARGET);
            return Sport.FOOTBALL;
        } else if (fetchBasketballEnabled) {
            log.info("{} Basketball is enabled", EMOJI_TARGET);
            return Sport.BASKETBALL;
        }

        log.warn("{} {} No sport enabled in configuration, defaulting to TABLE_TENNIS",
                EMOJI_WARNING, EMOJI_INFO);
        return Sport.TABLE_TENNIS;
    }

    /**
     * Handle captcha scenario if detected
     */
    private void handleCaptchaScenario() {
        log.warn("{} {} CAPTCHA detected - implementing recovery strategy", EMOJI_WARNING, EMOJI_SYNC);

        try {
            Thread.sleep(5000);
            recreateContext();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{} {} Interrupted during captcha handling", EMOJI_ERROR, EMOJI_WARNING);
        }
    }

    /**
     * Wait between retry attempts with exponential backoff
     * @param attempt Current attempt number
     */
    private void waitBetweenRetries(int attempt) {
        long waitTime = Math.min(2000L * attempt, 10000L);
        log.info("{} {} Waiting {}ms before retry attempt {}...",
                EMOJI_CLOCK, EMOJI_SYNC, waitTime, attempt + 1);

        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} {} Wait interrupted", EMOJI_WARNING, EMOJI_CLOCK);
        }
    }

    // ========================================================================
    // CONTEXT MANAGEMENT
    // ========================================================================

    /**
     * Create new browser context
     */
    private BrowserContext newContext(Browser browser, UserAgentProfile profile) {
        log.info("ViewPort size {} x {}", profile.getViewport().getWidth(), profile.getViewport().getHeight());
        log.info("Headers: {}", getAllHeaders(profile));

        return browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(profile.getUserAgent())
                .setLocale("en-US")
                .setViewportSize(profile.getViewport().getWidth(), profile.getViewport().getHeight())
        );
    }

    /**
     * Get all headers from profile
     */
    private Map<String, String> getAllHeaders(UserAgentProfile profile) {
        Map<String, String> all = new HashMap<>();
        if (profile.getHeaders().getStandardHeaders() != null) {
            all.putAll(profile.getHeaders().getStandardHeaders());
        }
        if (profile.getHeaders().getClientHintsHeaders() != null) {
            all.putAll(profile.getHeaders().getClientHintsHeaders());
        }
        return all;
    }

    /**
     * Load or create browser context
     */
    private BrowserContext loadOrCreateContext() {
        profile = profileManager.getNextProfile();
        Path contextFilePath = Paths.get(contextPath, CONTEXT_FILE);

        if (Files.exists(contextFilePath)) {
            try {
                log.info("Loading existing browser context from: {}", contextFilePath);

                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent(profile.getUserAgent())
                        .setLocale("en-US")
                        .setViewportSize(null)
                        .setIgnoreHTTPSErrors(true)
                        .setStorageStatePath(contextFilePath));

                if (context != null) {
                    log.info("Existing context loaded successfully");
                    return context;
                }

            } catch (Exception e) {
                log.warn("Failed to load existing context: {}", e.getMessage());

                try {
                    Files.deleteIfExists(contextFilePath);
                    log.info("Deleted corrupted context file");
                } catch (Exception deleteEx) {
                    log.warn("Could not delete context file: {}", deleteEx.getMessage());
                }
            }
        }

        log.info("Creating new browser context");
        return newContext(browser, profile);
    }

    /**
     * Save browser context
     */
    private void saveContext(BrowserContext context) {
        if (context == null) return;

        try {
            Path contextDir = Paths.get(contextPath);
            if (!Files.exists(contextDir)) {
                Files.createDirectories(contextDir);
            }

            Path contextFilePath = contextDir.resolve(CONTEXT_FILE);
            context.storageState(new BrowserContext.StorageStateOptions()
                    .setPath(contextFilePath));
            log.info("Browser context saved to: {}", contextFilePath);
        } catch (Exception e) {
            log.error("Failed to save context: {}", e.getMessage(), e);
        }
    }

    /**
     * Recreate browser context
     * Proper cleanup of all resources before recreating
     */
    private void recreateContext() {
        log.info("{} {} Recreating browser context...", EMOJI_SYNC, EMOJI_INIT);
        isLoggedIn.set(false);

        if (healthMonitor != null) {
            try {
                healthMonitor.stop();
            } catch (Exception e) {
                log.warn("Error stopping health monitor: {}", e.getMessage());
            }
            healthMonitor = null;
        }

        if (currentContext != null) {
            try {
                for (Page page : currentContext.pages()) {
                    try {
                        if (!page.isClosed()) {
                            page.close();
                        }
                    } catch (Exception e) {
                        log.debug("Error closing page: {}", e.getMessage());
                    }
                }

                currentContext.close();
                log.info("Old context closed successfully");

            } catch (Exception e) {
                log.warn("Error closing context: {}", e.getMessage());
            } finally {
                currentContext = null;
            }
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========================================================================
    // MAIN ENTRY POINTS
    // ========================================================================

    /**
     * Main entry point - runs the betting window with retry logic
     */
    @Override
    public void run() {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetryAttempts) {
            attempt++;
            log.info("{} {} Starting OneWin attempt {}/{}",
                    EMOJI_INIT, EMOJI_BET, attempt, maxRetryAttempts);

            try {
                windowEntry();
                log.info("{} {} OneWin completed successfully", EMOJI_SUCCESS, EMOJI_BET);
                break;

            } catch (CaptchaDetectedException e) {
                log.error("{} {} CAPTCHA detected on attempt {}: {}",
                        EMOJI_ERROR, EMOJI_WARNING, attempt, e.getMessage());
                lastException = e;
                handleCaptchaScenario();

            } catch (PageHealthException e) {
                log.error("{} {} Page health check failed on attempt {}: {}",
                        EMOJI_ERROR, EMOJI_HEALTH, attempt, e.getMessage());
                lastException = e;
                if (attempt < maxRetryAttempts) {
                    recreateContext();
                    waitBetweenRetries(attempt);
                }

            } catch (PlaywrightException e) {
                String msg = e.getMessage();

                if (msg.contains("Object doesn't exist") ||
                        msg.contains("frame was detached") ||
                        msg.contains("ERR_ABORTED") ||
                        msg.contains("Target closed")) {

                    log.error("{} {} Playwright object lifecycle error on attempt {}: {}",
                            EMOJI_ERROR, EMOJI_WARNING, attempt, msg);
                    lastException = e;

                    if (attempt < maxRetryAttempts) {
                        recreateContext();
                        waitBetweenRetries(attempt);
                    }
                } else {
                    log.error("{} {} Unexpected Playwright error: {}",
                            EMOJI_ERROR, EMOJI_BET, msg, e);
                    lastException = e;
                    if (attempt < maxRetryAttempts) {
                        recreateContext();
                        waitBetweenRetries(attempt);
                    }
                }

            } catch (Exception e) {
                log.error("{} {} Unexpected error on attempt {}: {}",
                        EMOJI_ERROR, EMOJI_WARNING, attempt, e.getMessage(), e);
                lastException = e;
                if (attempt < maxRetryAttempts) {
                    recreateContext();
                    waitBetweenRetries(attempt);
                }
            }
        }

        if (lastException != null) {
            String errorMsg = String.format("Failed after %d attempts. Last error: %s",
                    maxRetryAttempts, lastException.getMessage());
            log.error("{} {} All retry attempts exhausted for profile {}: {}",
                    EMOJI_ERROR, EMOJI_BET, profile != null ? profile.getId() : "unknown", errorMsg);
        }
    }



    /**
     * Main window entry method - orchestrates the complete betting flow
     *
     * Flow:
     * 1. Initial setup: Navigate to bookmaker, login, go to sport page
     * 2. Continuous loop: Poll tasks -> Navigate to game -> Place bet -> Return to sport page
     * 3. Loop continues until window is stopped or max retries reached
     */
    private void windowEntry() throws Exception {
        Page page = null;
        try {
            log.info("{} {} Starting window entry process...", EMOJI_START, EMOJI_ROCKET);
            isRunning.set(true);

            // ===== PHASE 1: INITIAL SETUP =====
            if (currentContext == null) {
                currentContext = loadOrCreateContext();
            }
            if (currentContext == null) {
                throw new RuntimeException("Failed to create valid browser context");
            }

            // Close any lingering pages
            for (Page existingPage : currentContext.pages()) {
                if (!existingPage.isClosed()) {
                    log.warn("Closing existing page: {}", existingPage.url());
                    try { existingPage.close(); } catch (Exception ignored) {}
                }
            }

            page = currentContext.newPage();
            log.info("{} {} New page created successfully", EMOJI_SUCCESS, EMOJI_INIT);

            // Navigate to bookmaker
            OneWinNavigationUtil.navigateToBookmaker(page, baseUrl);
            OneWinNavigationUtil.waitForPageReady(page);
            log.info("{} {} Navigation to bookmaker completed", EMOJI_SUCCESS, EMOJI_NAVIGATION);

            // Login handling
            boolean loggedIn = OneWinLoginUtil.checkIfLoggedIn(page);
            if (!loggedIn) {
                log.info("{} {} User not logged in, attempting login...", EMOJI_INFO, EMOJI_LOGIN);
                OneWinLoginUtil.performLogin(page, oneUsername, onePassword);
                OneWinNavigationUtil.waitForPageReady(page);
                if (!OneWinLoginUtil.checkIfLoggedIn(page)) {
                    throw new RuntimeException("Login verification failed");
                }
                isLoggedIn.set(true);
                log.info("{} {} Login successful", EMOJI_SUCCESS, EMOJI_LOGIN);
            } else {
                isLoggedIn.set(true);
                log.info("{} {} User already logged in", EMOJI_SUCCESS, EMOJI_LOGIN);
            }

            // Navigate to live events → sport page
            OneWinNavigationUtil.navigateToLiveEvents(page);
            OneWinNavigationUtil.waitForPageReady(page);

            Sport configuredSport = determineConfiguredSport();
            OneWinNavigationUtil.navigateToSportPage(page, configuredSport);
            OneWinNavigationUtil.waitForPageReady(page);
            log.info("{} {} Navigation to {} page completed", EMOJI_SUCCESS, EMOJI_TARGET, configuredSport);

            isWindowUpAndRunning.set(true);
            log.info("{} {} Window is now up and running - entering betting loop", EMOJI_SUCCESS, EMOJI_ROCKET);

            // ===== PHASE 2: BETTING LOOP =====
            int consecutiveFailures = 0;
            final int maxConsecutiveFailures = 5;

            while (isRunning.get() && !isPaused.get()) {
                BettingTask task = null;
                try {
                    randomHumanDelay(1000, 2500);
                    // 1. Poll for task
                    log.info("{} {} Polling for new betting task...", EMOJI_POLL, EMOJI_CLOCK);
                    task = pollTaskFromDispatcher();
                    if (task == null) {
                        Thread.sleep(pollIntervalMs);
                        continue;
                    }
                    log.info("{} {} Received betting task: {}", EMOJI_SUCCESS, EMOJI_POLL, task.taskId());

                    // 2. Mark bet in progress
                    isBetInProgress.set(true);

                    // 3. DEPLOY: Full pre-placement + sync with partner
                    if (!deployBet(page, task)) {
                        log.warn("{} {} Deployment failed or cancelled - skipping task {}", EMOJI_WARNING, EMOJI_TARGET, task.taskId());
                        isBetInProgress.set(false);
                        consecutiveFailures++;
//                        continue;
                        return;
                    }

                    // 4. PLACE THE BET (both sides are now synchronized and ready)
                    log.info("🚀 SIMULTANEOUS BETTING | ArbId: {} | Bookmaker: MSPORT", task.taskId());
                    boolean betPlaced = OneWinMarketUtil.placeBet(page, task, arbOutcomeService);

                    // Placeholder bet ID - replace with actual extraction logic
                    String betId = "BET_" + System.currentTimeMillis();

                    if (!betPlaced) {
                        log.warn("❌ Bet placement failed | ArbId: {}", task.taskId());
                        syncManager.notifyBetFailure(task.taskId(), com.mouse.bet.enums.BookMaker.MSPORT, "Placement failed");

                        syncManager.waitForPartnerBetCompletion(
                                task.taskId(), com.mouse.bet.enums.BookMaker.MSPORT,
                                Duration.ofSeconds(betTimeoutSeconds + 5));

                        OneWinMarketUtil.clearBetSlip(page);
                        isBetInProgress.set(false);
                        consecutiveFailures++;
                        continue;
                    }

                    log.info("✅ Bet PLACED | ArbId: {} | Stake: {} | Odds: {}", task.taskId(),
                            task.stakeAmount(), task.expectedOdds());

                    syncManager.notifyBetPlaced(task.taskId(), com.mouse.bet.enums.BookMaker.MSPORT);

                    // Close success modal if present
                    randomHumanDelay(2000, 3000);
                    try {
                        page.locator("div.m-betslip-success button:has-text('OK')")
                                .first()
                                .click(new Locator.ClickOptions().setTimeout(2000));
                    } catch (Exception ignored) {}

                    // 5. Wait for partner result & handle rollback if needed
                    log.info("⏳ Waiting for partner to complete | ArbId: {}", task.taskId());
                    WindowSyncManager.PartnerBetResult partnerResult = syncManager.waitForPartnerBetCompletion(
                            task.taskId(), com.mouse.bet.enums.BookMaker.MSPORT,
                            java.time.Duration.ofSeconds(betTimeoutSeconds + 5));

                    if (partnerResult.isSuccess()) {
                        log.info("✅ BOTH BETS PLACED SUCCESSFULLY | ArbId: {}", task.taskId());
                        handleBetSuccess(task);
                        consecutiveFailures = 0;
                    } else {
                        log.warn("⚠️ PARTNER FAILED - INITIATING ROLLBACK | ArbId: {}", task.taskId());
                        syncManager.requestRollback(task.taskId(), com.mouse.bet.enums.BookMaker.MSPORT,
                                "Partner failed: " + partnerResult.getMessage());
                        boolean rollbackSuccess = performRollback(page, task.taskId(), betId);
                        syncManager.notifyRollbackCompleted(task.taskId(), com.mouse.bet.enums.BookMaker.MSPORT, rollbackSuccess);
                        if (!rollbackSuccess) {
                            log.error("❌ ROLLBACK FAILED - MANUAL INTERVENTION REQUIRED | ArbId: {}", task.taskId());
                        }
                    }

                    // 6. Return to sport page for next task
                    OneWinNavigationUtil.returnToSportPage(page, configuredSport);
                    OneWinNavigationUtil.waitForPageReady(page);
                    log.info("{} {} Returned to sport page, ready for next task", EMOJI_SUCCESS, EMOJI_NAVIGATION);

                } catch (Exception e) {
                    log.error("{} {} Unexpected error processing task: {}", EMOJI_ERROR, EMOJI_WARNING, e.getMessage(), e);
                    if (task != null) {
                        handleBetFailure(page, task, e);
                    }
                    consecutiveFailures++;
                } finally {
                    isBetInProgress.set(false);
                    if (task != null) {
                        syncManager.unRegisterIntent(task.taskId(), com.mouse.bet.enums.BookMaker.MSPORT);
                    }
                }

                // Recovery from too many failures
                if (consecutiveFailures >= maxConsecutiveFailures) {
                    log.warn("{} {} Too many consecutive failures ({}), attempting recovery...", EMOJI_WARNING, EMOJI_SYNC, consecutiveFailures);
                    try {
                        OneWinMarketUtil.clearBetSlip(page);
                        OneWinNavigationUtil.returnToSportPage(page, configuredSport);
                        OneWinNavigationUtil.waitForPageReady(page);
                        consecutiveFailures = 0;
                        log.info("{} {} Recovery completed", EMOJI_SUCCESS, EMOJI_SYNC);
                    } catch (Exception recoveryEx) {
                        log.error("{} {} Recovery failed: {}", EMOJI_ERROR, EMOJI_SYNC, recoveryEx.getMessage());
                        throw recoveryEx;
                    }
                }

                // Pause handling
                if (isPaused.get()) {
                    log.info("{} {} Betting loop paused", EMOJI_WARNING, EMOJI_CLOCK);
                    while (isPaused.get() && isRunning.get()) {
                        Thread.sleep(1000);
                    }
                    log.info("{} {} Betting loop resumed", EMOJI_SUCCESS, EMOJI_START);
                }

                Thread.sleep(pollIntervalMs);
            }

            log.info("{} {} Betting loop ended normally", EMOJI_INFO, EMOJI_SHUTDOWN);

        } catch (Exception e) {
            log.error("{} {} Critical error in windowEntry: {}", EMOJI_ERROR, EMOJI_WARNING, e.getMessage(), e);
            isWindowUpAndRunning.set(false);
            throw e;
        } finally {
            isRunning.set(false);
            isWindowUpAndRunning.set(false);
            isBetInProgress.set(false);

            if (page != null && !page.isClosed()) {
                try { page.close(); } catch (Exception ignored) {}
            }

            if (currentContext != null) {
                try { saveContext(currentContext); } catch (Exception ignored) {}
            }

            log.info("{} {} Window entry completed", EMOJI_SUCCESS, EMOJI_SHUTDOWN);
        }
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public void shutdown() {

    }


}

