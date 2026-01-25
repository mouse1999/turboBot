package com.mouse.bet.service;

import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.repository.ArbOutcomeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArbOutcomeService {

    private final ArbOutcomeRepository arbOutcomeRepository;

    // Threshold for considering an outcome as stale (in seconds)
    private static final long ACTIVE_THRESHOLD_SECONDS = 3;

    // Emojis for logging
    private static final String EMOJI_SAVE = "💾";
    private static final String EMOJI_SEARCH = "🔍";
    private static final String EMOJI_CHECK = "✔️";
    private static final String EMOJI_CROSS = "❌";
    private static final String EMOJI_CLOCK = "⏰";
    private static final String EMOJI_FILTER = "🔬";
    private static final String EMOJI_DELETE = "🗑️";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_INFO = "ℹ️";
    private static final String EMOJI_ACTIVE = "🟢";
    private static final String EMOJI_STALE = "🔴";
    private static final String EMOJI_REFRESH = "🔄";

    /* ===================== CREATE / UPDATE ===================== */

    @Transactional
    public ArbOutcome save(ArbOutcome arbOutcome) {
        log.info("{} {} Saving ArbOutcome | ID: {} | ArbId: {} | Bookmaker: {} | Outcome: {} | Odds: {}",
                EMOJI_SAVE, EMOJI_INFO,
                arbOutcome.getId(),
                arbOutcome.getArbitrage() != null ? arbOutcome.getArbitrage().getId() : "N/A",
                arbOutcome.getBookmakerName(),
                arbOutcome.getOutComeName(),
                arbOutcome.getOdds());

        ArbOutcome saved = arbOutcomeRepository.save(arbOutcome);

        log.debug("{} {} ArbOutcome saved successfully | ID: {} | UpdatedAt: {}",
                EMOJI_CHECK, EMOJI_SAVE,
                saved.getId(),
                saved.getUpdatedAt());

        return saved;
    }

    @Transactional
    public List<ArbOutcome> saveAll(List<ArbOutcome> outcomes) {
        log.info("{} {} Saving {} ArbOutcomes in bulk",
                EMOJI_SAVE, EMOJI_INFO, outcomes.size());

        if (log.isDebugEnabled()) {
            outcomes.forEach(outcome ->
                    log.debug("  - ID: {} | Bookmaker: {} | Outcome: {} | Odds: {}",
                            outcome.getId(),
                            outcome.getBookmakerName(),
                            outcome.getOutComeName(),
                            outcome.getOdds()));
        }

        List<ArbOutcome> saved = arbOutcomeRepository.saveAll(outcomes);

        log.info("{} {} Successfully saved {} ArbOutcomes",
                EMOJI_CHECK, EMOJI_SAVE, saved.size());

        return saved;
    }

    /* ===================== READ ===================== */

    @Transactional(readOnly = true)
    public Optional<ArbOutcome> findByExternalIdAndBookmaker(
            String externalId,
            Integer bookmakerId
    ) {
        log.debug("{} {} Finding ArbOutcome | ExternalId: {} | BookmakerId: {}",
                EMOJI_SEARCH, EMOJI_INFO, externalId, bookmakerId);

        Optional<ArbOutcome> result = arbOutcomeRepository
                .findByArbitrageExternalIdAndBookmakerId(externalId, bookmakerId);

        if (result.isPresent()) {
            ArbOutcome outcome = result.get();
            log.debug("{} {} Found ArbOutcome | ID: {} | Outcome: {} | Odds: {} | UpdatedAt: {}",
                    EMOJI_CHECK, EMOJI_SEARCH,
                    outcome.getId(),
                    outcome.getOutComeName(),
                    outcome.getOdds(),
                    outcome.getUpdatedAt());
        } else {
            log.debug("{} {} No ArbOutcome found for ExternalId: {} | BookmakerId: {}",
                    EMOJI_CROSS, EMOJI_SEARCH, externalId, bookmakerId);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<ArbOutcome> findByArbitrageId(Long arbId) {
        log.debug("{} {} Finding ArbOutcomes by ArbitrageId: {}",
                EMOJI_SEARCH, EMOJI_INFO, arbId);

        List<ArbOutcome> outcomes = arbOutcomeRepository.findByArbitrageId(arbId);

        log.info("{} {} Found {} ArbOutcome(s) for ArbitrageId: {}",
                EMOJI_CHECK, EMOJI_SEARCH, outcomes.size(), arbId);

        if (log.isDebugEnabled() && !outcomes.isEmpty()) {
            outcomes.forEach(outcome ->
                    log.debug("  - ID: {} | Bookmaker: {} | Outcome: {} | Odds: {}",
                            outcome.getId(),
                            outcome.getBookmakerName(),
                            outcome.getOutComeName(),
                            outcome.getOdds()));
        }

        return outcomes;
    }

    @Transactional(readOnly = true)
    public List<ArbOutcome> findByBookmaker(Integer bookmakerId) {
        log.debug("{} {} Finding ArbOutcomes by BookmakerId: {}",
                EMOJI_SEARCH, EMOJI_INFO, bookmakerId);

        List<ArbOutcome> outcomes = arbOutcomeRepository.findByBookmakerId(bookmakerId);

        log.info("{} {} Found {} ArbOutcome(s) for BookmakerId: {}",
                EMOJI_CHECK, EMOJI_SEARCH, outcomes.size(), bookmakerId);

        return outcomes;
    }

    @Transactional(readOnly = true)
    public List<ArbOutcome> findByArbitrageAndBookmaker(
            Long arbId,
            Integer bookmakerId
    ) {
        log.debug("{} {} Finding ArbOutcomes | ArbitrageId: {} | BookmakerId: {}",
                EMOJI_SEARCH, EMOJI_INFO, arbId, bookmakerId);

        List<ArbOutcome> outcomes = arbOutcomeRepository
                .findByArbitrageIdAndBookmakerId(arbId, bookmakerId);

        log.info("{} {} Found {} ArbOutcome(s) for ArbitrageId: {} | BookmakerId: {}",
                EMOJI_CHECK, EMOJI_SEARCH, outcomes.size(), arbId, bookmakerId);

        return outcomes;
    }

    /* ===================== ACTIVE STATUS CHECK (THREAD-SAFE) ===================== */

    /**
     * Check if an ArbOutcome is still active (being updated)
     * An outcome is considered active if it was updated within the last 3 seconds
     *
     * THREAD-SAFE: This method is stateless and can be called concurrently by multiple threads.
     * It only reads the updatedAt field which is a final snapshot of the entity state.
     *
     * @param arbOutcome the outcome to check (can be a detached entity)
     * @return true if active (updated within 3 seconds), false otherwise
     */
    public boolean isActive(ArbOutcome arbOutcome) {
        if (arbOutcome == null) {
            log.warn("{} {} Cannot check active status: ArbOutcome is null",
                    EMOJI_WARNING, EMOJI_CHECK);
            return false;
        }

        // Capture updatedAt in a local variable for thread safety
        final LocalDateTime updatedAt = arbOutcome.getUpdatedAt();

        if (updatedAt == null) {
            log.warn("{} {} Cannot check active status: updatedAt is null | ArbOutcome ID: {} | Bookmaker: {} | Outcome: {}",
                    EMOJI_WARNING, EMOJI_CHECK,
                    arbOutcome.getId(),
                    arbOutcome.getBookmakerName(),
                    arbOutcome.getOutComeName());
            return false;
        }

        // Capture current time once to avoid time drift during calculation
        final LocalDateTime now = LocalDateTime.now();
        final Duration timeSinceUpdate = Duration.between(updatedAt, now);
        final long secondsSinceUpdate = timeSinceUpdate.getSeconds();

        final boolean isActive = secondsSinceUpdate <= ACTIVE_THRESHOLD_SECONDS;

        if (log.isDebugEnabled()) {
            log.debug("{} {} {} ArbOutcome Status | ID: {} | Bookmaker: {} | Outcome: {} | Odds: {} | Last Updated: {}s ago | Active: {} | Thread: {}",
                    EMOJI_CLOCK, EMOJI_CHECK, isActive ? EMOJI_ACTIVE : EMOJI_STALE,
                    arbOutcome.getId(),
                    arbOutcome.getBookmakerName(),
                    arbOutcome.getOutComeName(),
                    arbOutcome.getOdds(),
                    secondsSinceUpdate,
                    isActive,
                    Thread.currentThread().getName());
        } else {
            log.info("{} {} ArbOutcome ID: {} | Bookmaker: {} | {}s ago | Status: {}",
                    EMOJI_CHECK, isActive ? EMOJI_ACTIVE : EMOJI_STALE,
                    arbOutcome.getId(),
                    arbOutcome.getBookmakerName(),
                    secondsSinceUpdate,
                    isActive ? "ACTIVE" : "STALE");
        }

        return isActive;
    }

    /**
     * Check if an ArbOutcome is stale (not being updated)
     * Convenience method - opposite of isActive()
     *
     * THREAD-SAFE: Delegates to isActive() which is thread-safe
     *
     * @param arbOutcome the outcome to check
     * @return true if stale (not updated in last 3 seconds), false otherwise
     */
    public boolean isStale(ArbOutcome arbOutcome) {
        boolean stale = !isActive(arbOutcome);

        if (stale && arbOutcome != null) {
            log.debug("{} {} ArbOutcome is STALE | ID: {} | Bookmaker: {} | Outcome: {}",
                    EMOJI_STALE, EMOJI_WARNING,
                    arbOutcome.getId(),
                    arbOutcome.getBookmakerName(),
                    arbOutcome.getOutComeName());
        }

        return stale;
    }

    /**
     * Get the number of seconds since the outcome was last updated
     *
     * THREAD-SAFE: Stateless read operation on immutable LocalDateTime
     *
     * @param arbOutcome the outcome to check
     * @return seconds since last update, or -1 if cannot determine
     */
    public long getSecondsSinceUpdate(ArbOutcome arbOutcome) {
        if (arbOutcome == null) {
            log.warn("{} {} Cannot get seconds since update: ArbOutcome is null",
                    EMOJI_WARNING, EMOJI_CLOCK);
            return -1;
        }

        // Capture updatedAt in a local variable for thread safety
        final LocalDateTime updatedAt = arbOutcome.getUpdatedAt();

        if (updatedAt == null) {
            log.warn("{} {} Cannot get seconds since update: updatedAt is null | ID: {} | Bookmaker: {}",
                    EMOJI_WARNING, EMOJI_CLOCK,
                    arbOutcome.getId(),
                    arbOutcome.getBookmakerName());
            return -1;
        }

        final LocalDateTime now = LocalDateTime.now();
        final Duration timeSinceUpdate = Duration.between(updatedAt, now);
        final long seconds = timeSinceUpdate.getSeconds();

        log.debug("{} {} Seconds since update | ID: {} | Bookmaker: {} | Outcome: {} | Seconds: {}",
                EMOJI_CLOCK, EMOJI_INFO,
                arbOutcome.getId(),
                arbOutcome.getBookmakerName(),
                arbOutcome.getOutComeName(),
                seconds);

        return seconds;
    }

    /**
     * Filter a list of outcomes to only include active ones
     *
     * THREAD-SAFE: Uses stream which creates a new list, doesn't modify input
     *
     * @param outcomes list of outcomes to filter (not modified)
     * @return new list containing only active outcomes
     */
    public List<ArbOutcome> filterActiveOutcomes(List<ArbOutcome> outcomes) {
        if (outcomes == null) {
            log.warn("{} {} Cannot filter: outcomes list is null",
                    EMOJI_WARNING, EMOJI_FILTER);
            return List.of();
        }

        log.info("{} {} Filtering {} ArbOutcomes for active status",
                EMOJI_FILTER, EMOJI_INFO, outcomes.size());

        List<ArbOutcome> activeOutcomes = outcomes.stream()
                .filter(this::isActive)
                .toList();

        log.info("{} {} Filtered results: {} active out of {} total outcomes",
                EMOJI_CHECK, EMOJI_FILTER, activeOutcomes.size(), outcomes.size());

        if (log.isDebugEnabled() && !activeOutcomes.isEmpty()) {
            activeOutcomes.forEach(outcome ->
                    log.debug("  {} Active: ID: {} | Bookmaker: {} | Outcome: {} | Odds: {}",
                            EMOJI_ACTIVE,
                            outcome.getId(),
                            outcome.getBookmakerName(),
                            outcome.getOutComeName(),
                            outcome.getOdds()));
        }

        return activeOutcomes;
    }

    /**
     * Check if an outcome by external ID and bookmaker is still active
     *
     * THREAD-SAFE: Uses @Transactional(readOnly=true) for database isolation.
     * Each thread gets its own transaction and entity manager session.
     * The optimistic locking (@Version) in ArbOutcome prevents concurrent modification issues.
     *
     * @param externalId the arbitrage external ID
     * @param bookmakerId the bookmaker ID
     * @return true if found and active, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isActiveByExternalIdAndBookmaker(String externalId, Integer bookmakerId) {
        if (externalId == null || bookmakerId == null) {
            log.warn("{} {} Cannot check active status: null parameters | ExternalId: {} | BookmakerId: {}",
                    EMOJI_WARNING, EMOJI_CHECK, externalId, bookmakerId);
            return false;
        }

        log.debug("{} {} Checking active status | ExternalId: {} | BookmakerId: {}",
                EMOJI_CHECK, EMOJI_SEARCH, externalId, bookmakerId);

        Optional<ArbOutcome> outcome = findByExternalIdAndBookmaker(externalId, bookmakerId);
        boolean active = outcome.map(this::isActive).orElse(false);

        if (outcome.isPresent()) {
            ArbOutcome arbOutcome = outcome.get();
            log.info("{} {} ExternalId: {} | BookmakerId: {} | Active: {} | ID: {} | Outcome: {}",
                    EMOJI_CHECK, active ? EMOJI_ACTIVE : EMOJI_STALE,
                    externalId, bookmakerId, active,
                    arbOutcome.getId(),
                    arbOutcome.getOutComeName());
        } else {
            log.debug("{} {} No outcome found | ExternalId: {} | BookmakerId: {}",
                    EMOJI_CROSS, EMOJI_SEARCH, externalId, bookmakerId);
        }

        return active;
    }

    /**
     * Fetch fresh data from database and check if active
     * Use this when you need the most up-to-date status from the database
     *
     * THREAD-SAFE: Forces a fresh database read in a new transaction
     *
     * @param outcomeId the outcome ID to check
     * @return true if found and active, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isActiveById(Long outcomeId) {
        if (outcomeId == null) {
            log.warn("{} {} Cannot check active status: outcomeId is null",
                    EMOJI_WARNING, EMOJI_CHECK);
            return false;
        }

        log.debug("{} {} Checking active status by ID: {}",
                EMOJI_REFRESH, EMOJI_SEARCH, outcomeId);

        boolean active = arbOutcomeRepository.findById(outcomeId)
                .map(outcome -> {
                    boolean isActive = isActive(outcome);
                    log.debug("{} {} ID: {} | Bookmaker: {} | Outcome: {} | Active: {}",
                            EMOJI_CHECK, isActive ? EMOJI_ACTIVE : EMOJI_STALE,
                            outcomeId,
                            outcome.getBookmakerName(),
                            outcome.getOutComeName(),
                            isActive);
                    return isActive;
                })
                .orElseGet(() -> {
                    log.debug("{} {} No outcome found with ID: {}",
                            EMOJI_CROSS, EMOJI_SEARCH, outcomeId);
                    return false;
                });

        return active;
    }

    /**
     * Refresh an outcome entity from the database and check if active
     * Useful when working with potentially stale entities
     *
     * THREAD-SAFE: Fetches fresh data in a read transaction
     *
     * @param outcomeId the outcome ID
     * @return Optional containing the fresh outcome if active, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<ArbOutcome> findActiveOutcome(Long outcomeId) {
        if (outcomeId == null) {
            log.warn("{} {} Cannot find active outcome: outcomeId is null",
                    EMOJI_WARNING, EMOJI_REFRESH);
            return Optional.empty();
        }

        log.debug("{} {} Finding and refreshing outcome | ID: {}",
                EMOJI_REFRESH, EMOJI_SEARCH, outcomeId);

        Optional<ArbOutcome> result = arbOutcomeRepository.findById(outcomeId)
                .filter(outcome -> {
                    boolean isActive = isActive(outcome);
                    if (isActive) {
                        log.info("{} {} Found active outcome | ID: {} | Bookmaker: {} | Outcome: {} | Odds: {}",
                                EMOJI_CHECK, EMOJI_ACTIVE,
                                outcome.getId(),
                                outcome.getBookmakerName(),
                                outcome.getOutComeName(),
                                outcome.getOdds());
                    } else {
                        log.debug("{} {} Outcome found but STALE | ID: {} | Bookmaker: {}",
                                EMOJI_STALE, EMOJI_WARNING,
                                outcome.getId(),
                                outcome.getBookmakerName());
                    }
                    return isActive;
                });

        if (result.isEmpty()) {
            log.debug("{} {} No active outcome found for ID: {}",
                    EMOJI_CROSS, EMOJI_REFRESH, outcomeId);
        }

        return result;
    }

    /* ===================== DELETE ===================== */

    @Transactional
    public void deleteByArbitrageId(Long arbId) {
        log.info("{} {} Deleting ArbOutcomes by ArbitrageId: {}",
                EMOJI_DELETE, EMOJI_INFO, arbId);

        try {
            arbOutcomeRepository.deleteByArbitrageId(arbId);
            log.info("{} {} Successfully deleted ArbOutcomes for ArbitrageId: {}",
                    EMOJI_CHECK, EMOJI_DELETE, arbId);
        } catch (Exception e) {
            log.error("{} {} Failed to delete ArbOutcomes for ArbitrageId: {} | Error: {}",
                    EMOJI_CROSS, EMOJI_DELETE, arbId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void deleteByArbitrageIdExplicit(Long arbId) {
        log.info("{} {} Deleting ArbOutcomes (explicit query) by ArbitrageId: {}",
                EMOJI_DELETE, EMOJI_INFO, arbId);

        try {
            arbOutcomeRepository.deleteByArbitrageIdQuery(arbId);
            log.info("{} {} Successfully deleted ArbOutcomes (explicit) for ArbitrageId: {}",
                    EMOJI_CHECK, EMOJI_DELETE, arbId);
        } catch (Exception e) {
            log.error("{} {} Failed to delete ArbOutcomes (explicit) for ArbitrageId: {} | Error: {}",
                    EMOJI_CROSS, EMOJI_DELETE, arbId, e.getMessage(), e);
            throw e;
        }
    }
}