package com.mouse.bet.checker;

import com.mouse.bet.enums.BookMaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ArbChecker — Spring Singleton Component
 *
 * Two bookie windows push live odds in independently via updateOdds().
 * ArbChecker is the ONE place where arb validity and stake calculation happen.
 *
 * Staleness protection:
 *   Each side tracks the timestamp of its last odds report.
 *   If either side has not reported within staleThresholdMs, recalculate()
 *   immediately returns an invalid result — preventing a stale cached value
 *   from one bookie being compared against a fresh value from the other.
 *
 * Stale scenarios this guards against:
 *   - A bookie window stalls, crashes, or gets stuck mid-loop
 *   - Network lag means one side's odds are several seconds behind
 *   - A new arb session starts before reset() is called and old odds linger
 *   - One bookie's market suspends so its window stops calling updateOdds()
 */
@Slf4j
@Component
public class ArbChecker {

    private static final MathContext MC    = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal  ONE   = BigDecimal.ONE;
    private static final int         SCALE = 2;

    /**
     * How old a bookie's last report can be before it is considered stale.
     * If either side exceeds this age, the arb result is marked invalid
     * regardless of what the odds say.
     *
     * Set in application.properties:  arb.stale.threshold.ms=15000
     * Default: 15 seconds
     */
    @Value("${arb.stale.threshold.ms:2000}")
    private long staleThresholdMs;

    @Value("${arb.total.budget:10000}")
    private BigDecimal totalBudget;

    // ── Side A ─────────────────────────────────────────────────────────────
    private volatile BookMaker  bookieA;
    private volatile BigDecimal oddsA        = BigDecimal.ZERO;
    private volatile long       lastUpdatedA = 0L; // epoch ms — 0 means never reported

    // ── Side B ─────────────────────────────────────────────────────────────
    private volatile BookMaker  bookieB;
    private volatile BigDecimal oddsB        = BigDecimal.ZERO;
    private volatile long       lastUpdatedB = 0L; // epoch ms — 0 means never reported

