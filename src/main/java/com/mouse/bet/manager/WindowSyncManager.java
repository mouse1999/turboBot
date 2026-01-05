package com.mouse.bet.manager;

import com.mouse.bet.enums.BookMaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class WindowSyncManager {

    // Tracks which windows have signaled "ready" for a specific arbId
    private final Map<String, ArbSyncState> syncMap = new ConcurrentHashMap<>();

    // Registered windows - can be any bookmaker pair
    private final Map<BookMaker, Object> registeredWindows = new ConcurrentHashMap<>();

    /**
     * Register a window with the sync manager
     */
    public synchronized void registerWindow(BookMaker bookmaker, Object window) {
        registeredWindows.put(bookmaker, window);
        log.info("Window registered: {} ({})", bookmaker, window.getClass().getSimpleName());
    }

    /**
     * Get partner bookmaker for a given arbitrage
     * Dynamically determines partner from the arb's registered intents
     */
    private BookMaker getPartner(String arbId, BookMaker myBookmaker) {
        ArbSyncState state = syncMap.get(arbId);
        if (state == null) {
            log.warn("No sync state found for arbId: {}", arbId);
            return null;
        }

        Set<BookMaker> participants = state.getIntentRegistered();

        // Find the other bookmaker
        for (BookMaker bookmaker : participants) {
            if (bookmaker != myBookmaker) {
                return bookmaker;
            }
        }

        log.warn("Could not find partner for {} in arbId: {}", myBookmaker, arbId);
        return null;
    }

    /**
     * Register intent to participate in this arb (called BEFORE navigation)
     * Returns false if arb is already cancelled
     */
    public boolean registerIntent(String arbId, BookMaker bookmaker, double odds) {
        ArbSyncState state = syncMap.computeIfAbsent(arbId, k -> new ArbSyncState());

        if (state.isCancelled()) {
            log.warn("⚠️ Cannot register intent - arb already cancelled | ArbId: {} | Bookmaker: {}",
                    arbId, bookmaker);
            return false;
        }

        state.registerIntent(bookmaker, odds);
        log.info("✓ Intent registered | ArbId: {} | Bookmaker: {} | Odds: {}", arbId, bookmaker, odds);
        return true;
    }

    /**
     * Unregister intent (cleanup)
     */
    public void unRegisterIntent(String arbId, BookMaker bookmaker) {
        ArbSyncState state = syncMap.get(arbId);
        if (state != null) {
            state.unRegisterIntent(bookmaker);
            log.info("Intent unregistered | ArbId: {} | Bookmaker: {}", arbId, bookmaker);
        }
    }

    /**
     * Mark that bet deployment succeeded for this bookmaker
     * Returns false if arb is already cancelled
     */
    public boolean markDeploymentSuccess(String arbId, BookMaker bookmaker) {
        ArbSyncState state = syncMap.computeIfAbsent(arbId, k -> new ArbSyncState());

        if (state.isCancelled()) {
            log.warn("⚠️ Cannot mark deployment - arb already cancelled | ArbId: {} | Bookmaker: {}",
                    arbId, bookmaker);
            return false;
        }

        state.markDeploymentSuccess(bookmaker);
        log.info("✓ Bet DEPLOYED | ArbId: {} | Bookmaker: {}", arbId, bookmaker);
        return true;
    }

    /**
     * Wait for partner's deployment to complete with timeout
     * Returns true only if BOTH windows successfully deployed
     */
    public boolean waitForPartnerDeploymentOrTimeout(String arbId, BookMaker myBookmaker, Duration timeout) {
        ArbSyncState state = syncMap.computeIfAbsent(arbId, k -> new ArbSyncState());

        if (state.isCancelled()) {
            log.warn("⚠️ Arb already cancelled, not waiting for deployment | ArbId: {} | Bookmaker: {}",
                    arbId, myBookmaker);
            return false;
        }

        BookMaker partner = getPartner(arbId, myBookmaker);
        if (partner == null) {
            log.error("❌ Could not determine partner bookmaker | ArbId: {} | My bookmaker: {}",
                    arbId, myBookmaker);
            skipArbAndSync(arbId);
            return false;
        }

        if (!state.isDeployed(myBookmaker)) {
            log.error("❌ BUG: Waiting for deployment but {} never marked deployed! | ArbId: {}", myBookmaker, arbId);
            skipArbAndSync(arbId);
            return false;
        }

        log.info("⏳ Waiting for partner {} deployment | ArbId: {} | Timeout: {}s | My bookmaker: {}",
                partner, arbId, timeout.toSeconds(), myBookmaker);

        long startTime = System.currentTimeMillis();

        try {
            boolean awaitResult = state.deploymentLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            long elapsedTime = System.currentTimeMillis() - startTime;

            if (state.isCancelled()) {
                log.warn("⚠️ Arb cancelled while waiting for deployment | ArbId: {} | Elapsed: {}ms | Bookmaker: {}",
                        arbId, elapsedTime, myBookmaker);
                return false;
            }

            if (awaitResult) {
                if (state.areBothDeployed()) {
                    log.info("✅ Both windows DEPLOYED successfully | ArbId: {} | Elapsed: {}ms | Bookmakers: {}",
                            arbId, elapsedTime, state.getDeployedWindows());
                    return true;
                } else {
                    log.error("⚠️ Deployment latch completed but not both deployed | ArbId: {} | Deployed: {}",
                            arbId, state.getDeployedWindows());
                    skipArbAndSync(arbId);
                    return false;
                }
            } else {
                log.warn("⏱️ Timeout waiting for partner {} deployment | ArbId: {} | Elapsed: {}ms | Bookmaker: {}",
                        partner, arbId, elapsedTime, myBookmaker);

                if (state.trySetDeploymentTimeoutFlag(myBookmaker)) {
                    log.info("🚫 {} triggered deployment timeout - skipping arb | ArbId: {}",
                            myBookmaker, arbId);
                    skipArbAndSync(arbId);
                } else {
                    log.info("⏭️ Partner already triggered deployment timeout - following skip | ArbId: {}", arbId);
                }

                return false;
            }
        } catch (InterruptedException e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            Thread.currentThread().interrupt();
            log.warn("🛑 Interrupted while waiting for partner deployment | ArbId: {} | Elapsed: {}ms | Bookmaker: {}",
                    arbId, elapsedTime, myBookmaker);

            if (state.trySetDeploymentTimeoutFlag(myBookmaker)) {
                skipArbAndSync(arbId);
            }

            return false;
        }
    }

    /**
     * Called by a window when it's ready to place bet for a specific arb
     * Returns false if arb is already cancelled
     */
    public boolean markReady(String arbId, BookMaker bookmaker) {
        ArbSyncState state = syncMap.computeIfAbsent(arbId, k -> new ArbSyncState());

        if (state.isCancelled()) {
            log.warn("⚠️ Cannot mark ready - arb already cancelled by partner | ArbId: {} | Bookmaker: {}",
                    arbId, bookmaker);
            return false;
        }

        state.markReady(bookmaker);
        log.info("✓ Window READY | ArbId: {} | Bookmaker: {}", arbId, bookmaker);
        return true;
    }

    /**
     * Wait for partner with automatic skip on timeout
     * Returns true only if BOTH windows are ready and synchronized
     */
    public boolean waitForPartnersReadyOrTimeout(String arbId, BookMaker myBookmaker, Duration timeout) {
        ArbSyncState state = syncMap.computeIfAbsent(arbId, k -> new ArbSyncState());

        if (state.isCancelled()) {
            log.warn("⚠️ Arb already cancelled by partner, not waiting | ArbId: {} | Bookmaker: {}",
                    arbId, myBookmaker);
            return false;
        }

        BookMaker partner = getPartner(arbId, myBookmaker);
        if (partner == null) {
            log.error("❌ Could not determine partner bookmaker | ArbId: {} | My bookmaker: {}",
                    arbId, myBookmaker);
            skipArbAndSync(arbId);
            return false;
        }

        if (!state.isReady(myBookmaker)) {
            log.error("❌ BUG: Waiting but {} never marked ready! | ArbId: {}", myBookmaker, arbId);
            skipArbAndSync(arbId);
            return false;
        }

        log.info("⏳ Waiting for partner {} to be ready | ArbId: {} | Timeout: {}s | My bookmaker: {}",
                partner, arbId, timeout.toSeconds(), myBookmaker);

        long startTime = System.currentTimeMillis();

        try {
            boolean awaitResult = state.latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            long elapsedTime = System.currentTimeMillis() - startTime;

            if (state.isCancelled()) {
                log.warn("⚠️ Arb was cancelled while waiting | ArbId: {} | Elapsed: {}ms | Bookmaker: {}",
                        arbId, elapsedTime, myBookmaker);
                return false;
            }

            if (awaitResult) {
                if (state.areBothReady()) {
                    log.info("✅ Both windows READY and synchronized - SIMULTANEOUS BETTING | ArbId: {} | Elapsed: {}ms | Bookmakers: {}",
                            arbId, elapsedTime, state.getReadyWindows());
                    return true;
                } else {
                    log.error("⚠️ Latch completed but not both ready (race condition?) | ArbId: {} | Ready: {}",
                            arbId, state.getReadyWindows());
                    skipArbAndSync(arbId);
                    return false;
                }
            } else {
                log.warn("⏱️ Timeout waiting for partner {} | ArbId: {} | Elapsed: {}ms | Bookmaker: {}",
                        partner, arbId, elapsedTime, myBookmaker);

                if (state.trySetTimeoutFlag(myBookmaker)) {
                    log.info("🚫 {} triggered timeout - skipping arb for both windows | ArbId: {}",
                            myBookmaker, arbId);
                    skipArbAndSync(arbId);
                } else {
                    log.info("⏭️ Partner already triggered timeout - following skip | ArbId: {}", arbId);
                }

                return false;
            }
        } catch (InterruptedException e) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            Thread.currentThread().interrupt();
            log.warn("🛑 Interrupted while waiting for partner | ArbId: {} | Elapsed: {}ms | Bookmaker: {}",
                    arbId, elapsedTime, myBookmaker);

            if (state.trySetTimeoutFlag(myBookmaker)) {
                skipArbAndSync(arbId);
            }

            return false;
        }
    }

    /**
     * Notify that this bookmaker successfully placed the bet
     * This triggers a check to see if partner also succeeded
     */
    public void notifyBetPlaced(String arbId, BookMaker bookmaker) {
        ArbSyncState state = syncMap.get(arbId);
        if (state != null) {
            state.recordBetPlaced(bookmaker);
            log.info("✅ Bet PLACED and recorded | ArbId: {} | Bookmaker: {}", arbId, bookmaker);

            // Count down completion latch
            state.betCompletionLatch.countDown();
        }
        cleanupIfDone(arbId);
    }

    /**
     * Notify that bet placement failed
     * This triggers a check to see if partner succeeded (rollback needed)
     */
    public void notifyBetFailure(String arbId, BookMaker bookmaker, String reason) {
        ArbSyncState state = syncMap.get(arbId);
        if (state != null) {
            state.recordFailure(bookmaker, reason);
            log.warn("❌ Bet FAILED | ArbId: {} | Bookmaker: {} | Reason: {}", arbId, bookmaker, reason);

            // Count down completion latch
            state.betCompletionLatch.countDown();
        }
        cleanupIfDone(arbId);
    }

    /**
     * Wait for partner to complete their bet (success or failure)
     * Returns the partner's result
     */
    public PartnerBetResult waitForPartnerBetCompletion(String arbId, BookMaker myBookmaker, Duration timeout) {
        ArbSyncState state = syncMap.get(arbId);
        if (state == null) {
            log.error("❌ No sync state found | ArbId: {}", arbId);
            return PartnerBetResult.error("No sync state");
        }

        if (state.isCancelled()) {
            log.warn("⚠️ Arb cancelled, not waiting for partner | ArbId: {}", arbId);
            return PartnerBetResult.cancelled("Arb cancelled");
        }

        BookMaker partner = getPartner(arbId, myBookmaker);
        if (partner == null) {
            log.error("❌ Could not determine partner bookmaker | ArbId: {}", arbId);
            return PartnerBetResult.error("Unknown partner");
        }

        log.info("⏳ {} waiting for partner {} to complete bet | ArbId: {} | Timeout: {}s",
                myBookmaker, partner, arbId, timeout.toSeconds());

        long startTime = System.currentTimeMillis();

        try {
            boolean completed = state.betCompletionLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (!completed) {
                log.warn("⏱️ Timeout waiting for partner {} | ArbId: {} | Elapsed: {}ms",
                        partner, arbId, elapsed);
                return PartnerBetResult.timeout("Partner bet timeout");
            }

            if (state.isCancelled()) {
                log.warn("⚠️ Arb cancelled while waiting for partner | ArbId: {}", arbId);
                return PartnerBetResult.cancelled("Arb cancelled");
            }

            if (state.hasPlacedBet(partner)) {
                log.info("✅ Partner {} successfully placed bet | ArbId: {} | Elapsed: {}ms",
                        partner, arbId, elapsed);
                return PartnerBetResult.success();
            } else {
                String failureReason = state.getFailureReason(partner);
                log.warn("❌ Partner {} failed to place bet | ArbId: {} | Reason: {} | Elapsed: {}ms",
                        partner, arbId, failureReason, elapsed);
                return PartnerBetResult.failure(failureReason);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("🛑 Interrupted waiting for partner | ArbId: {} | Bookmaker: {}", arbId, myBookmaker);
            return PartnerBetResult.error("Interrupted");
        }
    }

    /**
     * Check if both bookmakers successfully placed their bets
     */
    public boolean areBothBetsPlaced(String arbId) {
        ArbSyncState state = syncMap.get(arbId);
        if (state == null) return false;

        Set<BookMaker> participants = state.getIntentRegistered();
        if (participants.size() != 2) return false;

        for (BookMaker bookmaker : participants) {
            if (!state.hasPlacedBet(bookmaker)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if both bookmakers successfully deployed their bets
     */
    public boolean areBothDeployed(String arbId) {
        ArbSyncState state = syncMap.get(arbId);
        if (state == null) return false;

        Set<BookMaker> participants = state.getIntentRegistered();
        if (participants.size() != 2) return false;

        for (BookMaker bookmaker : participants) {
            if (!state.isDeployed(bookmaker)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if exactly one bookmaker placed bet (rollback scenario)
     */
    public boolean needsRollback(String arbId, BookMaker bookmaker) {
        ArbSyncState state = syncMap.get(arbId);
        if (state == null) return false;

        BookMaker partner = getPartner(arbId, bookmaker);
        if (partner == null) return false;

        // Rollback needed if I succeeded but partner failed
        return state.hasPlacedBet(bookmaker) && !state.hasPlacedBet(partner);
    }

    /**
     * Request rollback - called by a window if they succeeded but partner failed
     */
    public boolean requestRollback(String arbId, BookMaker requestor, String reason) {
        ArbSyncState state = syncMap.get(arbId);
        if (state == null) {
            log.warn("⚠️ No sync state for rollback request | ArbId: {}", arbId);
            return false;
        }

        log.warn("🔄 ROLLBACK REQUESTED | ArbId: {} | Requestor: {} | Reason: {}", arbId, requestor, reason);

        state.initiateRollback(requestor, reason);
        return true;
    }

    /**
     * Get rollback reason
     */
    public String getRollbackReason(String arbId) {
        ArbSyncState state = syncMap.get(arbId);
        return state != null ? state.getRollbackReason() : null;
    }

    /**
     * Notify that rollback completed for a bookmaker
     */
    public void notifyRollbackCompleted(String arbId, BookMaker bookmaker, boolean success) {
        ArbSyncState state = syncMap.get(arbId);
        if (state != null) {
            state.recordRollbackCompleted(bookmaker, success);
            log.info("🔄 Rollback {} for {} | ArbId: {}",
                    success ? "SUCCEEDED" : "FAILED", bookmaker, arbId);
        }
        cleanupIfDone(arbId);
    }

    /**
     * Skip this arb and cancel for both windows
     */
    public void skipArbAndSync(String arbId) {
        ArbSyncState state = syncMap.get(arbId);
        if (state != null) {
            state.cancel();
            log.info("⏭️ Arb cancelled and synced - all windows will skip | ArbId: {} | Participants: {}",
                    arbId, state.getIntentRegistered());
        } else {
            log.warn("⚠️ Attempted to skip non-existent arb state | ArbId: {}", arbId);
        }
        scheduledCleanup(arbId, 5000);
    }

    /**
     * Check if arb was cancelled/skipped
     */
    public boolean wasArbCancelled(String arbId) {
        ArbSyncState state = syncMap.get(arbId);
        return state != null && state.isCancelled();
    }

    /**
     * Check if the partner has already placed their bet
     */
    public boolean hasPartnerPlacedBet(String arbId, BookMaker myBookmaker) {
        ArbSyncState state = syncMap.get(arbId);
        if (state == null) return false;

        BookMaker partner = getPartner(arbId, myBookmaker);
        if (partner == null) return false;

        return state.hasPlacedBet(partner);
    }

    /**
     * Check if the partner has deployed their bet
     */
    public boolean hasPartnerDeployed(String arbId, BookMaker myBookmaker) {
        ArbSyncState state = syncMap.get(arbId);
        if (state == null) return false;

        BookMaker partner = getPartner(arbId, myBookmaker);
        if (partner == null) return false;

        return state.isDeployed(partner);
    }

    /**
     * Get detailed sync status for debugging
     */
    public String getSyncStatus(String arbId) {
        ArbSyncState state = syncMap.get(arbId);
        if (state == null) {
            return "No sync state";
        }
        return state.getStateSummary();
    }

    private void scheduledCleanup(String arbId, long delayMs) {
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    syncMap.remove(arbId);
                    log.debug("🧹 Delayed cleanup completed | ArbId: {}", arbId);
                });
    }

    private void cleanupIfDone(String arbId) {
        ArbSyncState state = syncMap.get(arbId);
        if (state != null && state.isCompleted()) {
            syncMap.remove(arbId);
            log.debug("🧹 Cleaned up sync state for ArbId: {}", arbId);
        }
    }

    public synchronized void unregisterWindow(BookMaker bookmaker) {
        registeredWindows.remove(bookmaker);
        log.info("Window unregistered: {}", bookmaker);
    }

    public void clearPendingForBookmaker(BookMaker bookmaker) {
        int cleared = 0;
        for (Map.Entry<String, ArbSyncState> entry : syncMap.entrySet()) {
            ArbSyncState state = entry.getValue();
            if (state.hasIntent(bookmaker) && !state.isCompleted()) {
                skipArbAndSync(entry.getKey());
                cleared++;
            }
        }
        log.info("Cleared {} pending sync states for {}", cleared, bookmaker);
    }

    public void clearAll() {
        syncMap.clear();
        log.info("🧹 WindowSyncManager state cleared");
    }

    public int getActiveCoordinationCount() {
        return syncMap.size();
    }

    public int getRegisteredWindowCount() {
        return registeredWindows.size();
    }

    /**
     * Get all participants in an arbitrage
     */
    public Set<BookMaker> getParticipants(String arbId) {
        ArbSyncState state = syncMap.get(arbId);
        return state != null ? state.getIntentRegistered() : Collections.emptySet();
    }

    // =================================================================
    // Supporting Classes
    // =================================================================

    public static class PartnerBetResult {
        private final ResultType type;
        private final String message;

        private PartnerBetResult(ResultType type, String message) {
            this.type = type;
            this.message = message;
        }

        public static PartnerBetResult success() {
            return new PartnerBetResult(ResultType.SUCCESS, null);
        }

        public static PartnerBetResult failure(String reason) {
            return new PartnerBetResult(ResultType.FAILURE, reason);
        }

        public static PartnerBetResult timeout(String reason) {
            return new PartnerBetResult(ResultType.TIMEOUT, reason);
        }

        public static PartnerBetResult cancelled(String reason) {
            return new PartnerBetResult(ResultType.CANCELLED, reason);
        }

        public static PartnerBetResult error(String reason) {
            return new PartnerBetResult(ResultType.ERROR, reason);
        }

        public boolean isSuccess() {
            return type == ResultType.SUCCESS;
        }

        public boolean isFailed() {
            return type == ResultType.FAILURE;
        }

        public String getMessage() {
            return message;
        }

        public ResultType getType() {
            return type;
        }

        public enum ResultType {
            SUCCESS, FAILURE, TIMEOUT, CANCELLED, ERROR
        }
    }

    // =================================================================
    // Inner class to track synchronization state per Arb
    // =================================================================
    private static class ArbSyncState {
        private final CountDownLatch deploymentLatch = new CountDownLatch(2); // Wait for both deployments
        private final CountDownLatch latch = new CountDownLatch(2);
        private final CountDownLatch betCompletionLatch = new CountDownLatch(2); // Both must complete
        private final Set<BookMaker> intentRegistered = ConcurrentHashMap.newKeySet();
        private final Set<BookMaker> deployedWindows = ConcurrentHashMap.newKeySet();
        private final Set<BookMaker> readyWindows = ConcurrentHashMap.newKeySet();
        private final Map<BookMaker, Double> oddsMap = new ConcurrentHashMap<>();
        private final Map<BookMaker, Boolean> betPlaced = new ConcurrentHashMap<>();
        private final Map<BookMaker, String> failures = new ConcurrentHashMap<>();
        private final Map<BookMaker, Boolean> rollbackCompleted = new ConcurrentHashMap<>();

        private volatile boolean cancelled = false;
        private volatile BookMaker timeoutTriggeredBy = null;
        private volatile BookMaker deploymentTimeoutTriggeredBy = null;
        private volatile boolean rollbackNeeded = false;
        private volatile String rollbackReason = null;
        private volatile BookMaker rollbackRequestor = null;

        private final AtomicBoolean timeoutFlagSet = new AtomicBoolean(false);
        private final AtomicBoolean deploymentTimeoutFlagSet = new AtomicBoolean(false);

        public synchronized void registerIntent(BookMaker bookmaker, double odds) {
            if (!cancelled) {
                intentRegistered.add(bookmaker);
                oddsMap.put(bookmaker, odds);
            }
        }

        public synchronized void unRegisterIntent(BookMaker bookmaker) {
            intentRegistered.remove(bookmaker);
            oddsMap.remove(bookmaker);
        }

        public Set<BookMaker> getIntentRegistered() {
            return new HashSet<>(intentRegistered);
        }

        public synchronized void markDeploymentSuccess(BookMaker bookmaker) {
            if (!cancelled && deployedWindows.add(bookmaker)) {
                deploymentLatch.countDown();
            }
        }

        public boolean isDeployed(BookMaker bookmaker) {
            return deployedWindows.contains(bookmaker);
        }

        public boolean areBothDeployed() {
            return deployedWindows.size() == 2;
        }

        public Set<BookMaker> getDeployedWindows() {
            return new HashSet<>(deployedWindows);
        }

        public boolean trySetDeploymentTimeoutFlag(BookMaker bookmaker) {
            if (deploymentTimeoutFlagSet.compareAndSet(false, true)) {
                deploymentTimeoutTriggeredBy = bookmaker;
                return true;
            }
            return false;
        }

        public synchronized void markReady(BookMaker bookmaker) {
            if (!cancelled && readyWindows.add(bookmaker)) {
                latch.countDown();
            }
        }

        public boolean isReady(BookMaker bookmaker) {
            return readyWindows.contains(bookmaker);
        }

        public boolean areBothReady() {
            return readyWindows.size() == 2;
        }

        public Set<BookMaker> getReadyWindows() {
            return new HashSet<>(readyWindows);
        }

        public boolean trySetTimeoutFlag(BookMaker bookmaker) {
            if (timeoutFlagSet.compareAndSet(false, true)) {
                timeoutTriggeredBy = bookmaker;
                return true;
            }
            return false;
        }

        public synchronized void cancel() {
            if (!cancelled) {
                cancelled = true;
                while (deploymentLatch.getCount() > 0) {
                    deploymentLatch.countDown();
                }
                while (latch.getCount() > 0) {
                    latch.countDown();
                }
                while (betCompletionLatch.getCount() > 0) {
                    betCompletionLatch.countDown();
                }
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public synchronized void recordBetPlaced(BookMaker bookmaker) {
            betPlaced.put(bookmaker, true);
        }

        public synchronized void recordFailure(BookMaker bookmaker, String reason) {
            failures.put(bookmaker, reason);
            betPlaced.put(bookmaker, false);
        }

        public boolean hasPlacedBet(BookMaker bookmaker) {
            return Boolean.TRUE.equals(betPlaced.get(bookmaker));
        }

        public String getFailureReason(BookMaker bookmaker) {
            return failures.get(bookmaker);
        }

        public synchronized void initiateRollback(BookMaker requestor, String reason) {
            rollbackNeeded = true;
            rollbackReason = reason;
            rollbackRequestor = requestor;
        }

        public boolean needsRollback() {
            return rollbackNeeded;
        }

        public String getRollbackReason() {
            return rollbackReason;
        }

        public synchronized void recordRollbackCompleted(BookMaker bookmaker, boolean success) {
            rollbackCompleted.put(bookmaker, success);
        }

        public boolean isCompleted() {
            if (rollbackNeeded) {
                // If rollback needed, wait for rollback completion
                return rollbackCompleted.size() >= betPlaced.keySet().stream()
                        .filter(bm -> Boolean.TRUE.equals(betPlaced.get(bm))).count();
            }
            return betPlaced.size() >= 2 || cancelled;
        }

        public String getStateSummary() {
            return String.format("Participants: %s, Deployed: %s, Ready: %s, Placed: %s, Failed: %s, Cancelled: %s, Rollback: %s, TimeoutBy: %s, DeployTimeoutBy: %s",
                    intentRegistered, deployedWindows, readyWindows, betPlaced.keySet(), failures.keySet(), cancelled, rollbackNeeded, timeoutTriggeredBy, deploymentTimeoutTriggeredBy);
        }

        public boolean hasIntent(BookMaker bookmaker) {
            return intentRegistered.contains(bookmaker);
        }
    }
}