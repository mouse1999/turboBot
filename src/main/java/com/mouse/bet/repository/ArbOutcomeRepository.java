package com.mouse.bet.repository;

import com.mouse.bet.entity.ArbOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ArbOutcomeRepository extends JpaRepository<ArbOutcome, Long> {

    // ✅ CORRECT: Use "arbitrage" (the field name in entity)
    @Transactional
    @Modifying
    void deleteByArbitrageId(Long arbId);

    // Alternative with @Query (more explicit)
    @Transactional
    @Modifying
    @Query("DELETE FROM ArbOutcome a WHERE a.arbitrage.id = :arbId")
    void deleteByArbitrageIdQuery(@Param("arbId") Long arbId);

    // Find outcomes by arbitrage ID
    List<ArbOutcome> findByArbitrageId(Long arbId);

    // Find outcomes by bookmaker
    List<ArbOutcome> findByBookmakerId(Integer bookmakerId);

    // Find by arbitrage and bookmaker
    List<ArbOutcome> findByArbitrageIdAndBookmakerId(Long arbId, Integer bookmakerId);
}