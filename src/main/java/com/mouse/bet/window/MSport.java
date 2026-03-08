package com.mouse.bet.window;

import com.microsoft.playwright.*;
import com.mouse.bet.checker.ArbChecker;
import com.mouse.bet.config.WindowConfig;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.enums.Sport;
import com.mouse.bet.exception.CaptchaDetectedException;
import com.mouse.bet.exception.PageHealthException;
import com.mouse.bet.interfaces.BettingWindow;
import com.mouse.bet.manager.ProfileManager;
import com.mouse.bet.manager.WindowSyncManager;
import com.mouse.bet.monitor.PageHealthMonitor;
import com.mouse.bet.orchestrator.Orchestrator;
import com.mouse.bet.orchestrator.model.BetLeg;
import com.mouse.bet.orchestrator.model.BetLegTask;
import com.mouse.bet.profile.UserAgentProfile;
import com.mouse.bet.service.ArbOutcomeService;
import com.mouse.bet.util.msport.MSportBetPlacement;
import com.mouse.bet.util.msport.MSportLoginUtil;
import com.mouse.bet.util.msport.MSportMarketUtil;
import com.mouse.bet.util.msport.MSportNavigationUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MSport betting window implementation.
 *
 * Responsibilities:
 * - Poll BetLegTasks from orchestrator queue
 * - Deploy bets with partner synchronization (WindowSyncManager)
 * - Place bets and handle rollbacks
 * - Signal orchestrator completion via Phaser
 *
 * Two-layer synchronization:
 * 1. WindowSyncManager: MSport ↔ Partner window coordination
 * 2. Phaser: Worker → Orchestrator signaling
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MSport implements BettingWindow, Runnable {

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

    // Playwright components
    private Playwright playwright;
    private Browser browser;
    private BrowserContext currentContext;
    private UserAgentProfile profile;

    // Dependencies
    private final WindowConfig windowConfig;
    private final ProfileManager profileManager;
    private final WindowSyncManager syncManager;
    private final ArbOutcomeService arbOutcomeService;
    private final Orchestrator orchestrator;
    private final ArbChecker arbChecker;

    // Constants
    private static final String CONTEXT_FILE = "msport-context.json";
    private static final BookMaker BOOKMAKER = BookMaker.MSPORT;

    // Monitoring
    private PageHealthMonitor healthMonitor;

    // State flags
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isWindowUpAndRunning = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isLoggedIn = new AtomicBoolean(false);
    private final AtomicBoolean isBetInProgress = new AtomicBoolean(false);

    // Task queue - populated by orchestrator
    @Getter
    private final BlockingQueue<BetLegTask> taskQueue = new LinkedBlockingQueue<>();

    // Configuration properties
    @Value("${msport.username:}")
    private String msportUsername;

    @Value("${msport.password:}")
    private String msportPassword;

    @Value("${msport.login.url:https://www.msport.com/ng/web}")
    private String loginUrl;

    @Value("${msport.base.url:https://www.msport.com/ng/web}")
    private String baseUrl;

    @Value("${msport.context.path:./playwright-context}")
    private String contextPath;

    @Value("${msport.max.retry.attempts:3}")
    private int maxRetryAttempts;

    @Value("${msport.poll.interval.ms:2000}")
    private long pollIntervalMs;

    @Value("${bet.timeout.seconds:30}")
    private int betTimeoutSeconds;

    @Value("${partner.timeout.seconds:10}")
    private int partnerTimeout;

    @Value("${deploy.timeout.seconds:15}")
    private int deployTimeout;

    @Value("${fetch.enabled.football:false}")
    private boolean fetchFootballEnabled;

    @Value("${fetch.enabled.basketball:true}")
    private boolean fetchBasketballEnabled;

    @Value("${fetch.enabled.table-tennis:false}")
    private boolean fetchTableTennisEnabled;

    @Value("${msport.headless:false}")
    private boolean headless;

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    @PostConstruct
    public void init() {
        log.info("{} {} Initializing Optimized SportyBet with Playwright...", EMOJI_INIT, EMOJI_BET);
        try {
            playwright = Playwright.create();

            // Optimized argument list: Removed all --disable-cache and --disk-cache-size flags
            List<String> optimizedArgs = Arrays.asList(
                    // Core Performance
//                    "--disable-gpu",
//                    "--disable-dev-shm-usage",
//                    "--disable-extensions",
//                    "--no-sandbox",
//                    "--no-first-run",
//                    "--no-default-browser-check",
//                    "--mute-audio",
//
//                    // Background Process Stripping (Saves CPU)
//                    "--disable-background-networking",
//                    "--disable-background-timer-throttling",
//                    "--disable-backgrounding-occluded-windows",
//                    "--disable-renderer-backgrounding",
//                    "--metrics-recording-only",
//                    "--disable-component-update",
//                    "--disable-domain-reliability",
//
//                    // Speed Boosters (Resource Management)
//                    "--disable-images",
//                    "--blink-settings=imagesEnabled=false",
//                    "--disable-software-rasterizer",
//                    "--disable-popup-blocking",
//
//                    // Feature Stripping
//                    "--disable-features=TranslateUI,IsolateOrigins,BlockInsecurePrivateNetworkRequests",
//                    "--disable-site-isolation-trials",
//                    "--disable-translate",
//                    "--disable-sync"
                    "--start-maximized",
                    "--window-size=2560,1440",
                    "--force-device-scale-factor=1",
                    "--disable-blink-features=AutomationControlled",
                    // Speed Boosters (Resource Management)
//                    "--disable-images",
                    "--blink-settings=imagesEnabled=false",
                    "--disable-software-rasterizer",
                    "--disable-popup-blocking",

                    "--disable-gpu",
                    "--disable-dev-shm-usage",
                    "--disable-extensions",
                    "--no-sandbox",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--mute-audio",

                    // Background Process Stripping (Saves CPU)
                    "--disable-background-networking",
                    "--disable-background-timer-throttling",
                    "--disable-backgrounding-occluded-windows",
                    "--disable-renderer-backgrounding",
                    "--metrics-recording-only",
                    "--disable-component-update",
                    "--disable-domain-reliability",

                    // Speed Boosters (Resource Management)
                    "--disable-images",
                    "--blink-settings=imagesEnabled=false",
                    "--disable-software-rasterizer",
                    "--disable-popup-blocking",

                    // Feature Stripping
                    "--disable-features=TranslateUI,IsolateOrigins,BlockInsecurePrivateNetworkRequests",
                    "--disable-site-isolation-trials",
                    "--disable-translate",
                    "--disable-sync"
            );

            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setArgs(optimizedArgs)
                    .setSlowMo(0));

            log.info("{} {} Playwright initialized with Cache Support", EMOJI_SUCCESS, EMOJI_INIT);

            log.info("Registering SportyBet Window for Bet placing");
            orchestrator.registerWorker(BOOKMAKER, taskQueue);

        } catch (Exception e) {
            log.error("{} {} Failed to initialize Playwright: {}", EMOJI_ERROR, EMOJI_INIT, e.getMessage(), e);
            throw new RuntimeException("Playwright initialization failed", e);
        }
    }

