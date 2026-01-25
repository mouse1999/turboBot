package com.mouse.bet.service;

import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.repository.ArbitrageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrageService {

    private final ArbitrageRepository arbitrageRepository;

    // Application-level lock for critical cleanup operations
    private final ReadWriteLock cleanupLock = new ReentrantReadWriteLock(true);

    // Striped locks for arb updates - prevents same arb from being updated concurrently
    private final ConcurrentHashMap<String, Object> arbLocks = new ConcurrentHashMap<>();

    private static final long FRESHNESS_THRESHOLD_SECONDS = 2;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 100;

    private LocalDateTime getFreshnessCutoff() {
        return LocalDateTime.now().minusSeconds(FRESHNESS_THRESHOLD_SECONDS);
    }

    // ==================== READ OPERATIONS (Thread-safe, read-only) ====================

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findAllArbitrage() {
        LocalDateTime cutoff = getFreshnessCutoff();
        return arbitrageRepository.findAll(cutoff);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Page<ArbitrageOpportunity> findAllArbitrage(Pageable pageable) {
        LocalDateTime cutoff = getFreshnessCutoff();
        List<ArbitrageOpportunity> freshArbs = arbitrageRepository.findAll(cutoff);

        if (pageable.getSort().isSorted()) {
            applySorting(freshArbs, pageable.getSort());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), freshArbs.size());
        List<ArbitrageOpportunity> pageContent = freshArbs.subList(start, end);

        return new PageImpl<>(pageContent, pageable, freshArbs.size());
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findAllActiveArbitrage() {
        return arbitrageRepository.findFreshActiveArbs();
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Page<ArbitrageOpportunity> findAllActiveArbitrage(Pageable pageable) {
        List<ArbitrageOpportunity> freshArbs = arbitrageRepository.findFreshActiveArbs();

        if (pageable.getSort().isSorted()) {
            applySorting(freshArbs, pageable.getSort());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), freshArbs.size());
        List<ArbitrageOpportunity> pageContent = freshArbs.subList(start, end);

        return new PageImpl<>(pageContent, pageable, freshArbs.size());
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Optional<ArbitrageOpportunity> findById(Long id) {
        return arbitrageRepository.findById(id);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Optional<ArbitrageOpportunity> findByExternalId(String externalId) {
        return arbitrageRepository.findByExternalId(externalId);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Page<ArbitrageOpportunity> findAllArbitrageSortedByProfit(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "profitPercentage"));
        return findAllArbitrage(pageable);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findActiveLiveArbs() {
        return arbitrageRepository.findActiveLiveArbs();
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findActiveArbsBySport(String sport) {
        return arbitrageRepository.findActiveArbsBySportFresh(sport);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findActiveArbsWithMinProfit(BigDecimal minProfit) {
        LocalDateTime cutoff = getFreshnessCutoff();
        return arbitrageRepository.findActiveArbsWithMinProfit(minProfit).stream()
                .filter(arb -> arb.getUpdatedAt() != null && arb.getUpdatedAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findActiveArbsByBookmaker(Integer bookmakerId) {
        LocalDateTime cutoff = getFreshnessCutoff();
        return arbitrageRepository.findActiveArbsByBookmaker(bookmakerId).stream()
                .filter(arb -> arb.getUpdatedAt() != null && arb.getUpdatedAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findActiveArbsBetweenBookmakers(Integer bookmaker1, Integer bookmaker2) {
        LocalDateTime cutoff = getFreshnessCutoff();
        return arbitrageRepository.findActiveArbsBetweenBookmakers(bookmaker1, bookmaker2).stream()
                .filter(arb -> arb.getUpdatedAt() != null && arb.getUpdatedAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findStaleActiveArbs() {
        return arbitrageRepository.findStaleActiveArbs();
    }

    /**
     * Find active arbs by maximum age in seconds
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findActiveArbsByMaxAge(int maxAgeSeconds) {
        return arbitrageRepository.findActiveArbsByMaxAge(maxAgeSeconds);
    }

    /**
     * Find active arbs by max age with minimum profit filter
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findActiveArbsByMaxAgeAndMinProfit(int maxAgeSeconds, BigDecimal minProfit) {
        return arbitrageRepository.findActiveArbsByMaxAgeAndMinProfit(maxAgeSeconds, minProfit);
    }

    /**
     * Find active arbs by sport and max age
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<ArbitrageOpportunity> findActiveArbsBySportAndMaxAge(String sport, int maxAgeSeconds) {
        return arbitrageRepository.findActiveArbsBySportAndMaxAge(sport, maxAgeSeconds);
    }

    /**
     * Count active arbs by maximum age
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public long countActiveArbsByMaxAge(int maxAgeSeconds) {
        return arbitrageRepository.countActiveArbsByMaxAge(maxAgeSeconds);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public ArbStatistics getStatistics() {
        long totalActive = arbitrageRepository.countActiveArbs();
        long freshActive = arbitrageRepository.findFreshActiveArbs().size();
        long staleActive = arbitrageRepository.findStaleActiveArbs().size();
        BigDecimal avgProfit = arbitrageRepository.getAverageProfitPercentage();

        return new ArbStatistics(totalActive, freshActive, staleActive, avgProfit);
    }

    // ==================== WRITE OPERATIONS (Thread-safe with manual retry) ====================

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            timeout = 5
    )
    public void saveArbitrageOpportunity(ArbitrageOpportunity arbitrageOpportunity) {
        try {
            SaveResult result = saveOrUpdateArbitrage(arbitrageOpportunity);
            log.debug("✅ Successfully saved arb: {} ({})",
                    arbitrageOpportunity.getExternalId(), result);
        } catch (Exception e) {
            log.error("❌ Failed to save arb: {}",
                    arbitrageOpportunity.getExternalId(), e);
            throw e;
        }
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            timeout = 10
    )
    public SaveResult saveOrUpdateArbitrage(ArbitrageOpportunity opportunity) {
        if (opportunity.getExternalId() == null) {
            return saveOrUpdateWithRetry(opportunity);
        }

        Object lock = arbLocks.computeIfAbsent(opportunity.getExternalId(), k -> new Object());

        try {
            synchronized (lock) {
                return saveOrUpdateWithRetry(opportunity);
            }
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("EntityEntry")) {
                log.error("❌ Hibernate session error for arb {}: {}",
                        opportunity.getExternalId(), e.getMessage());
                return SaveResult.SKIPPED;
            }
            throw e;
        } finally {
            arbLocks.remove(opportunity.getExternalId(), lock);
        }
    }



    private SaveResult saveOrUpdateWithRetry(ArbitrageOpportunity opportunity) {
        int attempt = 0;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                return saveOrUpdateWithLock(opportunity);

            } catch (
                    OptimisticLockingFailureException |
                    StaleObjectStateException e) {

                attempt++;

                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    log.error("❌ Failed to save/update arb {} after {} attempts",
                            opportunity.getExternalId(), MAX_RETRY_ATTEMPTS);
                    throw new RuntimeException(
                            "Failed to save arbitrage after " + MAX_RETRY_ATTEMPTS + " retries", e);
                }

                log.warn("⚠️ Lock conflict for arb {} (attempt {}/{}), retrying...",
                        opportunity.getExternalId(), attempt, MAX_RETRY_ATTEMPTS);

                retryWithBackoff(attempt);
            }
        }

        throw new IllegalStateException("Should never reach here");
    }



    private SaveResult saveOrUpdateWithLock(ArbitrageOpportunity opportunity) {
        if (opportunity.getExternalId() == null) {
            arbitrageRepository.save(opportunity);
            log.debug("✅ Saved new arb without external ID");
            return SaveResult.SAVED;
        }

        Optional<ArbitrageOpportunity> existing =
                arbitrageRepository.findByExternalIdWithLock(opportunity.getExternalId());

        if (existing.isEmpty()) {
            arbitrageRepository.save(opportunity);
            log.debug("✅ Saved new arb: {}", opportunity.getExternalId());
            return SaveResult.SAVED;
        }

        ArbitrageOpportunity existingArb = existing.get();
        log.debug("🔄 Updating existing arb: {}", opportunity.getExternalId());

        // Update basic fields
        existingArb.setLastCheckedAt(LocalDateTime.now());
        existingArb.setCreatedAt(LocalDateTime.now());
        existingArb.setUpdatedAt(LocalDateTime.now());
        existingArb.setProfitPercentage(opportunity.getProfitPercentage());
        existingArb.setConfidenceScore(opportunity.getConfidenceScore());
        existingArb.setStatus(opportunity.getStatus());
        existingArb.setIsLive(opportunity.getIsLive());
        existingArb.setMatchProgress(opportunity.getMatchProgress());
        existingArb.setRoiPercentage(opportunity.getRoiPercentage());
        existingArb.setMarketType(opportunity.getMarketType());
        existingArb.setOutCome(opportunity.getOutCome());

        // Update outcomes if present
        if (opportunity.getOutcomes() != null && !opportunity.getOutcomes().isEmpty()) {
            existingArb.getOutcomes().removeIf(existingOutcome ->
                    opportunity.getOutcomes().stream()
                            .noneMatch(newOutcome ->
                                    newOutcome.getSubEventId().equals(existingOutcome.getSubEventId())
                            )
            );

            for (ArbOutcome newOutcome : opportunity.getOutcomes()) {
                Optional<ArbOutcome> existingOutcome = existingArb.getOutcomes().stream()
                        .filter(o -> o.getSubEventId().equals(newOutcome.getSubEventId()))
                        .findFirst();

                if (existingOutcome.isPresent()) {
                    ArbOutcome outcomeToUpdate = existingOutcome.get();
                    outcomeToUpdate.setOdds(newOutcome.getOdds());
                    outcomeToUpdate.setPreviousOdds(newOutcome.getPreviousOdds());
                    outcomeToUpdate.setBookmakerName(newOutcome.getBookmakerName());
                    outcomeToUpdate.setOutComeName(newOutcome.getOutComeName());
                    outcomeToUpdate.setMarketType(newOutcome.getMarketType());
                    outcomeToUpdate.setProgress(newOutcome.getProgress());
                    outcomeToUpdate.setUpdatedAt(LocalDateTime.now());
                    outcomeToUpdate.setInitiator(newOutcome.getInitiator());
                } else {
                    ArbOutcome outcomeToAdd = ArbOutcome.builder()
                            .bookmakerId(newOutcome.getBookmakerId())
                            .bookmakerName(newOutcome.getBookmakerName())
                            .outComeName(newOutcome.getOutComeName())
                            .marketType(newOutcome.getMarketType())
                            .odds(newOutcome.getOdds())
                            .previousOdds(newOutcome.getPreviousOdds())
                            .subEventId(newOutcome.getSubEventId())
                            .originalId(newOutcome.getOriginalId())
                            .sport(newOutcome.getSport())
                            .awayTeam(newOutcome.getAwayTeam())
                            .homeTeam(newOutcome.getHomeTeam())
                            .leagueName(newOutcome.getLeagueName())
                            .progress(newOutcome.getProgress())
                            .updatedAt(LocalDateTime.now())
                            .reordered(newOutcome.getReordered())
                            .initiator(newOutcome.getInitiator())
                            .stake(newOutcome.getStake())
                            .build();
                    existingArb.addOutcome(outcomeToAdd);
                }
            }
        }

        // ✅ ALWAYS call onUpdate before saving
        existingArb.onUpdate();

        arbitrageRepository.save(existingArb);
        return SaveResult.UPDATED;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            timeout = 30
    )
    public void saveAllArbitrage(List<ArbitrageOpportunity> opportunities) {
        if (opportunities == null || opportunities.isEmpty()) {
            return;
        }

        int attempt = 0;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                arbitrageRepository.saveAll(opportunities);
                log.debug("✅ Batch saved {} arbitrage opportunities", opportunities.size());
                return;

            } catch (
                    OptimisticLockingFailureException |
                    StaleObjectStateException e) {

                attempt++;

                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    log.error("❌ Failed to batch save {} arbs after {} attempts",
                            opportunities.size(), MAX_RETRY_ATTEMPTS);
                    throw new RuntimeException(
                            "Failed to batch save arbitrage opportunities after " +
                                    MAX_RETRY_ATTEMPTS + " retries", e);
                }

                log.warn("⚠️ Optimistic lock failure during batch save (attempt {}/{}), retrying...",
                        attempt, MAX_RETRY_ATTEMPTS);

                retryWithBackoff(attempt);

            } catch (Exception e) {
                log.error("❌ Failed to batch save arbitrage opportunities", e);
                throw e;
            }
        }
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED
    )
    public boolean updateArbitrageById(Long id, ArbitrageOpportunity updates) {
        int attempt = 0;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                Optional<ArbitrageOpportunity> existing = arbitrageRepository.findByIdWithLock(id);

                if (existing.isEmpty()) {
                    log.warn("⚠️ Arb not found for update: {}", id);
                    return false;
                }

                ArbitrageOpportunity arb = existing.get();

                if (updates.getProfitPercentage() != null) {
                    arb.setProfitPercentage(updates.getProfitPercentage());
                }
                if (updates.getStatus() != null) {
                    arb.setStatus(updates.getStatus());
                }
                if (updates.getConfidenceScore() != null) {
                    arb.setConfidenceScore(updates.getConfidenceScore());
                }

                arb.setLastCheckedAt(LocalDateTime.now());
                arbitrageRepository.save(arb);

                log.debug("✅ Updated arb: {}", id);
                return true;

            } catch (
                    OptimisticLockingFailureException |
                    StaleObjectStateException e) {

                attempt++;

                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    log.error("❌ Failed to update arb {} after {} attempts",
                            id, MAX_RETRY_ATTEMPTS);
                    throw new RuntimeException(
                            "Failed to update arbitrage after " + MAX_RETRY_ATTEMPTS + " retries", e);
                }

                log.warn("⚠️ Optimistic lock failure for arb update {} (attempt {}/{}), retrying...",
                        id, attempt, MAX_RETRY_ATTEMPTS);

                retryWithBackoff(attempt);
            }
        }

        return false;
    }

    // ==================== CLEANUP OPERATIONS ====================

    @Scheduled(fixedRate = 5000)
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            timeout = 30
    )
    public void cleanupStaleArbs() {
        if (!cleanupLock.writeLock().tryLock()) {
            log.debug("⏭️ Skipping cleanup - another cleanup is in progress");
            return;
        }

        try {
            int expired = arbitrageRepository.expireStaleArbs();
            if (expired > 0) {
                log.warn("⚠️ Expired {} stale arbitrage opportunities (not updated in last {} seconds)",
                        expired, FRESHNESS_THRESHOLD_SECONDS);
            } else {
                log.debug("✅ Cleanup completed - no stale arbs found");
            }
        } catch (Exception e) {
            log.error("❌ Error during cleanup of stale arbs", e);
        } finally {
            cleanupLock.writeLock().unlock();
        }
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED
    )
    public int expireStaleArbs() {
        cleanupLock.writeLock().lock();
        try {
            int expired = arbitrageRepository.expireStaleArbs();
            log.info("✅ Manually expired {} stale arbitrage opportunities", expired);
            return expired;
        } catch (Exception e) {
            log.error("❌ Error during manual cleanup", e);
            throw e;
        } finally {
            cleanupLock.writeLock().unlock();
        }
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED,
            timeout = 60
    )
    public int cleanupStaleArbsWithCustomLogic() {
        cleanupLock.writeLock().lock();
        try {
            List<ArbitrageOpportunity> staleArbs = arbitrageRepository.findStaleArbsWithLock(
                    ArbStatus.ACTIVE,
                    getFreshnessCutoff()
            );

            if (staleArbs.isEmpty()) {
                log.debug("✅ No stale arbs to clean up");
                return 0;
            }

            log.info("🧹 Found {} stale arbs to expire", staleArbs.size());

            List<Long> idsToExpire = staleArbs.stream()
                    .filter(arb -> {
                        boolean shouldExpire = arb.getProfitPercentage().compareTo(new BigDecimal("1.0")) < 0
                                || arb.getArbAgeSeconds() > 10;
                        if (shouldExpire) {
                            log.debug("🗑️ Expiring arb {} (profit: {}%, age: {}s)",
                                    arb.getExternalId(), arb.getProfitPercentage(), arb.getArbAgeSeconds());
                        }
                        return shouldExpire;
                    })
                    .map(ArbitrageOpportunity::getId)
                    .collect(Collectors.toList());

            if (!idsToExpire.isEmpty()) {
                int expired = arbitrageRepository.expireArbsByIds(idsToExpire, LocalDateTime.now());
                log.info("✅ Expired {} arbs with custom logic", expired);
                return expired;
            }

            return 0;

        } catch (Exception e) {
            log.error("❌ Error during custom cleanup", e);
            throw e;
        } finally {
            cleanupLock.writeLock().unlock();
        }
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED
    )
    public int deleteOldExpiredArbs(int daysOld) {
        cleanupLock.writeLock().lock();
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
            int deleted = arbitrageRepository.deleteByStatusAndCreatedAtBefore(ArbStatus.EXPIRED, cutoff);
            log.info("✅ Deleted {} expired arbs older than {} days", deleted, daysOld);
            return deleted;
        } catch (Exception e) {
            log.error("❌ Error deleting old expired arbs", e);
            throw e;
        } finally {
            cleanupLock.writeLock().unlock();
        }
    }

    /**
     * Expire arbs by age
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED
    )
    public int expireArbsByAge(int maxAgeSeconds) {
        cleanupLock.writeLock().lock();
        try {
            int expired = arbitrageRepository.expireArbsByAge(maxAgeSeconds);
            log.info("✅ Expired {} arbs older than {} seconds", expired, maxAgeSeconds);
            return expired;
        } catch (Exception e) {
            log.error("❌ Error expiring arbs by age", e);
            throw e;
        } finally {
            cleanupLock.writeLock().unlock();
        }
    }

    // ==================== HELPER METHODS ====================

    private void retryWithBackoff(int attempt) {
        try {
            long delay = RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry backoff", ie);
        }
    }

    private List<ArbitrageOpportunity> applySorting(List<ArbitrageOpportunity> arbs, Sort sort) {
        for (Sort.Order order : sort) {
            String property = order.getProperty();
            boolean ascending = order.isAscending();

            switch (property) {
                case "profitPercentage":
                    arbs.sort((a, b) -> {
                        int compare = a.getProfitPercentage().compareTo(b.getProfitPercentage());
                        return ascending ? compare : -compare;
                    });
                    break;
                case "createdAt":
                    arbs.sort((a, b) -> {
                        int compare = a.getCreatedAt().compareTo(b.getCreatedAt());
                        return ascending ? compare : -compare;
                    });
                    break;
                case "updatedAt":
                    arbs.sort((a, b) -> {
                        int compare = a.getUpdatedAt().compareTo(b.getUpdatedAt());
                        return ascending ? compare : -compare;
                    });
                    break;
                case "confidenceScore":
                    arbs.sort((a, b) -> {
                        int compare = a.getConfidenceScore().compareTo(b.getConfidenceScore());
                        return ascending ? compare : -compare;
                    });
                    break;
                default:
                    arbs.sort((a, b) -> b.getProfitPercentage().compareTo(a.getProfitPercentage()));
            }
        }
        return arbs;
    }

    // ==================== DTOs & ENUMS ====================

    public enum SaveResult {
        SAVED,
        UPDATED,
        SKIPPED
    }

    public record ArbStatistics(
            long totalActive,
            long freshActive,
            long staleActive,
            BigDecimal averageProfit
    ) {
        public double freshPercentage() {
            return totalActive > 0 ? (freshActive * 100.0 / totalActive) : 0.0;
        }

        public double stalePercentage() {
            return totalActive > 0 ? (staleActive * 100.0 / totalActive) : 0.0;
        }
    }
}