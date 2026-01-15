package com.mouse.bet.entity;

import com.mouse.bet.converter.ResultMapConverter;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.orchestrator.model.LegResult;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized arbitrage entity with age calculation
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

    // ✅ CRITICAL: Add @Version for optimistic locking
    @Version
    @Column(name = "version")
    private Long version;


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

    private String outCome;

    // Profit Calculation
    @Column(name = "profit_percentage", nullable = false, precision = 10, scale = 4)
    private BigDecimal profitPercentage;

    @Column(name = "roi_percentage", precision = 10, scale = 4)
    private BigDecimal roiPercentage;

    @Convert(converter = ResultMapConverter.class)
    @Column(name = "result_map_json", columnDefinition = "TEXT")
    @Builder.Default
    private Map<BookMaker, LegResult> resultMap = new HashMap<>();


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
        // Calculate initial age (will be 0)
        calculateAge();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (updateCount != null) {
            updateCount++;
        }
        // Recalculate age on every update
        calculateAge();
    }

    /**
     * Calculate arbitrage age in seconds
     * Age = time elapsed since creation
     */
    public void calculateAge() {
        if (createdAt != null) {
            this.arbAgeSeconds = Duration.between(createdAt, LocalDateTime.now()).getSeconds();
        } else {
            this.arbAgeSeconds = 0L;
        }
    }

    /**
     * Get current age (call this for real-time age without saving)
     */
    public Long getCurrentAge() {
        if (createdAt != null) {
            return Duration.between(createdAt, LocalDateTime.now()).getSeconds();
        }
        return 0L;
    }

    /**
     * Get age in minutes
     */
    public Long getAgeInMinutes() {
        return arbAgeSeconds != null ? arbAgeSeconds / 60 : 0L;
    }

    /**
     * Get age in hours
     */
    public Double getAgeInHours() {
        return arbAgeSeconds != null ? arbAgeSeconds / 3600.0 : 0.0;
    }

    /**
     * Check if arb is stale (older than threshold)
     */
    public boolean isStale(long thresholdSeconds) {
        Long currentAge = getCurrentAge();
        return currentAge > thresholdSeconds;
    }

    /**
     * Get human-readable age string
     */
    public String getAgeFormatted() {
        Long age = getCurrentAge();
        if (age < 60) {
            return age + " seconds";
        } else if (age < 3600) {
            return (age / 60) + " minutes";
        } else {
            return String.format("%.1f hours", age / 3600.0);
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

    public String getSummary() {
        return String.format("%s vs %s | %s | %.2f%% profit | Age: %s",
                homeTeam, awayTeam, sport, profitPercentage, getAgeFormatted());
    }
}