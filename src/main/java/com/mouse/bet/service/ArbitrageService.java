package com.mouse.bet.service;

import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.repository.ArbitrageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArbitrageService {
    private final ArbitrageRepository arbitrageRepository;

    /**
     * Find all arbitrage opportunities sorted by profit percentage (descending)
     */
    @Transactional(readOnly = true)
    public List<ArbitrageOpportunity> findAllArbitrage() {
        return arbitrageRepository.findAll(Sort.by(Sort.Direction.DESC, "profitPercentage"));
    }

    /**
     * Find all arbitrage opportunities with pagination and sorting
     */
    @Transactional(readOnly = true)
    public Page<ArbitrageOpportunity> findAllArbitrage(Pageable pageable) {
        return arbitrageRepository.findAll(pageable);
    }

    /**
     * Find arbitrage by ID
     */
    @Transactional(readOnly = true)
    public Optional<ArbitrageOpportunity> findById(Long id) {
        return arbitrageRepository.findById(id);
    }

    /**
     * Find arbitrage by external ID
     */
    @Transactional(readOnly = true)
    public Optional<ArbitrageOpportunity> findByExternalId(String externalId) {
        return arbitrageRepository.findByExternalId(externalId);
    }

    /**
     * Find all arbitrage opportunities sorted by profit (descending) with pagination
     */
    @Transactional(readOnly = true)
    public Page<ArbitrageOpportunity> findAllArbitrageSortedByProfit(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "profitPercentage"));
        return arbitrageRepository.findAll(pageable);
    }
}