//    "--start-maximized",
//            "--window-size=2560,1440",
//            "--force-device-scale-factor=1",
//            "--disable-blink-features=AutomationControlled"

    // ========================================================================
    // TASK POLLING
    // ========================================================================

    /**
     * Poll for a BetLegTask from the orchestrator task queue.
     * Blocks until a task is available or timeout occurs.
     *
     * @return BetLegTask object or null if timeout/interrupted
     */
    private BetLegTask pollTaskFromDispatcher() {
        try {
            log.debug("{} {} Polling for BetLegTask from queue...", EMOJI_POLL, EMOJI_SEARCH);

            BetLegTask task = taskQueue.poll(pollIntervalMs, TimeUnit.MILLISECONDS);

            if (task != null) {
                log.info("{} {} Received BetLegTask | ArbId: {} | Bookmaker: {} | Outcome: {} | Odds: {} | Stake: {}",
                        EMOJI_SUCCESS, EMOJI_POLL,
                        task.getArbId(), task.getBookmaker(), task.getOutcome(),
                        task.getExpectedOdds(), task.getStakeAmount());

                log.info("{}", task.getArb().getOutcomeBreakdown());
            }

            return task;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} {} Task polling interrupted", EMOJI_WARNING, EMOJI_POLL);
            return null;
        } catch (Exception e) {
            log.error("{} {} Error polling task: {}", EMOJI_ERROR, EMOJI_POLL, e.getMessage(), e);
            return null;
        }

