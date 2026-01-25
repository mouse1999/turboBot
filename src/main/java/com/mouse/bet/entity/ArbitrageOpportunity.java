package com.mouse.bet.entity;

import com.mouse.bet.converter.ResultMapConverter;
import com.mouse.bet.enums.ArbStatus;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.orchestrator.model.LegResult;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized arbitrage entity with age calculation and reset logic
 * Age resets if update gap exceeds 4 seconds (maintains consistency for 2-second updates)
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
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class ArbitrageOpportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @OneToMany(mappedBy = "arbitrage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ArbOutcome> outcomes = new ArrayList<>();

    // ✅ Constants for age reset logic
    private static final long UPDATE_INTERVAL_SECONDS = 2L;
    private static final long MAX_UPDATE_GAP_SECONDS = 4L;

    // In ArbitrageOpportunity.java

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
        calculateAge();
    }

    // ✅ Public method, NOT @PreUpdate
    public void onUpdate() {
        LocalDateTime now = LocalDateTime.now();

        // Check if update gap exceeds threshold (more than 4 seconds)
        if (updatedAt != null) {
            long secondsSinceLastUpdate = Duration.between(updatedAt, now).getSeconds();

            if (secondsSinceLastUpdate > MAX_UPDATE_GAP_SECONDS) {
                // Reset createdAt to maintain consistent age
                createdAt = now;
                updateCount = 0;

                log.debug("🔄 Age reset for arb {} - update gap was {} seconds",
                        externalId, secondsSinceLastUpdate);
            }
        }

        updatedAt = now;

        if (updateCount != null) {
            updateCount++;
        }

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
     * Check if last update was consistent (within expected interval)
     */
    public boolean isUpdateConsistent() {
        if (updatedAt == null) {
            return false;
        }
        long secondsSinceLastUpdate = Duration.between(updatedAt, LocalDateTime.now()).getSeconds();
        return secondsSinceLastUpdate <= MAX_UPDATE_GAP_SECONDS;
    }

    /**
     * Get seconds since last update
     */
    public Long getSecondsSinceLastUpdate() {
        if (updatedAt == null) {
            return null;
        }
        return Duration.between(updatedAt, LocalDateTime.now()).getSeconds();
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

    /**
     * Check if this arb was recently reset (age < 5 seconds and updateCount < 3)
     */
    public boolean wasRecentlyReset() {
        return getCurrentAge() < 5 && (updateCount == null || updateCount < 3);
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

    /**
     * Get detailed summary including outcome information
     * Format: "Team1 vs Team2 | Sport | Bookmaker1 (odds1) vs Bookmaker2 (odds2) | Profit% | Age"
     */
    public String getDetailedSummary() {
        if (outcomes == null || outcomes.isEmpty()) {
            return getSummary() + " | No outcomes";
        }

        StringBuilder sb = new StringBuilder();

        // Basic info
        sb.append(String.format("%s vs %s | %s | ", homeTeam, awayTeam, sport));

        // Outcome details
        for (int i = 0; i < outcomes.size(); i++) {
            ArbOutcome outcome = outcomes.get(i);
            sb.append(String.format("%s (%.2f)",
                    outcome.getBookmakerName(),
                    outcome.getOdds()));

            if (i < outcomes.size() - 1) {
                sb.append(" vs ");
            }
        }

        // Profit and age
        sb.append(String.format(" | %.2f%% profit | Age: %s",
                profitPercentage, getAgeFormatted()));

        return sb.toString();
    }

    /**
     * Get full outcome breakdown with market types and selections
     * Format: Multi-line with each outcome on separate line
     */
    public String getOutcomeBreakdown() {
        if (outcomes == null || outcomes.isEmpty()) {
            return String.format("Arb: %s vs %s | No outcomes available", homeTeam, awayTeam);
        }

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(String.format("=== %s vs %s | %s ===\n", homeTeam, awayTeam, sport));
        sb.append(String.format("Profit: %.2f%% | ROI: %s | Age: %s | Status: %s\n",
                profitPercentage,
                roiPercentage != null ? String.format("%.2f%%", roiPercentage) : "N/A",
                getAgeFormatted(),
                status));
        sb.append(String.format("Market: %s | Outcome: %s\n", marketType, outCome));
        sb.append("\nOutcomes:\n");

        // Each outcome
        for (int i = 0; i < outcomes.size(); i++) {
            ArbOutcome outcome = outcomes.get(i);
            sb.append(String.format("  %d. %s (ID: %d)\n",
                    i + 1,
                    outcome.getBookmakerName(),
                    outcome.getBookmakerId()));
            sb.append(String.format("     Teams: %s vs %s\n",
                    outcome.getHomeTeam(),
                    outcome.getAwayTeam()));
            sb.append(String.format("     Market: %s | Selection: %s\n",
                    outcome.getMarketType() != null ? outcome.getMarketType() : "N/A",
                    outcome.getOutComeName() != null ? outcome.getOutComeName() : "N/A"));
            sb.append(String.format("     Odds: %.2f", outcome.getOdds()));

            if (outcome.getPreviousOdds() != null) {
                sb.append(String.format(" (was: %.2f)", outcome.getPreviousOdds()));
            }

            if (outcome.getStake() != null) {
                sb.append(String.format(" | Stake: %.2f", outcome.getStake()));
            }

            if (outcome.getInitiator() != null && outcome.getInitiator()) {
                sb.append(" [INITIATOR]");
            }

            sb.append("\n");

            if (outcome.getProgress() != null) {
                sb.append(String.format("     Progress: %s\n", outcome.getProgress()));
            }

            if (outcome.getLeagueName() != null) {
                sb.append(String.format("     League: %s\n", outcome.getLeagueName()));
            }
        }

        return sb.toString();
    }
}