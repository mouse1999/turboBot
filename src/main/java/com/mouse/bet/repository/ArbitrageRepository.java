package com.mouse.bet.repository;

import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.enums.ArbStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArbitrageRepository extends JpaRepository<ArbitrageOpportunity, Long> {

    // ==================== PESSIMISTIC LOCKING METHODS (Thread-Safe Writes) ====================

    /**
     * Find by ID with pessimistic write lock - prevents concurrent modifications
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.id = :id")
    Optional<ArbitrageOpportunity> findByIdWithLock(@Param("id") Long id);

    /**
     * Find by external ID with pessimistic write lock - prevents concurrent modifications
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.externalId = :externalId")
    Optional<ArbitrageOpportunity> findByExternalIdWithLock(@Param("externalId") String externalId);

    /**
     * Find stale arbs with pessimistic write lock for cleanup operations
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = :status AND a.updatedAt < :cutoffTime")
    List<ArbitrageOpportunity> findStaleArbsWithLock(
            @Param("status") ArbStatus status,
            @Param("cutoffTime") LocalDateTime cutoffTime);

    // ==================== READ OPERATIONS (Read-Only, No Locking) ====================

    // Single entity fetch with outcomes
    @Query("SELECT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.externalId = :externalId")
    Optional<ArbitrageOpportunity> findByExternalId(@Param("externalId") String externalId);

    // Fetch all by status with outcomes
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.status = :status")
    List<ArbitrageOpportunity> findByStatus(@Param("status") ArbStatus status);

    // Fetch all by isLive with outcomes
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.isLive = :isLive")
    List<ArbitrageOpportunity> findByIsLive(@Param("isLive") Boolean isLive);

    // Find active arbs with minimum profit (with outcomes)
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = 'ACTIVE' AND a.profitPercentage >= :minProfit " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findActiveArbsWithMinProfit(@Param("minProfit") BigDecimal minProfit);

    // Find active live arbs (with outcomes)
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = 'ACTIVE' AND a.isLive = true " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findActiveLiveArbs();

    // Count queries (no need for fetch)
    @Query("SELECT COUNT(a) FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE'")
    long countActiveArbs();

    @Query("SELECT AVG(a.profitPercentage) FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE'")
    BigDecimal getAverageProfitPercentage();

    // Find arbs involving specific bookmaker (with outcomes)
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes o " +
            "WHERE o.bookmakerId = :bookmakerId AND a.status = 'ACTIVE'")
    List<ArbitrageOpportunity> findActiveArbsByBookmaker(@Param("bookmakerId") Integer bookmakerId);

    // Find arbs between two specific bookmakers (with outcomes)
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE EXISTS (SELECT 1 FROM ArbOutcome o1 WHERE o1.arbitrage = a AND o1.bookmakerId = :bm1) " +
            "AND EXISTS (SELECT 1 FROM ArbOutcome o2 WHERE o2.arbitrage = a AND o2.bookmakerId = :bm2) " +
            "AND a.status = 'ACTIVE'")
    List<ArbitrageOpportunity> findActiveArbsBetweenBookmakers(
            @Param("bm1") Integer bookmaker1,
            @Param("bm2") Integer bookmaker2);

    // Find all arbs with outcomes (override default findAll) - FRESH ONLY (updated within 2 seconds)
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.updatedAt >= :cutoffTime " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findAll(@Param("cutoffTime") LocalDateTime cutoffTime);

    // Find all active arbs with outcomes (useful for listing/API) - FRESH ONLY
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = 'ACTIVE' " +
            "AND a.updatedAt >= :cutoffTime " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findAllActiveArbs(@Param("cutoffTime") LocalDateTime cutoffTime);

    // Helper method to get cutoff time (call from service layer)
    // Service should pass: LocalDateTime.now().minusSeconds(2)
    default List<ArbitrageOpportunity> findAllFresh() {
        return findAll(LocalDateTime.now().minusSeconds(2));
    }

    default List<ArbitrageOpportunity> findAllActiveArbsFresh() {
        return findAllActiveArbs(LocalDateTime.now().minusSeconds(2));
    }

    // Find arbs by sport with outcomes - FRESH ONLY
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.sport = :sport " +
            "AND a.status = 'ACTIVE' " +
            "AND a.updatedAt >= :cutoffTime " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findActiveArbsBySport(@Param("sport") String sport, @Param("cutoffTime") LocalDateTime cutoffTime);

    // Convenience method with automatic cutoff
    default List<ArbitrageOpportunity> findActiveArbsBySportFresh(String sport) {
        return findActiveArbsBySport(sport, LocalDateTime.now().minusSeconds(2));
    }

    // Find recent arbs (last N hours) with outcomes - FRESH ONLY
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.createdAt >= :since " +
            "AND a.status = 'ACTIVE' " +
            "AND a.updatedAt >= :cutoffTime " +
            "ORDER BY a.createdAt DESC")
    List<ArbitrageOpportunity> findRecentActiveArbs(@Param("since") LocalDateTime since, @Param("cutoffTime") LocalDateTime cutoffTime);

    // Convenience method with automatic cutoff
    default List<ArbitrageOpportunity> findRecentActiveArbsFresh(LocalDateTime since) {
        return findRecentActiveArbs(since, LocalDateTime.now().minusSeconds(2));
    }

    // Find fresh active arbs (updated within last N seconds)
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = 'ACTIVE' " +
            "AND a.updatedAt >= :cutoffTime " +
            "ORDER BY a.updatedAt DESC")
    List<ArbitrageOpportunity> findFreshActiveArbs(@Param("cutoffTime") LocalDateTime cutoffTime);

    // Convenience method for 2-second freshness
    default List<ArbitrageOpportunity> findFreshActiveArbs() {
        return findFreshActiveArbs(LocalDateTime.now().minusSeconds(2));
    }

    // Find stale arbs (not updated within last N seconds)
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = 'ACTIVE' " +
            "AND a.updatedAt < :cutoffTime " +
            "ORDER BY a.updatedAt ASC")
    List<ArbitrageOpportunity> findStaleActiveArbs(@Param("cutoffTime") LocalDateTime cutoffTime);

    // Convenience method for 2-second staleness
    default List<ArbitrageOpportunity> findStaleActiveArbs() {
        return findStaleActiveArbs(LocalDateTime.now().minusSeconds(2));
    }

    // ==================== WRITE OPERATIONS (With @Modifying) ====================

    @Modifying
    @Query("DELETE FROM ArbitrageOpportunity a WHERE a.status = :status AND a.createdAt < :before")
    int deleteByStatusAndCreatedAtBefore(@Param("status") ArbStatus status, @Param("before") LocalDateTime before);

    // Cleanup stale arbs (mark as expired if not updated for N seconds)
    @Modifying
    @Query("UPDATE ArbitrageOpportunity a SET a.status = 'EXPIRED', a.expiredAt = :now " +
            "WHERE a.status = 'ACTIVE' AND a.updatedAt < :cutoffTime")
    int expireStaleArbs(@Param("cutoffTime") LocalDateTime cutoffTime, @Param("now") LocalDateTime now);

    // Convenience method to expire arbs older than 2 seconds
    default int expireStaleArbs() {
        LocalDateTime now = LocalDateTime.now();
        return expireStaleArbs(now.minusSeconds(2), now);
    }

    /**
     * Expire specific arbs by IDs (used in custom cleanup logic)
     */
    @Modifying
    @Query("UPDATE ArbitrageOpportunity a SET a.status = 'EXPIRED', a.expiredAt = :now " +
            "WHERE a.id IN :ids")
    int expireArbsByIds(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);
}