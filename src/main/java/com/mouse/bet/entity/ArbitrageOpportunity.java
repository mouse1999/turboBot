package com.mouse.bet.entity;

import com.mouse.bet.enums.ArbStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalized arbitrage entity with separate outcomes
 * This design prevents bookmaker position confusion
 */
@Entity
@Table(name = "arbitrage_opportunities", indexes = {
        @Index(name = "idx_external_id", columnList = "external_id"),
        @Index(name = "idx_profit", columnList = "profit_percentage"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_sport", columnList = "sport"),
        @Index(name = "idx_is_live", columnList = "is_live")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArbitrageOpportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true, length = 500)
    private String externalId;

    @Column(name = "event_id", length = 100)
    private String eventId;

    // Sport Information
    @Column(nullable = false, length = 100)
    private String sport;

    @Column(name = "sport_id")
    private Integer sportId;

    @Column(name = "league_name", length = 200)
    private String leagueName;

    @Column(length = 100)
    private String country;

    // Match Information
    @Column(name = "home_team", nullable = false, length = 200)
    private String homeTeam;

    @Column(name = "away_team", nullable = false, length = 200)
    private String awayTeam;

    @Column(name = "match_start_time")
    private LocalDateTime matchStartTime;

    @Column(name = "is_live", nullable = false)
    private Boolean isLive;

    @Column(name = "match_progress", length = 100)
    private String matchProgress;

    // Market Information
    @Column(name = "market_type", nullable = false, length = 100)
    private String marketType;

    // Profit Calculation
    @Column(name = "profit_percentage", nullable = false, precision = 10, scale = 4)
    private BigDecimal profitPercentage;

    @Column(name = "roi_percentage", precision = 10, scale = 4)
    private BigDecimal roiPercentage;

    // Status and Tracking
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArbStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    // Metadata
    @Column(name = "arb_age_seconds")
    private Long arbAgeSeconds;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @Column(name = "update_count")
    private Integer updateCount;

    // ✅ NORMALIZED: Separate outcomes collection
    // Bookmaker positions don't matter - matched by bookmaker_id
    @OneToMany(mappedBy = "arbitrage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ArbOutcome> outcomes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ArbStatus.ACTIVE;
        }
        if (updateCount == null) {
            updateCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (updateCount != null) {
            updateCount++;
        }
    }

    // Helper methods
    public void addOutcome(ArbOutcome outcome) {
        outcomes.add(outcome);
        outcome.setArbitrage(this);
    }

    public boolean isTwoWay() {
        return outcomes != null && outcomes.size() == 2;
    }

    public boolean isValid() {
        return status == ArbStatus.ACTIVE &&
                (expiredAt == null || expiredAt.isAfter(LocalDateTime.now()));
    }

//    public BigDecimal calculateProfitAmount() {
//        if (outcomes == null || outcomes.isEmpty() || profitPercentage == null) {
//            return BigDecimal.ZERO;
//        }
//
//        BigDecimal totalStake = outcomes.stream()
//                .map(ArbOutcome::getStake)
//                .filter(stake -> stake != null)
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        return totalStake.multiply(profitPercentage)
//                .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
//    }

    public String getSummary() {
        return String.format("%s vs %s | %s | %.2f%% profit",
                homeTeam, awayTeam, sport, profitPercentage);
    }
}