    private volatile ArbResult currentResult = ArbResult.empty();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // ══════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Called by each bookie window on every betslip loop tick.
     *
     * Records the exact timestamp of this update alongside the odds.
     * recalculate() uses both timestamps to reject comparisons where
     * one side's data is older than staleThresholdMs.
     *
     * Important: timestamp is refreshed even when odds are unchanged —
     * this is how a window signals "I am still alive and watching".
     *
     * @param bookmaker  Which bookie is reporting
     * @param newOdds    Current odds read from that bookie's betslip
     */
    public void updateOdds(BookMaker bookmaker, BigDecimal newOdds) {
        lock.writeLock().lock();
        try {
            long now = Instant.now().toEpochMilli();

            // ── Register first two distinct bookmakers ─────────────────────
            if (bookieA == null) {
                bookieA      = bookmaker;
                oddsA        = newOdds;
                lastUpdatedA = now;
                log.info("[ArbChecker] Registered bookieA: {} odds={}",
                        bookmaker.getDisplayName(), newOdds);
                currentResult = recalculate(now);
                return;
            }

            if (bookieA != bookmaker && bookieB == null) {
                bookieB = bookmaker;
                oddsB = newOdds;
                lastUpdatedB = now;
                log.info("[ArbChecker] Registered bookieB: {} odds={}",
                        bookmaker.getDisplayName(), newOdds);
                currentResult = recalculate(now);
                return;
            }

            if (bookmaker != bookieA && bookmaker != bookieB) {
                log.warn("[ArbChecker] Unknown bookmaker {} — only {} and {} are registered",
                        bookmaker.getDisplayName(),
                        bookieA.getDisplayName(),
                        bookieB != null ? bookieB.getDisplayName() : "none");
                return;
            }

            // ── Update odds + ALWAYS refresh timestamp ─────────────────────
            // Timestamp is updated even when odds are unchanged.
            // This is how a window proves it is still alive.
            if (bookmaker == bookieA) {
                if (oddsA.compareTo(newOdds) != 0) {
                    log.info("[ArbChecker] {} odds: {} -> {}",
                            bookieA.getDisplayName(), oddsA, newOdds);
                    oddsA = newOdds;
                }
                lastUpdatedA = now;

            } else {
                if (oddsB.compareTo(newOdds) != 0) {
                    log.info("[ArbChecker] {} odds: {} -> {}",
                            bookieB.getDisplayName(), oddsB, newOdds);
                    oddsB = newOdds;
                }
                lastUpdatedB = now;
            }

            currentResult = recalculate(now);

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the latest ArbResult.
     *
     * Performs a second staleness check at read time — because time passes
     * between the last updateOdds() call and when a window polls getResult().
     * Even a result that was fresh when calculated can become stale by the
     * time the caller reads it.
     */
    public ArbResult getResult() {
        long now = Instant.now().toEpochMilli();
        ArbResult result = currentResult;

        if (result.isArbValid() && isEitherSideStale(now)) {
            log.warn("[ArbChecker] Stale data detected at read time — invalidating. " +
                            "ageA={}ms ageB={}ms threshold={}ms",
                    lastUpdatedA == 0 ? "never" : (now - lastUpdatedA),
                    lastUpdatedB == 0 ? "never" : (now - lastUpdatedB),
                    staleThresholdMs);
            return ArbResult.stale(result);
        }

        return result;
    }

    /**
     * Resets state between arb sessions.
     * Call after a session ends before starting a new one.
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            bookieA       = null;
            bookieB       = null;
            oddsA         = BigDecimal.ZERO;
            oddsB         = BigDecimal.ZERO;
            lastUpdatedA  = 0L;
            lastUpdatedB  = 0L;
            currentResult = ArbResult.empty();
            log.info("[ArbChecker] Reset — ready for next session");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CORE CALCULATION
    // ══════════════════════════════════════════════════════════════════════

    private ArbResult recalculate(long now) {

        // ── Guard 1: both sides registered ────────────────────────────────
        if (bookieA == null || bookieB == null) {
            log.info("[ArbChecker] Waiting for both bookies to register...");
            return ArbResult.empty();
        }

        // ── Guard 2: both sides reported at least once ─────────────────────
        if (lastUpdatedA == 0L || lastUpdatedB == 0L) {
            log.info("[ArbChecker] Waiting for initial odds from both sides...");
            return ArbResult.empty();
        }

        // ── Guard 3: STALENESS CHECK ───────────────────────────────────────
        long ageA = now - lastUpdatedA;long ageB = now - lastUpdatedB;

        if (ageA > staleThresholdMs) {
            log.warn("[ArbChecker] STALE: {} odds are {}ms old (threshold={}ms) — rejecting",
                    bookieA.getDisplayName(), ageA, staleThresholdMs);
            return ArbResult.stale(currentResult);
        }
        if (ageB > staleThresholdMs) {
            log.warn("[ArbChecker] STALE: {} odds are {}ms old (threshold={}ms) — rejecting",
                    bookieB.getDisplayName(), ageB, staleThresholdMs);
            return ArbResult.stale(currentResult);
        }

        // ── Guard 4: positive odds ─────────────────────────────────────────
        if (oddsA.compareTo(BigDecimal.ZERO) <= 0 || oddsB.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[ArbChecker] Non-positive odds — skipping calculation");
            return ArbResult.empty();
        }

        // ── Calculation ────────────────────────────────────────────────────
        BigDecimal impliedA   = ONE.divide(oddsA, MC);
        BigDecimal impliedB   = ONE.divide(oddsB, MC);
        BigDecimal impliedSum = impliedA.add(impliedB);
        boolean    isArb      = impliedSum.compareTo(ONE) < 0;

        BigDecimal stakeA = BigDecimal.ZERO;
        BigDecimal stakeB = BigDecimal.ZERO;

        if (isArb) {
            stakeA = totalBudget.multiply(impliedA, MC)
                    .divide(impliedSum, MC)
                    .setScale(SCALE, RoundingMode.HALF_UP);
            stakeB = totalBudget.multiply(impliedB, MC)
                    .divide(impliedSum, MC)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        ArbResult result = new ArbResult(
                isArb, impliedSum,
                bookieA, oddsA, stakeA, ageA,
                bookieB, oddsB, stakeB, ageB,
                totalBudget
        );

        log.info("[ArbChecker] {} ({} | {}ms ago) <-> {} ({} | {}ms ago) | impliedSum={} | {}",
                bookieA.getDisplayName(), oddsA.setScale(2, RoundingMode.HALF_UP), ageA,
                bookieB.getDisplayName(), oddsB.setScale(2, RoundingMode.HALF_UP), ageB,
                impliedSum.setScale(4, RoundingMode.HALF_UP),
                isArb
                        ? "ARB | " + bookieA.getDisplayName() + "=N" + stakeA
                        + " | " + bookieB.getDisplayName() + "=N" + stakeB
                        + " | profit=N" + result.profit() + " (" + result.profitPercent() + "%)"
                        : "NO ARB");

        return result;
    }

    private boolean isEitherSideStale(long now) {
        if (lastUpdatedA == 0L || lastUpdatedB == 0L) return true;
        return (now - lastUpdatedA) > staleThresholdMs
                || (now - lastUpdatedB) > staleThresholdMs;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ArbResult
    // ══════════════════════════════════════════════════════════════════════

    public static class ArbResult {

        private final boolean    arbValid;
        private final boolean    stale;
        private final BigDecimal impliedProbSum;
        private final BookMaker  bookieA;
        private final BigDecimal oddsA;
        private final BigDecimal stakeA;
        private final long       ageAMs;
        private final BookMaker  bookieB;
        private final BigDecimal oddsB;
        private final BigDecimal stakeB;
        private final long       ageBMs;
        private final BigDecimal totalBudget;

        private ArbResult(boolean arbValid, BigDecimal impliedProbSum,
                          BookMaker bookieA, BigDecimal oddsA, BigDecimal stakeA, long ageAMs,
                          BookMaker bookieB, BigDecimal oddsB, BigDecimal stakeB, long ageBMs,
                          BigDecimal totalBudget) {
            this.arbValid       = arbValid;
            this.stale          = false;
            this.impliedProbSum = impliedProbSum;
            this.bookieA        = bookieA;
            this.oddsA          = oddsA;
            this.stakeA         = stakeA;
            this.ageAMs         = ageAMs;
            this.bookieB        = bookieB;
            this.oddsB          = oddsB;
            this.stakeB         = stakeB;
            this.ageBMs         = ageBMs;
            this.totalBudget    = totalBudget;
        }

        // Sentinel constructor for empty/stale with no odds data
        private ArbResult(boolean arbValid, boolean stale) {
            this.arbValid       = arbValid;
            this.stale          = stale;
            this.impliedProbSum = BigDecimal.ZERO;
            this.bookieA        = null;
            this.oddsA          = BigDecimal.ZERO;
            this.stakeA         = BigDecimal.ZERO;
            this.ageAMs         = -1L;
            this.bookieB        = null;
            this.oddsB          = BigDecimal.ZERO;
            this.stakeB         = BigDecimal.ZERO;
            this.ageBMs         = -1L;
            this.totalBudget    = BigDecimal.ZERO;
        }

        static ArbResult empty() {
            return new ArbResult(false, false);
        }

        /**
         * Copies the previous result's odds for logging context but:
         *   - sets arbValid = false  (windows must not place)
         *   - sets stale    = true   (so caller knows why it was invalidated)
         *   - zeroes stakes          (no stake should be used from a stale result)
         */
        static ArbResult stale(ArbResult prev) {
            if (prev == null || prev.bookieA == null || prev.bookieB == null) {
                return new ArbResult(false, true);
            }
            return new ArbResult(
                    false,                // arbValid
                    prev.impliedProbSum,
                    prev.bookieA, prev.oddsA, BigDecimal.ZERO, prev.ageAMs,
                    prev.bookieB, prev.oddsB, BigDecimal.ZERO, prev.ageBMs,
                    prev.totalBudget
            ) {
                @Override public boolean isStale() { return true; }
            };
        }

        // ── Polling ────────────────────────────────────────────────────────

        /** True only when arb is valid AND both sides are fresh. */
        public boolean isArbValid() { return arbValid; }

        /** True when the result was invalidated due to stale data. */
        public boolean isStale() { return stale; }

        /**
         * Stake for the given bookmaker.
         * Always returns ZERO when arbValid is false (including stale).
         */
        public BigDecimal getStake(BookMaker bookmaker) {
            if (!arbValid) return BigDecimal.ZERO;
            if (bookmaker == bookieA) return stakeA;
            if (bookmaker == bookieB) return stakeB;
            return BigDecimal.ZERO;
        }

        /** Latest odds for the given bookmaker. */
        public BigDecimal getOdds(BookMaker bookmaker) {
            if (bookmaker == bookieA) return oddsA;
            if (bookmaker == bookieB) return oddsB;
            return BigDecimal.ZERO;
        }

        /** How old (ms) the given bookie's odds were when this result was built. */
        public long getAgeMs(BookMaker bookmaker) {
            if (bookmaker == bookieA) return ageAMs;
            if (bookmaker == bookieB) return ageBMs;
            return -1L;
        }

        public BigDecimal getImpliedProbSum() { return impliedProbSum; }

        public BigDecimal guaranteedReturn() {
            if (!arbValid || impliedProbSum.compareTo(BigDecimal.ZERO) == 0)
                return BigDecimal.ZERO;
            return totalBudget
                    .divide(impliedProbSum, new MathContext(10, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        public BigDecimal profit() {
            return guaranteedReturn().subtract(totalBudget);
        }

        public String profitPercent() {
            if (!arbValid || impliedProbSum.compareTo(BigDecimal.ZERO) == 0) return "0.00";
            return ONE.divide(impliedProbSum, new MathContext(10, RoundingMode.HALF_UP))
                    .subtract(ONE)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        @Override
        public String toString() {
            if (bookieA == null || bookieB == null)
                return "ArbResult{ " + (stale ? "STALE — no prior data" : "awaiting odds") + " }";
            return String.format(
                    "ArbResult{ %s<->%s | valid=%b | stale=%b | impliedSum=%s | " +
                            "return=N%s | profit=N%s (%s%%) | " +
                            "%s=N%s@%s(%dms) | %s=N%s@%s(%dms) }",
                    bookieA.getDisplayName(), bookieB.getDisplayName(),
                    arbValid, stale,
                    impliedProbSum.setScale(4, RoundingMode.HALF_UP),
                    guaranteedReturn(), profit(), profitPercent(),
                    bookieA.getDisplayName(), stakeA, oddsA, ageAMs,
                    bookieB.getDisplayName(), stakeB, oddsB, ageBMs);
        }
    }
}
