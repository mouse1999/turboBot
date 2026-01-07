package com.mouse.bet.service;


import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.repository.ArbOutcomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArbOutcomeService {

    private final ArbOutcomeRepository arbOutcomeRepository;

    /* ===================== CREATE / UPDATE ===================== */

    @Transactional
    public ArbOutcome save(ArbOutcome arbOutcome) {
        return arbOutcomeRepository.save(arbOutcome);
    }

    @Transactional
    public List<ArbOutcome> saveAll(List<ArbOutcome> outcomes) {
        return arbOutcomeRepository.saveAll(outcomes);
    }

    /* ===================== READ ===================== */

    public Optional<ArbOutcome> findByExternalIdAndBookmaker(
            String externalId,
            Integer bookmakerId
    ) {
        return arbOutcomeRepository
                .findByArbitrageExternalIdAndBookmakerId(externalId, bookmakerId);
    }

    public List<ArbOutcome> findByArbitrageId(Long arbId) {
        return arbOutcomeRepository.findByArbitrageId(arbId);
    }

    public List<ArbOutcome> findByBookmaker(Integer bookmakerId) {
        return arbOutcomeRepository.findByBookmakerId(bookmakerId);
    }

    public List<ArbOutcome> findByArbitrageAndBookmaker(
            Long arbId,
            Integer bookmakerId
    ) {
        return arbOutcomeRepository
                .findByArbitrageIdAndBookmakerId(arbId, bookmakerId);
    }

    /* ===================== DELETE ===================== */

    @Transactional
    public void deleteByArbitrageId(Long arbId) {
        arbOutcomeRepository.deleteByArbitrageId(arbId);
    }

    @Transactional
    public void deleteByArbitrageIdExplicit(Long arbId) {
        arbOutcomeRepository.deleteByArbitrageIdQuery(arbId);
    }
}

