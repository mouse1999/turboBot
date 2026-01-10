package com.mouse.bet.service;

import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.repository.ArbitrageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrageService {

    private final ArbitrageRepository arbitrageRepository;

    // Freshness threshold in seconds (2 seconds)
    private static final long FRESHNESS_THRESHOLD_SECONDS = 2;

    /**
     * Get cutoff time for fresh arbs (NOW - 2 seconds)
     */
    private LocalDateTime getFreshnessCutoff() {
        return LocalDateTime.now().minusSeconds(FRESHNESS_THRESHOLD_SECONDS);
    }

    /**
     * Find all FRESH arbitrage opportunities sorted by profit percentage (descending)
     */
    @Transactional(readOnly = true)
    public List<ArbitrageOpportunity> findAllArbitrage() {
        LocalDateTime cutoff = getFreshnessCutoff();
//        return arbitrageRepository.findAll().stream()
//                .filter(arb -> arb.getUpdatedAt() != null && arb.getUpdatedAt().isAfter(cutoff))
//                .sorted((a, b) -> b.getProfitPercentage().compareTo(a.getProfitPercentage()))
//                .collect(Collectors.toList());
        return arbitrageRepository.findAll(cutoff);
    }


    public void saveArbitrageOpportunity(ArbitrageOpportunity arbitrageOpportunity) {
        arbitrageRepository.save(arbitrageOpportunity);
    }

    /**
     * Find all FRESH arbitrage opportunities with pagination and sorting
     */
    @Transactional(readOnly = true)
    public Page<ArbitrageOpportunity> findAllArbitrage(Pageable pageable) {
        LocalDateTime cutoff = getFreshnessCutoff();
        List<ArbitrageOpportunity> freshArbs = arbitrageRepository.findAll().stream()
                .filter(arb -> arb.getUpdatedAt() != null && arb.getUpdatedAt().isAfter(cutoff))
                .collect(Collectors.toList());

        // Apply sorting from pageable
        if (pageable.getSort().isSorted()) {
            freshArbs = applySorting(freshArbs, pageable.getSort());
        }

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), freshArbs.size());
        List<ArbitrageOpportunity> pageContent = freshArbs.subList(start, end);

        return new PageImpl<>(pageContent, pageable, freshArbs.size());
    }

    /**
     * Find all FRESH active arbitrage opportunities
     */
    @Transactional(readOnly = true)
    public List<ArbitrageOpportunity> findAllActiveArbitrage() {
        return arbitrageRepository.findFreshActiveArbs();
    }

    /**
     * Find FRESH active arbs with pagination
     */
    @Transactional(readOnly = true)
    public Page<ArbitrageOpportunity> findAllActiveArbitrage(Pageable pageable) {
        List<ArbitrageOpportunity> freshArbs = arbitrageRepository.findFreshActiveArbs();

        // Apply sorting
        if (pageable.getSort().isSorted()) {
            freshArbs = applySorting(freshArbs, pageable.getSort());
        }

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), freshArbs.size());
        List<ArbitrageOpportunity> pageContent = freshArbs.subList(start, end);

        return new PageImpl<>(pageContent, pageable, freshArbs.size());
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
     * Find all FRESH arbitrage opportunities sorted by profit (descending) with pagination
     */
    @Transactional(readOnly = true)
    public Page<ArbitrageOpportunity> findAllArbitrageSortedByProfit(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "profitPercentage"));
        return findAllArbitrage(pageable);
    }

    /**
     * Find FRESH active live arbs
     */
    @Transactional(readOnly = true)
    public List<ArbitrageOpportunity> findActiveLiveArbs() {
        LocalDateTime cutoff = getFreshnessCutoff();
        return arbitrageRepository.findActiveLiveArbs().stream()
                .filter(arb -> arb.getUpdatedAt() != null && arb.getUpdatedAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    /**
     * Find FRESH arbs by sport
     */
    @Transactional(readOnly = true)
    public List<ArbitrageOpportunity> findActiveArbsBySport(String sport) {
        return arbitrageRepository.findActiveArbsBySportFresh(sport);
    }

    /**
     * Find FRESH arbs with minimum profit
     */
    @Transactional(readOnly = true)
    public List<ArbitrageOpportunity> findActiveArbsWithMinProfit(BigDecimal minProfit) {
        LocalDateTime cutoff = getFreshnessCutoff();
        return arbitrageRepository.findActiveArbsWithMinProfit(minProfit).stream()
                .filter(arb -> arb.getUpdatedAt() != null && arb.getUpdatedAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    /**
     * Find FRESH arbs by bookmaker
     */
    @Transactional(readOnly = true)
    public List<ArbitrageOpportunity> findActiveArbsByBookmaker(Integer bookmakerId) {
        LocalDateTime cutoff = getFreshnessCutoff();
        return arbitrageRepository.findActiveArbsByBookmaker(bookmakerId).stream()
                .filter(arb -> arb.getUpdatedAt() != null && arb.getUpdatedAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    /**
     * Find FRESH arbs between two bookmakers
     */
    @Transactional(readOnly = true)
    public List<ArbitrageOpportunity> findActiveArbsBetweenBookmakers(Integer bookmaker1, Integer bookmaker2) {
        LocalDateTime cutoff = getFreshnessCutoff();
        return arbitrageRepository.findActiveArbsBetweenBookmakers(bookmaker1, bookmaker2).stream()
                .filter(arb -> arb.getUpdatedAt() != null && arb.getUpdatedAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    /**
     * Get stale arbs (for monitoring/debugging)
     */
    @Transactional(readOnly = true)
    public List<ArbitrageOpportunity> findStaleActiveArbs() {
        return arbitrageRepository.findStaleActiveArbs();
    }

    /**
     * Get statistics
     */
    @Transactional(readOnly = true)
    public ArbStatistics getStatistics() {
        long totalActive = arbitrageRepository.countActiveArbs();
        long freshActive = arbitrageRepository.findFreshActiveArbs().size();
        long staleActive = arbitrageRepository.findStaleActiveArbs().size();
        BigDecimal avgProfit = arbitrageRepository.getAverageProfitPercentage();

        return new ArbStatistics(totalActive, freshActive, staleActive, avgProfit);
    }

    /**
     * Scheduled cleanup of stale arbs (runs every 5 seconds)
     */
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void cleanupStaleArbs() {
        int expired = arbitrageRepository.expireStaleArbs();
        if (expired > 0) {
            log.warn("⚠️ Expired {} stale arbitrage opportunities (not updated in last {} seconds)",
                    expired, FRESHNESS_THRESHOLD_SECONDS);
        }
    }

    /**
     * Manual cleanup trigger
     */
    @Transactional
    public int expireStaleArbs() {
        int expired = arbitrageRepository.expireStaleArbs();
        log.info("✅ Manually expired {} stale arbitrage opportunities", expired);
        return expired;
    }

    /**
     * Helper method to apply sorting to a list
     */
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
                    // Default to profit percentage descending
                    arbs.sort((a, b) -> b.getProfitPercentage().compareTo(a.getProfitPercentage()));
            }
        }
        return arbs;
    }

    /**
     * Statistics DTO
     */
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