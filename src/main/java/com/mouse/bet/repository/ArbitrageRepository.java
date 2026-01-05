package com.mouse.bet.repository;

import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.enums.ArbStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArbitrageRepository extends JpaRepository<ArbitrageOpportunity, Long> {
    Optional<ArbitrageOpportunity> findByExternalId(String externalId);

    List<ArbitrageOpportunity> findByStatus(ArbStatus status);

    List<ArbitrageOpportunity> findByIsLive(Boolean isLive);

    @Query("SELECT a FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE' " +
            "AND a.profitPercentage >= :minProfit ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findActiveArbsWithMinProfit(@Param("minProfit") BigDecimal minProfit);

    @Query("SELECT a FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE' AND a.isLive = true " +
            "ORDER BY a.profitPercentage DESC")
    List<ArbitrageOpportunity> findActiveLiveArbs();

    @Query("SELECT COUNT(a) FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE'")
    long countActiveArbs();

    @Query("SELECT AVG(a.profitPercentage) FROM ArbitrageOpportunity a WHERE a.status = 'ACTIVE'")
    BigDecimal getAverageProfitPercentage();

    // Find arbs involving specific bookmaker
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a JOIN a.outcomes o " +
            "WHERE o.bookmakerId = :bookmakerId AND a.status = 'ACTIVE'")
    List<ArbitrageOpportunity> findActiveArbsByBookmaker(@Param("bookmakerId") Integer bookmakerId);

    // Find arbs between two specific bookmakers
    @Query("SELECT DISTINCT a FROM ArbitrageOpportunity a " +
            "WHERE EXISTS (SELECT 1 FROM ArbOutcome o1 WHERE o1.arbitrage = a AND o1.bookmakerId = :bm1) " +
            "AND EXISTS (SELECT 1 FROM ArbOutcome o2 WHERE o2.arbitrage = a AND o2.bookmakerId = :bm2) " +
            "AND a.status = 'ACTIVE'")
    List<ArbitrageOpportunity> findActiveArbsBetweenBookmakers(
            @Param("bm1") Integer bookmaker1,
            @Param("bm2") Integer bookmaker2);

    @Modifying
    void deleteByStatusAndCreatedAtBefore(ArbStatus status, LocalDateTime before);

}