//        ArbOutcome outcome1 = ArbOutcome.builder()
//                .bookmakerId(1)
//                .bookmakerName(BookMaker.MSPORT)
//                .homeTeam("Putrajaya")
//                .awayTeam("Perak")
//                .marketType("Double Chance")
//                .outComeName("1 2")
//                .odds(new BigDecimal("1.85"))
//                .previousOdds(new BigDecimal("2.10"))
//                .stake(new BigDecimal("10"))
//                .sport("Soccer")
//                .progress("Not Started")
//                .reordered(false)
//                .initiator(true)
//                .leagueName("NBA")
//                .bookMakerUrl("https://www.msport.com/ng/web/sports/Soccer/live/International_Clubs_Club_Friendly_Games/KS_Gornik_Polkowice_vs_Warta_Gorzow_Wielkopolski/sr:match:68826198")
//                .build();
//
//
//        BetLeg betLeg = new BetLeg(
//                outcome1.getBookmakerName(),                    // BookMaker.MSPORT
//                outcome1.getBookmakerId(),                      // 1
//                outcome1.getMarketType(),                       // "Point Handicap"
//                outcome1.getOutComeName(),                      // "Home (-12.5)"
//                outcome1.getBookMakerUrl(),                     // "https://www.msport.com/..."
//                outcome1.getOdds().doubleValue(),               // 1.88
//                outcome1.getOdds().doubleValue() * (1.874), // 1.874 (min)
//                outcome1.getOdds().doubleValue() * (1.886), // 1.886 (max)
//                outcome1.getStake().doubleValue(),              // 531.91
//                outcome1.getLeagueName(),                       // "NBA"
//                outcome1.getHomeTeam(),                         // "Test Lakers"
//                outcome1.getAwayTeam(),
//                outcome1.getSubEventId(),
//                outcome1.getSport()
//                // "Test Celtics// "demo_arb_001"
//        );
//        Phaser phaser = new Phaser(1);
//        return BetLegTask.builder()
//                .betLeg(betLeg)
//                .barrier(phaser)
//                .bookmaker(BookMaker.MSPORT)
//                .build();

    }

    // ========================================================================
    // BET DEPLOYMENT
    // ========================================================================

    /**
     * Deploy bet to betslip with partner synchronization.
     * Uses WindowSyncManager for partner coordination.
     *
     * Steps:
     * 1. Register intent with partner
     * 2. Navigate to game
     * 3. Select and verify bet
     * 4. Mark deployment success
     * 5. Wait for partner deployment
     *
     * @param page The Playwright page instance
     * @param task The BetLegTask containing bet details
     * @return true if successfully deployed, false otherwise
     */
    private boolean deployBet(Page page, BetLegTask task) {
        String arbId = task.getTaskId();
        log.info("{} {} Starting bet deployment for task: {} | ArbId: {}",
                EMOJI_START, EMOJI_TARGET, arbId, task.getArbId());

        try {
             //STEP 1: REGISTER INTENT WITH PARTNER
            boolean intentRegistered = syncManager.registerIntent(
                    arbId,
                    BOOKMAKER,
                    task.getExpectedOdds()
            );

            if (!intentRegistered) {
                log.warn("{} {} Arb cancelled during intent registration: {}",
                        EMOJI_WARNING, EMOJI_SYNC, arbId);
                return false;
            }

            log.info("{} {} Intent registered for arb: {}", EMOJI_SUCCESS, EMOJI_SYNC, arbId);

            // STEP 2: NAVIGATE TO GAME
            log.info("{} {} [1/4] Navigating to game for outcome: {}",
                    EMOJI_GAME, EMOJI_NAVIGATION, task.getOutcome());

            boolean gameAvailable = MSportNavigationUtil.navigateToGame(page, task.getBetLeg());
            randomHumanDelay(800, 1500);
            MSportNavigationUtil.waitForPageReady(page);

            if (!gameAvailable) {
                log.warn("{} {} Game not available: {}", EMOJI_WARNING, EMOJI_GAME, arbId);
                syncManager.notifyBetFailure(arbId, BOOKMAKER, "Game not available");
                syncManager.skipArbAndSync(arbId);
                return false;
            }

            log.info("{} {} Game navigation successful", EMOJI_SUCCESS, EMOJI_GAME);
            randomHumanDelay(500, 1000);

            // STEP 3: SELECT AND VERIFY BET
            log.info("{} {} [2/4] Selecting and verifying bet", EMOJI_MARKET, EMOJI_CART);

            boolean selectAndVerify = MSportMarketUtil.selectAndVerifyBet(
                    page, task.getBetLeg(), arbOutcomeService);

            if (!selectAndVerify) {
                log.warn("{} {} Bet selection and verification failed", EMOJI_WARNING, EMOJI_CART);
                MSportMarketUtil.clearBetSlip(page);
                syncManager.notifyBetFailure(arbId, BOOKMAKER, "Bet selection and verification failed");
                syncManager.skipArbAndSync(arbId);
                return false;
            }

            // STEP 4: MARK DEPLOYMENT SUCCESS
            boolean markedDeployed = syncManager.markDeploymentSuccess(
                    arbId,
                    BOOKMAKER
            );

            if (!markedDeployed) {
                log.warn("{} {} Arb cancelled after deployment: {}",
                        EMOJI_WARNING, EMOJI_SYNC, arbId);
                MSportMarketUtil.clearBetSlip(page);
                return false;
            }

            log.info("{} {} Deployment marked as successful", EMOJI_SUCCESS, EMOJI_SYNC);

            // STEP 5: WAIT FOR PARTNER DEPLOYMENT
            log.info("{} {} [3/4] Waiting for partner to deploy...", EMOJI_SYNC, EMOJI_CLOCK);

            boolean partnerDeployed = syncManager.waitForPartnerDeploymentOrTimeout(
                    arbId,
                    BOOKMAKER,
                    Duration.ofSeconds(deployTimeout)
            );

            if (!partnerDeployed) {
                log.warn("{} {} Partner deployment failed or timeout", EMOJI_WARNING, EMOJI_SYNC);
                MSportMarketUtil.clearBetSlip(page);
                return false;
            }

            log.info("{} {} Both windows DEPLOYED - ready for simultaneous placement!",
                    EMOJI_SUCCESS, EMOJI_ROCKET);

            randomHumanDelay(200, 400);

            log.info("{} {} [4/4] Bet deployment completed successfully for task: {}",
                    EMOJI_SUCCESS, EMOJI_ROCKET, arbId);
            return true;

        } catch (Exception e) {
            log.error("{} {} Bet deployment failed: {}",
                    EMOJI_ERROR, EMOJI_BET, e.getMessage(), e);

            syncManager.notifyBetFailure(arbId, BOOKMAKER,
                    "Deployment exception: " + e.getMessage());
            syncManager.skipArbAndSync(arbId);

            try {
                MSportMarketUtil.clearBetSlip(page);
            } catch (Exception clearEx) {
                log.warn("{} {} Failed to clear betslip after error: {}",
                        EMOJI_WARNING, EMOJI_CART, clearEx.getMessage());
            }

            return false;
        }
    }

    // ========================================================================
    // BET EXECUTION
    // ========================================================================

    /**
     * Execute the complete bet placement workflow for a BetLegTask.
     *
     * Workflow:
     * 1. Deploy bet (with partner synchronization via WindowSyncManager)
     * 2. Place bet
     * 3. Wait for partner result & handle rollback if needed
     * 4. Signal orchestrator completion via Phaser
     *
     * CRITICAL: This method GUARANTEES that task.complete() or task.fail() is called
     * to prevent orchestrator from blocking indefinitely.
     *
     * @param page The Playwright page instance
     * @param task The BetLegTask to execute
     */
    private void executeBetLegTask(Page page, BetLegTask task) {
        String arbId = task.getTaskId();
        log.info("{} {} Executing BetLegTask | {} | MaxRetries: {}",
                EMOJI_START, EMOJI_TARGET, task.getSummary(), task.getMaxRetries());

        int attempt = 0;
        boolean success = false;
        String resultMessage = null;
        String betId = null;
        boolean phaserSignaled = false;

        try {
            while (attempt < task.getMaxRetries() && !success) {
                attempt++;

                try {
                    log.info("{} {} Attempt {}/{} for ArbId: {}",
                            EMOJI_BET, EMOJI_SYNC, attempt, task.getMaxRetries(), task.getArbId());

                    // STEP 1: DEPLOY BET TO BETSLIP (with partner sync)
                    boolean deployed = deployBet(page, task);

                    if (!deployed) {
                        resultMessage = String.format("Deployment failed on attempt %d/%d",
                                attempt, task.getMaxRetries());
                        log.warn("{} {} {}", EMOJI_WARNING, EMOJI_BET, resultMessage);

                        if (attempt < task.getMaxRetries()) {
                            long backoffMs = task.getRetryBackoff().toMillis() * attempt;
                            log.info("{} {} Retrying after {}ms...", EMOJI_CLOCK, EMOJI_SYNC, backoffMs);
                            Thread.sleep(backoffMs);
                            continue;
                        }
                        break;
                    }

                    log.info("{} {} Bet deployed successfully - ready for placement",
                            EMOJI_SUCCESS, EMOJI_ROCKET);

                    // STEP 2: PLACE THE BET (SYNCHRONIZED)
                    log.info("{} {} SIMULTANEOUS BETTING | ArbId: {} | Bookmaker: {}",
                            EMOJI_MONEY, EMOJI_BET, task.getArbId(), BOOKMAKER);

                    boolean betPlaced = false;
                    try {
                        betPlaced = MSportBetPlacement.placeBet(
                                page, task.getBetLeg(), arbOutcomeService, arbChecker);

                        betId = "BET_" + System.currentTimeMillis(); // TODO: Extract actual bet ID

                    } catch (Exception placeBetEx) {
                        log.error("{} {} Exception during bet placement: {}",
                                EMOJI_ERROR, EMOJI_MONEY, placeBetEx.getMessage(), placeBetEx);

                        resultMessage = String.format("Bet placement exception on attempt %d/%d: %s",
                                attempt, task.getMaxRetries(), placeBetEx.getMessage());

                        syncManager.notifyBetFailure(task.getTaskId(), BOOKMAKER,
                                "Placement exception: " + placeBetEx.getMessage());

                        syncManager.waitForPartnerBetCompletion(
                                task.getTaskId(), BOOKMAKER,
                                Duration.ofSeconds(betTimeoutSeconds + 5));

                        try {
                            MSportMarketUtil.clearBetSlip(page);
                        } catch (Exception clearEx) {
                            log.warn("{} {} Failed to clear betslip: {}",
                                    EMOJI_WARNING, EMOJI_CART, clearEx.getMessage());
                        }

                        if (attempt < task.getMaxRetries()) {
                            long backoffMs = task.getRetryBackoff().toMillis() * attempt;
                            log.info("{} {} Retrying after {}ms...", EMOJI_CLOCK, EMOJI_SYNC, backoffMs);
                            Thread.sleep(backoffMs);
                            continue;
                        }
                        break;
                    }

                    if (!betPlaced) {
                        resultMessage = String.format("Bet placement failed on attempt %d/%d",
                                attempt, task.getMaxRetries());
                        log.warn("{} {} {}", EMOJI_WARNING, EMOJI_MONEY, resultMessage);

                        syncManager.notifyBetFailure(task.getTaskId(), BOOKMAKER, "Placement failed");

                        syncManager.waitForPartnerBetCompletion(
                                task.getTaskId(), BOOKMAKER,
                                Duration.ofSeconds(betTimeoutSeconds + 5));

                        MSportMarketUtil.clearBetSlip(page);

                        if (attempt < task.getMaxRetries()) {
                            long backoffMs = task.getRetryBackoff().toMillis() * attempt;
                            log.info("{} {} Retrying after {}ms...", EMOJI_CLOCK, EMOJI_SYNC, backoffMs);
                            Thread.sleep(backoffMs);
                            continue;
                        }
                        break;
                    }

                    log.info("{} {} Bet PLACED | ArbId: {} | Stake: {} | Odds: {}",
                            EMOJI_SUCCESS, EMOJI_MONEY, task.getTaskId(),
                            task.getStakeAmount(), task.getExpectedOdds());

                    syncManager.notifyBetPlaced(task.getTaskId(), BOOKMAKER);

                    // Close success modal if present
                    randomHumanDelay(2000, 3000);
                    try {
                        page.locator("div.m-betslip-success button:has-text('OK')")
                                .first()
                                .click(new Locator.ClickOptions().setTimeout(2000));
                    } catch (Exception ignored) {}

                    // STEP 3: WAIT FOR PARTNER RESULT & HANDLE ROLLBACK
                    log.info("{} {} Waiting for partner to complete | ArbId: {}",
                            EMOJI_SYNC, EMOJI_CLOCK, task.getTaskId());

                    WindowSyncManager.PartnerBetResult partnerResult = syncManager.waitForPartnerBetCompletion(
                            task.getTaskId(), BOOKMAKER,
                            Duration.ofSeconds(betTimeoutSeconds + 5));

                    if (partnerResult.isSuccess()) {
                        log.info("{} {} BOTH BETS PLACED SUCCESSFULLY | ArbId: {}",
                                EMOJI_SUCCESS, EMOJI_ROCKET, task.getTaskId());

                        success = true;
                        resultMessage = String.format("Bet placed successfully | Odds: %.2f | Stake: %.2f | BetId: %s",
                                task.getExpectedOdds(), task.getStakeAmount(), betId);

                    } else {
                        log.warn("{} {} PARTNER FAILED - INITIATING ROLLBACK | ArbId: {}",
                                EMOJI_WARNING, EMOJI_SYNC, task.getTaskId());

                        syncManager.requestRollback(task.getTaskId(), BOOKMAKER,
                                "Partner failed: " + partnerResult.getMessage());

                        boolean rollbackSuccess = performRollback(page, task.getTaskId(), betId);
                        syncManager.notifyRollbackCompleted(task.getTaskId(), BOOKMAKER, rollbackSuccess);

                        if (rollbackSuccess) {
                            resultMessage = String.format("Rollback successful - partner failed: %s",
                                    partnerResult.getMessage());
                            success = false;
                        } else {
                            log.error("{} {} ROLLBACK FAILED - MANUAL INTERVENTION REQUIRED | ArbId: {}",
                                    EMOJI_ERROR, EMOJI_WARNING, task.getTaskId());
                            resultMessage = "CRITICAL: Rollback failed - manual intervention required";
                            success = false;
                        }
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    resultMessage = "Task execution interrupted";
                    log.error("{} {} {}", EMOJI_ERROR, EMOJI_WARNING, resultMessage);
                    break;

                } catch (Exception e) {
                    resultMessage = String.format("Exception on attempt %d/%d: %s",
                            attempt, task.getMaxRetries(), e.getMessage());
                    log.error("{} {} {}", EMOJI_ERROR, EMOJI_BET, resultMessage, e);

                    try {
                        MSportMarketUtil.clearBetSlip(page);
                    } catch (Exception clearEx) {
                        log.warn("{} {} Failed to clear betslip: {}",
                                EMOJI_WARNING, EMOJI_CART, clearEx.getMessage());
                    }

                    if (attempt < task.getMaxRetries()) {
                        try {
                            long backoffMs = task.getRetryBackoff().toMillis() * attempt;
                            log.info("{} {} Retrying after {}ms...", EMOJI_CLOCK, EMOJI_SYNC, backoffMs);
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            // STEP 4: COMPLETE THE TASK (SIGNAL PHASER)
            if (success) {
                task.complete(true, resultMessage);
                phaserSignaled = true;
                log.info("{} {} BetLegTask completed successfully | {} | Phase advanced",
                        EMOJI_SUCCESS, EMOJI_ROCKET, task.getSummary());
            } else {
                task.complete(false, resultMessage != null ? resultMessage : "All retry attempts exhausted");
                phaserSignaled = true;
                log.error("{} {} BetLegTask failed after {} attempts | {} | Phase advanced",
                        EMOJI_ERROR, EMOJI_BET, attempt, task.getSummary());
            }

        } catch (Throwable t) {
            // CRITICAL: Catch ALL throwables including unchecked exceptions
            log.error("{} {} CRITICAL: Unexpected throwable in executeBetLegTask: {}",
                    EMOJI_ERROR, EMOJI_WARNING, t.getMessage(), t);

            if (!phaserSignaled) {
                try {
                    task.fail(t);
                    phaserSignaled = true;
                    log.info("{} {} Emergency phaser signal sent after throwable",
                            EMOJI_WARNING, EMOJI_SYNC);
                } catch (Throwable signalError) {
                    // ABSOLUTE LAST RESORT: Force phaser advance
                    log.error("{} {} CRITICAL: Failed to signal phaser - forcing advance: {}",
                            EMOJI_ERROR, EMOJI_ERROR, signalError.getMessage());
                    try {
                        task.getBarrier().arriveAndDeregister();
                    } catch (Throwable forceError) {
                        log.error("{} {} FATAL: Could not force phaser advance - orchestrator may block!",
                                EMOJI_ERROR, EMOJI_ERROR);
                    }
                }
            }
        }

        // STEP 5: UNREGISTER INTENT & RETURN TO SPORT PAGE
        try {
            syncManager.unRegisterIntent(task.getTaskId(), BOOKMAKER);
            log.debug("{} {} Intent unregistered", EMOJI_SUCCESS, EMOJI_SYNC);
        } catch (Exception e) {
            log.warn("{} {} Failed to unregister intent: {}", EMOJI_WARNING, EMOJI_SYNC, e.getMessage());
        }

        try {
            Sport configuredSport = determineConfiguredSport();
            MSportNavigationUtil.returnToSportPage(page, configuredSport);
            MSportNavigationUtil.waitForPageReady(page);
            log.info("{} {} Returned to sport page, ready for next task",
                    EMOJI_SUCCESS, EMOJI_NAVIGATION);
        } catch (Exception e) {
            log.warn("{} {} Failed to return to sport page: {}",
                    EMOJI_WARNING, EMOJI_NAVIGATION, e.getMessage());
        }
    }

    // ========================================================================
    // ROLLBACK
    // ========================================================================

    /**
     * Perform rollback - cancel/cash out the bet.
     * Called when partner bet fails and this bet succeeded.
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

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Human-like random delay to avoid bot detection
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
     * Determine which sport to navigate to based on configuration
     */
    private Sport determineConfiguredSport() {
        if (fetchTableTennisEnabled) {
            return Sport.TABLE_TENNIS;
        } else if (fetchFootballEnabled) {
            return Sport.FOOTBALL;
        } else if (fetchBasketballEnabled) {
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

    /**
     * Get betting indicator status
     */
    public boolean isBetInProgress() {
        return isBetInProgress.get();
    }

    // ========================================================================
    // CONTEXT MANAGEMENT
    // ========================================================================

    private BrowserContext newContext(Browser browser, UserAgentProfile profile) {
        log.info("ViewPort size {} x {}", profile.getViewport().getWidth(), profile.getViewport().getHeight());
        log.info("Headers: {}", getAllHeaders(profile));

        return browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(profile.getUserAgent())
                .setJavaScriptEnabled(true)
                .setLocale("en-US")
                .setViewportSize(profile.getViewport().getWidth(), profile.getViewport().getHeight())
        );
    }

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
                        .setJavaScriptEnabled(true)
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

    @Override
    public void run() {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetryAttempts) {
            attempt++;
            log.info("{} {} Starting MSport attempt {}/{}",
                    EMOJI_INIT, EMOJI_BET, attempt, maxRetryAttempts);

            try {
                windowEntry();
                log.info("{} {} MSport completed successfully", EMOJI_SUCCESS, EMOJI_BET);
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
     * 2. Continuous loop: Poll BetLegTasks -> Execute with partner sync -> Signal orchestrator via Phaser
     * 3. Loop continues until window is stopped
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
            MSportNavigationUtil.navigateToBookmaker(page, baseUrl);
            MSportNavigationUtil.waitForPageReady(page);
            log.info("{} {} Navigation to bookmaker completed", EMOJI_SUCCESS, EMOJI_NAVIGATION);

            // Login handling
            boolean loggedIn = MSportLoginUtil.checkIfLoggedIn(page);
            if (!loggedIn) {
                log.info("{} {} User not logged in, attempting login...", EMOJI_INFO, EMOJI_LOGIN);
                MSportLoginUtil.performLogin(page, msportUsername, msportPassword);
                MSportNavigationUtil.waitForPageReady(page);
                if (!MSportLoginUtil.checkIfLoggedIn(page)) {
                    throw new RuntimeException("Login verification failed");
                }
                isLoggedIn.set(true);
                log.info("{} {} Login successful", EMOJI_SUCCESS, EMOJI_LOGIN);
            } else {
                isLoggedIn.set(true);
                log.info("{} {} User already logged in", EMOJI_SUCCESS, EMOJI_LOGIN);
            }

            // Navigate to live events → sport page
            MSportNavigationUtil.navigateToLiveEvents(page);
            MSportNavigationUtil.waitForPageReady(page);

            Sport configuredSport = determineConfiguredSport();
            MSportNavigationUtil.navigateToSportPage(page, configuredSport);
            MSportNavigationUtil.waitForPageReady(page);
            log.info("{} {} Navigation to {} page completed", EMOJI_SUCCESS, EMOJI_TARGET, configuredSport);

            isWindowUpAndRunning.set(true);
            log.info("{} {} Window is now up and running - entering betting loop", EMOJI_SUCCESS, EMOJI_ROCKET);

            // ===== PHASE 2: BETTING LOOP =====
            int consecutiveFailures = 0;
            final int maxConsecutiveFailures = 5;

            while (isRunning.get() && !isPaused.get()) {
                BetLegTask task = null;

                try {
                    randomHumanDelay(1000, 2500);

                    // Poll for BetLegTask from orchestrator queue
                    task = pollTaskFromDispatcher();

                    if (task == null) {
                        // No task available, continue polling
                        continue;
                    }

                    // Mark bet in progress
                    isBetInProgress.set(true);

                    // Execute the complete BetLegTask workflow
                    // NOTE: executeBetLegTask() GUARANTEES phaser signaling internally
                    // - Deploy with WindowSyncManager partner coordination
                    // - Place bet
                    // - Handle rollback if partner fails
                    // - Signal orchestrator via Phaser (task.complete())
                    executeBetLegTask(page, task);

                    // Reset consecutive failures on successful task processing
                    consecutiveFailures = 0;

                } catch (Throwable t) {
                    // CRITICAL: Catch Throwable to handle ALL errors including OutOfMemoryError
                    log.error("{} {} CRITICAL error in betting loop: {}",
                            EMOJI_ERROR, EMOJI_WARNING, t.getMessage(), t);

                    // NOTE: We do NOT call task.fail() here because executeBetLegTask()
                    // already guarantees phaser signaling in its own catch block

                    consecutiveFailures++;

                } finally {
                    isBetInProgress.set(false);
                }

                // Recovery from too many consecutive failures
                if (consecutiveFailures >= maxConsecutiveFailures) {
                    log.warn("{} {} Too many consecutive failures ({}), attempting recovery...",
                            EMOJI_WARNING, EMOJI_SYNC, consecutiveFailures);
                    try {
                        MSportMarketUtil.clearBetSlip(page);
                        configuredSport = determineConfiguredSport();
                        MSportNavigationUtil.returnToSportPage(page, configuredSport);
                        MSportNavigationUtil.waitForPageReady(page);
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

    // ========================================================================
    // INTERFACE IMPLEMENTATIONS
    // ========================================================================

    @Override
    public void pause() {
        log.info("{} {} Pausing MSport window...", EMOJI_WARNING, EMOJI_CLOCK);
        isPaused.set(true);
    }

    public boolean isWindowUpAndRunning() {
        return !isPaused.get() && isWindowUpAndRunning.get();
    }

    @Override
    public void resume() {
        log.info("{} {} Resuming MSport window...", EMOJI_SUCCESS, EMOJI_START);
        isPaused.set(false);
    }

    @Override
    public void stop() {
        log.info("{} {} Stopping MSport window...", EMOJI_SHUTDOWN, EMOJI_WARNING);
        isRunning.set(false);
        isWindowUpAndRunning.set(false);
        isBetInProgress.set(false);
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public boolean isPaused() {
        return isPaused.get();
    }

    @Override
    public void shutdown() {
        log.info("{} {} Shutting down MSport window...", EMOJI_SHUTDOWN, EMOJI_TRASH);

        try {
            stop();

            if (healthMonitor != null) {
                healthMonitor.stop();
            }

            if (currentContext != null) {
                saveContext(currentContext);
                currentContext.close();
            }

            if (browser != null) {
                browser.close();
            }

            if (playwright != null) {
                playwright.close();
            }

            log.info("{} {} Shutdown completed successfully", EMOJI_SUCCESS, EMOJI_SHUTDOWN);
        } catch (Exception e) {
            log.error("{} {} Error during shutdown: {}", EMOJI_ERROR, EMOJI_SHUTDOWN, e.getMessage(), e);
        }
    }
}