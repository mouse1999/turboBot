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

    // ==================== STANDARD READS (No locking) ====================

    @Query("SELECT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.externalId = :externalId")
    Optional<ArbitrageOpportunity> findByExternalId(@Param("externalId") String externalId);

    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.status = :status")
    List<ArbitrageOpportunity> findByStatus(@Param("status") ArbStatus status);

    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.isLive = :isLive")
    List<ArbitrageOpportunity> findByIsLive(@Param("isLive") Boolean isLive);

    // ==================== PESSIMISTIC LOCKING READS (Thread-safe for updates) ====================

    /**
     * Find by external ID with PESSIMISTIC WRITE lock
     * Use this when you need to update the entity to prevent concurrent modifications
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.externalId = :externalId")
    Optional<ArbitrageOpportunity> findByExternalIdWithLock(@Param("externalId") String externalId);

    /**
     * Find by ID with PESSIMISTIC WRITE lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.id = :id")
    Optional<ArbitrageOpportunity> findByIdWithLock(@Param("id") Long id);

    /**
     * Find by ID with PESSIMISTIC READ lock (allows other reads, blocks writes)
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes WHERE a.id = :id")
    Optional<ArbitrageOpportunity> findByIdWithReadLock(@Param("id") Long id);

    /**
     * Find stale arbs with PESSIMISTIC WRITE lock for batch cleanup
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = :status AND a.updatedAt < :cutoffTime " +
            "ORDER BY a.updatedAt ASC")
    List<ArbitrageOpportunity> findStaleArbsWithLock(
            @Param("status") ArbStatus status,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    // ==================== ACTIVE ARBS QUERIES ====================

    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = 'ACTIVE' AND a.profitPercentage >= :minProfit " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findActiveArbsWithMinProfit(@Param("minProfit") BigDecimal minProfit);

    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = 'ACTIVE' AND a.isLive = true " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findActiveLiveArbs();

    // ==================== STATISTICS ====================

    @Query("SELECT COUNT(a) FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE'")
    long countActiveArbs();

    @Query("SELECT AVG(a.profitPercentage) FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE'")
    BigDecimal getAverageProfitPercentage();

    // ==================== BOOKMAKER QUERIES ====================

    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes o " +
            "WHERE o.bookmakerId = :bookmakerId AND a.status = 'ACTIVE'")
    List<ArbitrageOpportunity> findActiveArbsByBookmaker(@Param("bookmakerId") Integer bookmakerId);

    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE EXISTS (SELECT 1 FROM ArbOutcome o1 WHERE o1.arbitrage = a AND o1.bookmakerId = :bm1) " +
            "AND EXISTS (SELECT 1 FROM ArbOutcome o2 WHERE o2.arbitrage = a AND o2.bookmakerId = :bm2) " +
            "AND a.status = 'ACTIVE'")
    List<ArbitrageOpportunity> findActiveArbsBetweenBookmakers(
            @Param("bm1") Integer bookmaker1,
            @Param("bm2") Integer bookmaker2);

    // ==================== FRESH/STALE QUERIES (2-second threshold) ====================

    /**
     * Find all arbs updated within the specified time window
     */
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.updatedAt >= :cutoffTime " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findAll(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find all ACTIVE arbs updated within the specified time window
     */
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = 'ACTIVE' " +
            "AND a.updatedAt >= :cutoffTime " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findAllActiveArbs(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Default method for fresh active arbs (2-second window)
     */
    default List<ArbitrageOpportunity> findFreshActiveArbs() {
        return findAllActiveArbs(LocalDateTime.now().minusSeconds(2));
    }

    /**
     * Find arbs by sport (fresh only)
     */
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.sport = :sport " +
            "AND a.status = 'ACTIVE' " +
            "AND a.updatedAt >= :cutoffTime " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findActiveArbsBySport(
            @Param("sport") String sport,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    /**
     * Convenience method for fresh arbs by sport
     */
    default List<ArbitrageOpportunity> findActiveArbsBySportFresh(String sport) {
        return findActiveArbsBySport(sport, LocalDateTime.now().minusSeconds(2));
    }

    /**
     * Find recent active arbs (created since a certain time, and fresh)
     */
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.createdAt >= :since " +
            "AND a.status = 'ACTIVE' " +
            "AND a.updatedAt >= :cutoffTime " +
            "ORDER BY a.createdAt DESC")
    List<ArbitrageOpportunity> findRecentActiveArbs(
            @Param("since") LocalDateTime since,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    /**
     * Convenience method for recent fresh arbs
     */
    default List<ArbitrageOpportunity> findRecentActiveArbsFresh(LocalDateTime since) {
        return findRecentActiveArbs(since, LocalDateTime.now().minusSeconds(2));
    }

    /**
     * Find stale active arbs (not updated within threshold)
     */
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a LEFT JOIN FETCH a.outcomes " +
            "WHERE a.status = 'ACTIVE' " +
            "AND a.updatedAt < :cutoffTime " +
            "ORDER BY a.updatedAt ASC")
    List<ArbitrageOpportunity> findStaleActiveArbs(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Convenience method for stale arbs (older than 2 seconds)
     */
    default List<ArbitrageOpportunity> findStaleActiveArbs() {
        return findStaleActiveArbs(LocalDateTime.now().minusSeconds(2));
    }

    // ==================== CLEANUP OPERATIONS ====================

    /**
     * Delete arbs by status and created date
     */
    @Modifying
    @Query("DELETE FROM ArbitrageOpportunity a WHERE a.status = :status AND a.createdAt < :before")
    int deleteByStatusAndCreatedAtBefore(@Param("status") ArbStatus status, @Param("before") LocalDateTime before);

    /**
     * Expire stale arbs (mark as EXPIRED if not updated within threshold)
     * This is thread-safe with proper transaction isolation
     */
    @Modifying
    @Query("UPDATE ArbitrageOpportunity a SET a.status = 'EXPIRED', a.expiredAt = :now " +
            "WHERE a.status = 'ACTIVE' AND a.updatedAt < :cutoffTime")
    int expireStaleArbs(@Param("cutoffTime") LocalDateTime cutoffTime, @Param("now") LocalDateTime now);

    /**
     * Convenience method to expire arbs older than 2 seconds
     */
    default int expireStaleArbs() {
        LocalDateTime now = LocalDateTime.now();
        return expireStaleArbs(now.minusSeconds(2), now);
    }

    /**
     * Batch expire with pessimistic locking for more control
     * Use this when you need to perform additional logic before expiring
     */
    @Modifying
    @Query("UPDATE ArbitrageOpportunity a SET a.status = 'EXPIRED', a.expiredAt = :now " +
            "WHERE a.id IN :ids")
    int expireArbsByIds(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    // ==================== CUSTOM BATCH OPERATIONS ====================

    /**
     * Find arbs for batch update with pessimistic lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ArbitrageOpportunity a WHERE a.externalId IN :externalIds")
    List<ArbitrageOpportunity> findByExternalIdsWithLock(@Param("externalIds") List<String> externalIds);

    /**
     * Check if external ID exists (for quick validation without fetching outcomes)
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM ArbitrageOpportunity a WHERE a.externalId = :externalId")
    boolean existsByExternalId(@Param("externalId") String externalId);

    /**
     * Count fresh active arbs
     */
    @Query("SELECT COUNT(a) FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE' AND a.updatedAt >= :cutoffTime")
    long countFreshActiveArbs(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Convenience method for fresh count
     */
    default long countFreshActiveArbs() {
        return countFreshActiveArbs(LocalDateTime.now().minusSeconds(2));
    }
}