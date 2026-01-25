//package com.mouse.bet.window;
//
//import com.mouse.bet.enums.BookMaker;
//import com.mouse.bet.interfaces.BettingWindow;
//import com.mouse.bet.manager.WindowSyncManager;
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import lombok.Getter;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicBoolean;
//
///**
// * Manages parallel execution of MSport and Sporty betting windows
// * Ensures proper synchronization and graceful shutdown
// */
//@Slf4j
//@Component
//@Getter
//@RequiredArgsConstructor
//public class Player {
//
//    private static final String EMOJI_INIT = "🚀";
//    private static final String EMOJI_SUCCESS = "✅";
//    private static final String EMOJI_ERROR = "❌";
//    private static final String EMOJI_WARNING = "⚠️";
//    private static final String EMOJI_SHUTDOWN = "🛑";
//    private static final String EMOJI_WINDOW = "🪟";
//
//    private final MSport mSportWindow;
//    private final SportyBet sportyWindow;
//    private final OneWin oneWinWindow;
//    private final WindowSyncManager syncManager;
//
//    private ExecutorService executorService;
//    private final List<Future<?>> runningTasks = new CopyOnWriteArrayList<>();
//    private final AtomicBoolean isRunning = new AtomicBoolean(false);
//    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
//
//    @Value("${window.player.shutdown.timeout.seconds:30}")
//    private int shutdownTimeoutSeconds;
//
//    @Value("${window.player.auto.start:true}")
//    private boolean autoStart;
//    private boolean oneWinRunning;
//    private boolean sportyRunning;
//    private boolean mSportRunning;
//
//    /**
//     * Initialize the executor service
//     */
//    @PostConstruct
//    public void init() {
//        log.info("{} {} Initializing WindowPlayer...", EMOJI_INIT, EMOJI_WINDOW);
//
//        // Create thread pool with 2 threads (one for each window)
//        executorService = Executors.newFixedThreadPool(3, new ThreadFactory() {
//            private int counter = 0;
//
//            @Override
//            public Thread newThread(Runnable r) {
//                Thread thread = new Thread(r);
//                thread.setName("BettingWindow-" + (++counter));
//                thread.setDaemon(false); // Non-daemon to keep app alive
//                thread.setUncaughtExceptionHandler((t, e) -> {
//                    log.error("{} {} Uncaught exception in thread {}: {}",
//                            EMOJI_ERROR, EMOJI_WINDOW, t.getName(), e.getMessage(), e);
//                    handleWindowCrash(t.getName(), e);
//                });
//                return thread;
//            }
//        });
//
//        log.info("{} {} WindowPlayer initialized with 2-thread executor",
//                EMOJI_SUCCESS, EMOJI_WINDOW);
//
//        if (autoStart) {
//            startWindows();
//        }
//    }
//
//    /**
//     * Start both betting windows in parallel
//     */
//    public synchronized void startWindows() {
//        if (isRunning.get()) {
//            log.warn("{} {} Windows are already running", EMOJI_WARNING, EMOJI_WINDOW);
//            return;
//        }
//
//        if (isShuttingDown.get()) {
//            log.warn("{} {} Cannot start - shutdown in progress", EMOJI_WARNING, EMOJI_SHUTDOWN);
//            return;
//        }
//
//        log.info("{} {} Starting both betting windows in parallel...",
//                EMOJI_INIT, EMOJI_WINDOW);
//
//        isRunning.set(true);
//
//        try {
//
//            // Submit MSport window
//            runningTasks.add(bookMakerFuture(mSportWindow, BookMaker.MSPORT));
//            log.info("{} window submitted to executor", BookMaker.MSPORT);
//
//            // Small delay to stagger startup
//            Thread.sleep(30000);
//            runningTasks.add(bookMakerFuture(sportyWindow, BookMaker.SPORTYBET));
//            log.info("{} window submitted to executor", BookMaker.SPORTYBET);
//
//            Thread.sleep(30000);
//
//            runningTasks.add(bookMakerFuture(oneWinWindow, BookMaker._1WIN));
//            log.info("{} window submitted to executor", BookMaker._1WIN);
//
//
//            log.info("{} {} Both windows started successfully", EMOJI_SUCCESS, EMOJI_WINDOW);
//
//            // Monitor windows in background
//            monitorWindows();
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to start windows: {}",
//                    EMOJI_ERROR, EMOJI_WINDOW, e.getMessage(), e);
//            isRunning.set(false);
//            throw new RuntimeException("Failed to start betting windows", e);
//        }
//    }
//
//
//    private Future<?> bookMakerFuture(BettingWindow window, BookMaker bookMaker) {
//        return executorService.submit(() -> {
//            try {
//                log.info("{} {} window thread started-", EMOJI_SUCCESS, bookMaker);
//                window.run();
//                log.info("{} {} window thread completed-", EMOJI_SUCCESS, bookMaker);
//            } catch (Exception e) {
//                log.error("{} {} window crashed: {}",
//                        EMOJI_ERROR, bookMaker, e.getMessage(), e);
//                throw new RuntimeException("Sporty window failed", e);
//            }
//        });
//    }
//
//    /**
//     * Monitor running windows and handle failures
//     */
//    private void monitorWindows() {
//
////        Thread.sleep(30000);
//        CompletableFuture.runAsync(() -> {
//            log.info("🔍 Window monitor started");
//
//            while (isRunning.get() && !isShuttingDown.get()) {
//                try {
//                    Thread.sleep(5000); // Check every 5 seconds
//
//                    // Check if MSport window is up and running
//                    mSportRunning = mSportWindow.isWindowUpAndRunning();
//                    if (!mSportRunning) {
//                        log.error("{} {} {} window is NOT running!",
//                                EMOJI_ERROR, EMOJI_WARNING, BookMaker.MSPORT);
//                    } else {
//                        log.debug("✅ {} window is running", BookMaker.MSPORT);
//                    }
//
//                    // Check if Sporty window is up and running
//                    sportyRunning = sportyWindow.isWindowUpAndRunning();
//                    if (!sportyRunning) {
//                        log.error("{} {} {} window is NOT running!",
//                                EMOJI_ERROR, EMOJI_WARNING, BookMaker.SPORTYBET);
//                    } else {
//                        log.debug("✅ {} window is running", BookMaker.SPORTYBET);
//                    }
//
//                    // Check if Sporty window is up and running
//                    oneWinRunning = oneWinWindow.isWindowUpAndRunning();
//                    if (!oneWinRunning) {
//                        log.error("{} {} {} window is NOT running!",
//                                EMOJI_ERROR, EMOJI_WARNING, BookMaker._1WIN);
//                    } else {
//                        log.debug("✅ {} window is running", BookMaker._1WIN);
//                    }
//
//                    // Log status summary
////                    if (mSportRunning && sportyRunning) {
////                        log.info("📊 Window status: Both windows are UP and RUNNING");
////                    } else if (!mSportRunning && !sportyRunning) {
////                        log.error("{} {} CRITICAL: Both windows are DOWN - stopping player",
////                                EMOJI_ERROR, EMOJI_SHUTDOWN);
////                        stopWindows();
////                        break;
////                    } else if (!mSportRunning) {
////                        log.warn("{} {} {} window is DOWN, {} window is running",
////                                EMOJI_WARNING, EMOJI_WINDOW, BookMaker.M_SPORT, BookMaker.SPORTY_BET);
////                    } else {
////                        log.warn("{} {} {} window is DOWN, {} window is running",
////                                EMOJI_WARNING, EMOJI_WINDOW, BookMaker.SPORTY_BET, BookMaker.M_SPORT);
////                    }
//
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    log.info("🛑 Window monitor interrupted");
//                    break;
//                } catch (Exception e) {
//                    log.error("❌ Error in window monitor: {}", e.getMessage());
//                }
//            }
//
//            log.info("🏁 Window monitor stopped");
//        });
//    }
//
//    /**
//     * Handle window crash
//     */
//    private void handleWindowCrash(String threadName, Throwable error) {
//        log.error("{} {} CRITICAL: Window crashed | Thread: {} | Error: {}",
//                EMOJI_ERROR, EMOJI_WARNING, threadName, error.getMessage(), error);
//
//        // Optionally: trigger alerts, restart logic, etc.
//        // For now, just log the crash
//
//        // Check if we should stop everything
//        int failedCount = 0;
//        for (Future<?> task : runningTasks) {
//            if (task.isDone()) {
//                try {
//                    task.get();
//                } catch (Exception e) {
//                    failedCount++;
//                }
//            }
//        }
//
//        if (failedCount >= 2) {
//            log.error("{} {} Both windows have failed - stopping player",
//                    EMOJI_ERROR, EMOJI_SHUTDOWN);
//            stopWindows();
//        }
//    }
//
//    /**
//     * Stop both betting windows gracefully
//     */
//    public synchronized void stopWindows() {
//        if (!isRunning.get()) {
//            log.info("{} {} Windows are not running", EMOJI_WARNING, EMOJI_WINDOW);
//            return;
//        }
//
//        if (isShuttingDown.get()) {
//            log.warn("{} {} Shutdown already in progress", EMOJI_WARNING, EMOJI_SHUTDOWN);
//            return;
//        }
//
//        log.info("{} {} Stopping betting windows gracefully...",
//                EMOJI_SHUTDOWN, EMOJI_WINDOW);
//
//        isShuttingDown.set(true);
//
//        try {
//            // Signal windows to stop
//            mSportWindow.stop();
//            sportyWindow.stop();
//
//            log.info("⏳ Waiting for windows to complete current operations...");
//
//            // Wait for tasks to complete with timeout
//            boolean allCompleted = true;
//            for (Future<?> task : runningTasks) {
//                try {
//                    task.get(shutdownTimeoutSeconds, TimeUnit.SECONDS);
//                    log.info("✅ Window task completed gracefully");
//                } catch (TimeoutException e) {
//                    log.warn("⏱️ Window task did not complete in time - forcing shutdown");
//                    task.cancel(true);
//                    allCompleted = false;
//                } catch (Exception e) {
//                    log.error("❌ Error waiting for task: {}", e.getMessage());
//                    allCompleted = false;
//                }
//            }
//
//            if (allCompleted) {
//                log.info("{} {} All windows stopped gracefully", EMOJI_SUCCESS, EMOJI_SHUTDOWN);
//            } else {
//                log.warn("{} {} Some windows were forcefully stopped", EMOJI_WARNING, EMOJI_SHUTDOWN);
//            }
//
//            // Clear sync manager state
//            syncManager.clearAll();
//
//        } catch (Exception e) {
//            log.error("{} {} Error during window shutdown: {}",
//                    EMOJI_ERROR, EMOJI_SHUTDOWN, e.getMessage(), e);
//        } finally {
//            runningTasks.clear();
//            isRunning.set(false);
//            isShuttingDown.set(false);
//        }
//    }
//
//    /**
//     * Restart both windows
//     */
//    public synchronized void restartWindows() {
//        log.info("{} {} Restarting betting windows...", EMOJI_INIT, EMOJI_WINDOW);
//
//        stopWindows();
//
//        // Wait a bit before restarting
//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//
//        startWindows();
//    }
//
//    /**
//     * Pause both windows
//     */
//    public void pauseWindows() {
//        log.info("⏸️ Pausing both windows...");
//        mSportWindow.pause();
//        sportyWindow.pause();
//        log.info("⏸️ {} Both windows paused", EMOJI_SUCCESS);
//    }
//
//    /**
//     * Resume both windows
//     */
//    public void resumeWindows() {
//        log.info("▶️ Resuming both windows...");
//        mSportWindow.resume();
//        sportyWindow.resume();
//        log.info("▶️ {} Both windows resumed", EMOJI_SUCCESS);
//    }
//
//    /**
//     * Get player status
//     */
//    public PlayerStatus getStatus() {
//        return PlayerStatus.builder()
//                .isRunning(isRunning.get())
//                .isShuttingDown(isShuttingDown.get())
//                .activeWindows(runningTasks.size())
//                .mSportWindowActive(!runningTasks.isEmpty() && !runningTasks.get(0).isDone())
//                .sportyWindowActive(runningTasks.size() > 1 && !runningTasks.get(1).isDone())
//                .syncManagerRegistered(syncManager.getRegisteredWindowCount())
//                .activeCoordinations(syncManager.getActiveCoordinationCount())
//                .build();
//    }
//
//    /**
//     * Cleanup on application shutdown
//     */
//    @PreDestroy
//    public void destroy() {
//        log.info("{} {} Shutting down WindowPlayer...", EMOJI_SHUTDOWN, EMOJI_WINDOW);
//
//        stopWindows();
//
//        // Shutdown executor service
//        if (executorService != null && !executorService.isShutdown()) {
//            log.info("🔌 Shutting down executor service...");
//            executorService.shutdown();
//
//            try {
//                if (!executorService.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
//                    log.warn("⏱️ Executor did not terminate in time - forcing shutdown");
//                    List<Runnable> droppedTasks = executorService.shutdownNow();
//                    log.warn("⚠️ {} tasks were not executed", droppedTasks.size());
//                }
//            } catch (InterruptedException e) {
//                log.warn("🛑 Interrupted while waiting for executor shutdown");
//                executorService.shutdownNow();
//                Thread.currentThread().interrupt();
//            }
//        }
//
//        log.info("{} {} WindowPlayer shutdown complete", EMOJI_SUCCESS, EMOJI_SHUTDOWN);
//    }
//
//    /**
//     * Status data class
//     */
//    @lombok.Builder
//    @lombok.Data
//    public static class PlayerStatus {
//        private boolean isRunning;
//        private boolean isShuttingDown;
//        private int activeWindows;
//        private boolean mSportWindowActive;
//        private boolean sportyWindowActive;
//        private int syncManagerRegistered;
//        private int activeCoordinations;
//    }
//}