package com.mouse.bet.entity;

import com.mouse.bet.enums.BookMaker;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outcome entity - represents one side of the arbitrage
 * Position-independent - matched by bookmaker_id
 */
@Entity
@Table(name = "arb_outcomes", indexes = {
        @Index(name = "idx_arb_id", columnList = "arbitrage_id"),
        @Index(name = "idx_bookmaker", columnList = "bookmaker_id"),
        @Index(name = "idx_arb_bookmaker", columnList = "arbitrage_id, bookmaker_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArbOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link to parent arbitrage
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arbitrage_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ArbitrageOpportunity arbitrage;

    // Bookmaker info (KEY IDENTIFIER for matching)
    @Column(name = "bookmaker_id", nullable = false)
    private Integer bookmakerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bookmaker_name", nullable = false, length = 100)
    private BookMaker bookmakerName;

    @Column(name = "home_team", nullable = false, length = 200)
    private String homeTeam;

    @Column(name = "away_team", nullable = false, length = 200)
    private String awayTeam;

    private String marketType;

    // Outcome details
    private String outComeName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal odds;

    @Column(name = "previous_odds", precision = 10, scale = 2)
    private BigDecimal previousOdds;

    @Column(precision = 10, scale = 2)
    private BigDecimal stake;

    // Sub-event details from Breaking-Bet
    @Column(name = "sub_event_id", length = 100)
    private String subEventId;

    @Column(name = "original_id", length = 100)
    private String originalId;

    @Column(length = 50)
    private String sport;

    @Column(length = 100)
    private String progress;

    @Column
    private Boolean reordered;

    @Column
    private Boolean initiator;
    private String leagueName;

    // Timestamps
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}