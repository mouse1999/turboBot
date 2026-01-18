package com.mouse.bet.repository;

import com.mouse.bet.entity.ArbOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArbOutcomeRepository extends JpaRepository<ArbOutcome, Long> {

    // ✅ DELETE operations
    @Transactional
    @Modifying
    void deleteByArbitrageId(Long arbId);

    @Transactional
    @Modifying
    @Query("DELETE FROM ArbOutcome a WHERE a.arbitrage.id = :arbId")
    void deleteByArbitrageIdQuery(@Param("arbId") Long arbId);

    // ✅ FIXED: Add JOIN FETCH to eagerly load arbitrage relationship
    @Query("SELECT ao FROM ArbOutcome ao " +
            "JOIN FETCH ao.arbitrage a " +
            "WHERE a.externalId = :externalId AND ao.bookmakerId = :bookmakerId")
    Optional<ArbOutcome> findByArbitrageExternalIdAndBookmakerId(
            @Param("externalId") String externalId,
            @Param("bookmakerId") Integer bookmakerId
    );

    // ✅ FIXED: Add JOIN FETCH for arbitrage queries
    @Query("SELECT ao FROM ArbOutcome ao " +
            "JOIN FETCH ao.arbitrage " +
            "WHERE ao.arbitrage.id = :arbId")
    List<ArbOutcome> findByArbitrageId(@Param("arbId") Long arbId);

    // This one doesn't need JOIN FETCH since it doesn't use arbitrage in the query
    List<ArbOutcome> findByBookmakerId(Integer bookmakerId);

    // ✅ FIXED: Add JOIN FETCH
    @Query("SELECT ao FROM ArbOutcome ao " +
            "JOIN FETCH ao.arbitrage " +
            "WHERE ao.arbitrage.id = :arbId AND ao.bookmakerId = :bookmakerId")
    List<ArbOutcome> findByArbitrageIdAndBookmakerId(
            @Param("arbId") Long arbId,
            @Param("bookmakerId") Integer bookmakerId
    );
}