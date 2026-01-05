package com.mouse.bet.controller;

import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.service.ArbitrageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for managing arbitrage opportunities
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/arbitrage")
@RequiredArgsConstructor
@Tag(name = "Arbitrage", description = "Arbitrage opportunities management endpoints")
public class ArbitrageController {

    private static final String EMOJI_API = "🌐";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_INFO = "ℹ️";
    private static final String EMOJI_SEARCH = "🔍";

    private final ArbitrageService arbitrageService;

    /**
     * Get all arbitrage opportunities sorted by profit (descending)
     */
    @GetMapping
    @Operation(
            summary = "Get all arbitrage opportunities",
            description = "Retrieves all arbitrage opportunities sorted by profit percentage in descending order"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved arbitrage opportunities",
                    content = @Content(schema = @Schema(implementation = ArbitrageListResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    public ResponseEntity<ArbitrageListResponse> getAllArbitrage() {
        log.info("{} {} Fetching all arbitrage opportunities sorted by profit",
                EMOJI_API, EMOJI_SEARCH);

        try {
            List<ArbitrageOpportunity> opportunities = arbitrageService.findAllArbitrage();
            List<ArbitrageDTO> dtos = opportunities.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            ArbitrageListResponse response = ArbitrageListResponse.builder()
                    .success(true)
                    .count(dtos.size())
                    .opportunities(dtos)
                    .message("Successfully retrieved arbitrage opportunities")
                    .build();

            log.info("{} {} Retrieved {} arbitrage opportunities",
                    EMOJI_SUCCESS, EMOJI_API, dtos.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("{} {} Failed to retrieve arbitrage opportunities: {}",
                    EMOJI_ERROR, EMOJI_API, e.getMessage(), e);

            ArbitrageListResponse response = ArbitrageListResponse.builder()
                    .success(false)
                    .count(0)
                    .message("Failed to retrieve arbitrage opportunities: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get arbitrage opportunities with pagination
     */
    @GetMapping("/paginated")
    @Operation(
            summary = "Get paginated arbitrage opportunities",
            description = "Retrieves arbitrage opportunities with pagination and sorting"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved paginated results",
                    content = @Content(schema = @Schema(implementation = ArbitragePageResponse.class))
            )
    })
    public ResponseEntity<ArbitragePageResponse> getArbitragePaginated(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field", example = "profitPercentage")
            @RequestParam(defaultValue = "profitPercentage") String sortBy,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        log.info("{} {} Fetching paginated arbitrage: page={}, size={}, sortBy={}, direction={}",
                EMOJI_API, EMOJI_SEARCH, page, size, sortBy, sortDirection);

        try {
            Sort.Direction direction = Sort.Direction.fromString(sortDirection);
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

            Page<ArbitrageOpportunity> arbPage = arbitrageService.findAllArbitrage(pageable);
            List<ArbitrageDTO> dtos = arbPage.getContent().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            ArbitragePageResponse response = ArbitragePageResponse.builder()
                    .success(true)
                    .content(dtos)
                    .currentPage(arbPage.getNumber())
                    .totalPages(arbPage.getTotalPages())
                    .totalElements(arbPage.getTotalElements())
                    .pageSize(arbPage.getSize())
                    .hasNext(arbPage.hasNext())
                    .hasPrevious(arbPage.hasPrevious())
                    .message("Successfully retrieved paginated results")
                    .build();

            log.info("{} {} Retrieved page {} of {} ({} total items)",
                    EMOJI_SUCCESS, EMOJI_API, page, arbPage.getTotalPages(), arbPage.getTotalElements());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("{} {} Failed to retrieve paginated arbitrage: {}",
                    EMOJI_ERROR, EMOJI_API, e.getMessage(), e);

            ArbitragePageResponse response = ArbitragePageResponse.builder()
                    .success(false)
                    .message("Failed to retrieve paginated results: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get arbitrage opportunities sorted by profit with pagination
     */
    @GetMapping("/sorted")
    @Operation(
            summary = "Get arbitrage sorted by profit",
            description = "Retrieves arbitrage opportunities sorted by profit percentage in descending order with pagination"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved sorted results",
                    content = @Content(schema = @Schema(implementation = ArbitragePageResponse.class))
            )
    })
    public ResponseEntity<ArbitragePageResponse> getArbitrageSortedByProfit(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("{} {} Fetching arbitrage sorted by profit: page={}, size={}",
                EMOJI_API, EMOJI_SEARCH, page, size);

        try {
            Page<ArbitrageOpportunity> arbPage = arbitrageService.findAllArbitrageSortedByProfit(page, size);
            List<ArbitrageDTO> dtos = arbPage.getContent().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            ArbitragePageResponse response = ArbitragePageResponse.builder()
                    .success(true)
                    .content(dtos)
                    .currentPage(arbPage.getNumber())
                    .totalPages(arbPage.getTotalPages())
                    .totalElements(arbPage.getTotalElements())
                    .pageSize(arbPage.getSize())
                    .hasNext(arbPage.hasNext())
                    .hasPrevious(arbPage.hasPrevious())
                    .message("Successfully retrieved arbitrage sorted by profit")
                    .build();

            log.info("{} {} Retrieved {} opportunities sorted by profit",
                    EMOJI_SUCCESS, EMOJI_API, arbPage.getNumberOfElements());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("{} {} Failed to retrieve sorted arbitrage: {}",
                    EMOJI_ERROR, EMOJI_API, e.getMessage(), e);

            ArbitragePageResponse response = ArbitragePageResponse.builder()
                    .success(false)
                    .message("Failed to retrieve sorted results: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get arbitrage opportunity by ID
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get arbitrage by ID",
            description = "Retrieves a specific arbitrage opportunity by its database ID"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved arbitrage opportunity",
                    content = @Content(schema = @Schema(implementation = ArbitrageSingleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Arbitrage opportunity not found"
            )
    })
    public ResponseEntity<ArbitrageSingleResponse> getArbitrageById(
            @Parameter(description = "Arbitrage ID", example = "1")
            @PathVariable Long id
    ) {
        log.info("{} {} Fetching arbitrage with ID: {}", EMOJI_API, EMOJI_SEARCH, id);

        try {
            Optional<ArbitrageOpportunity> arbOpt = arbitrageService.findById(id);

            if (arbOpt.isPresent()) {
                ArbitrageDTO dto = convertToDTO(arbOpt.get());

                ArbitrageSingleResponse response = ArbitrageSingleResponse.builder()
                        .success(true)
                        .opportunity(dto)
                        .message("Successfully retrieved arbitrage opportunity")
                        .build();

                log.info("{} {} Found arbitrage with ID: {}", EMOJI_SUCCESS, EMOJI_API, id);
                return ResponseEntity.ok(response);
            } else {
                ArbitrageSingleResponse response = ArbitrageSingleResponse.builder()
                        .success(false)
                        .message("Arbitrage opportunity not found with ID: " + id)
                        .build();

                log.warn("{} {} Arbitrage not found with ID: {}", EMOJI_ERROR, EMOJI_API, id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("{} {} Failed to retrieve arbitrage by ID: {}",
                    EMOJI_ERROR, EMOJI_API, e.getMessage(), e);

            ArbitrageSingleResponse response = ArbitrageSingleResponse.builder()
                    .success(false)
                    .message("Failed to retrieve arbitrage: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get arbitrage opportunity by external ID
     */
    @GetMapping("/external/{externalId}")
    @Operation(
            summary = "Get arbitrage by external ID",
            description = "Retrieves a specific arbitrage opportunity by its external ID from Breaking-Bet"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved arbitrage opportunity",
                    content = @Content(schema = @Schema(implementation = ArbitrageSingleResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Arbitrage opportunity not found"
            )
    })
    public ResponseEntity<ArbitrageSingleResponse> getArbitrageByExternalId(
            @Parameter(description = "External arbitrage ID", example = "arb_123456")
            @PathVariable String externalId
    ) {
        log.info("{} {} Fetching arbitrage with external ID: {}", EMOJI_API, EMOJI_SEARCH, externalId);

        try {
            Optional<ArbitrageOpportunity> arbOpt = arbitrageService.findByExternalId(externalId);

            if (arbOpt.isPresent()) {
                ArbitrageDTO dto = convertToDTO(arbOpt.get());

                ArbitrageSingleResponse response = ArbitrageSingleResponse.builder()
                        .success(true)
                        .opportunity(dto)
                        .message("Successfully retrieved arbitrage opportunity")
                        .build();

                log.info("{} {} Found arbitrage with external ID: {}", EMOJI_SUCCESS, EMOJI_API, externalId);
                return ResponseEntity.ok(response);
            } else {
                ArbitrageSingleResponse response = ArbitrageSingleResponse.builder()
                        .success(false)
                        .message("Arbitrage opportunity not found with external ID: " + externalId)
                        .build();

                log.warn("{} {} Arbitrage not found with external ID: {}", EMOJI_ERROR, EMOJI_API, externalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("{} {} Failed to retrieve arbitrage by external ID: {}",
                    EMOJI_ERROR, EMOJI_API, e.getMessage(), e);

            ArbitrageSingleResponse response = ArbitrageSingleResponse.builder()
                    .success(false)
                    .message("Failed to retrieve arbitrage: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get top N most profitable arbitrage opportunities
     */
    @GetMapping("/top")
    @Operation(
            summary = "Get top profitable arbitrage opportunities",
            description = "Retrieves the top N most profitable arbitrage opportunities"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved top opportunities",
                    content = @Content(schema = @Schema(implementation = ArbitrageListResponse.class))
            )
    })
    public ResponseEntity<ArbitrageListResponse> getTopArbitrage(
            @Parameter(description = "Number of top opportunities to retrieve", example = "10")
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("{} {} Fetching top {} arbitrage opportunities", EMOJI_API, EMOJI_SEARCH, limit);

        try {
            Page<ArbitrageOpportunity> arbPage = arbitrageService.findAllArbitrageSortedByProfit(0, limit);
            List<ArbitrageDTO> dtos = arbPage.getContent().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            ArbitrageListResponse response = ArbitrageListResponse.builder()
                    .success(true)
                    .count(dtos.size())
                    .opportunities(dtos)
                    .message("Successfully retrieved top " + limit + " arbitrage opportunities")
                    .build();

            log.info("{} {} Retrieved {} top arbitrage opportunities",
                    EMOJI_SUCCESS, EMOJI_API, dtos.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("{} {} Failed to retrieve top arbitrage: {}",
                    EMOJI_ERROR, EMOJI_API, e.getMessage(), e);

            ArbitrageListResponse response = ArbitrageListResponse.builder()
                    .success(false)
                    .count(0)
                    .message("Failed to retrieve top arbitrage: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(
            summary = "Health check",
            description = "Simple health check endpoint for the arbitrage service"
    )
    public ResponseEntity<HealthResponse> healthCheck() {
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .service("ArbitrageService")
                .build());
    }

    // ============= Helper Method =============

    /**
     * Convert entity to DTO for frontend
     */
    private ArbitrageDTO convertToDTO(ArbitrageOpportunity arb) {
        List<OutcomeDTO> outcomeDTOs = arb.getOutcomes().stream()
                .map(outcome -> {
                    return OutcomeDTO.builder()
                            .id(outcome.getId())
                            .bookmakerId(outcome.getBookmakerId())
                            .bookmakerName(outcome.getBookmakerName())
                            .outcomeName(outcome.getOutcomeName())
                            .odds(outcome.getOdds())
                            .previousOdds(outcome.getPreviousOdds())
                            .stake(outcome.getStake())
                            .subEventId(outcome.getSubEventId())
                            .build();
                })
                .collect(Collectors.toList());

        return ArbitrageDTO.builder()
                .id(arb.getId())
                .externalId(arb.getExternalId())
                .eventId(arb.getEventId())
                .sport(arb.getSport())
                .sportId(arb.getSportId())
                .leagueName(arb.getLeagueName())
                .country(arb.getCountry())
                .homeTeam(arb.getHomeTeam())
                .awayTeam(arb.getAwayTeam())
                .matchStartTime(arb.getMatchStartTime())
                .isLive(arb.getIsLive())
                .matchProgress(arb.getMatchProgress())
                .marketType(arb.getMarketType())
                .profitPercentage(arb.getProfitPercentage())
                .roiPercentage(arb.getRoiPercentage())
                .status(arb.getStatus().name())
                .confidenceScore(arb.getConfidenceScore())
                .createdAt(arb.getCreatedAt())
                .updatedAt(arb.getUpdatedAt())
                .lastCheckedAt(arb.getLastCheckedAt())
                .outcomes(outcomeDTOs)
                .build();
    }

    // ============= Response DTOs =============

    @lombok.Data
    @lombok.Builder
    @Schema(description = "Arbitrage opportunity DTO for frontend")
    public static class ArbitrageDTO {
        @Schema(description = "Database ID")
        private Long id;

        @Schema(description = "External ID from Breaking-Bet")
        private String externalId;

        @Schema(description = "Event ID")
        private String eventId;

        @Schema(description = "Sport name")
        private String sport;

        @Schema(description = "Sport ID")
        private Integer sportId;

        @Schema(description = "League name")
        private String leagueName;

        @Schema(description = "Country")
        private String country;

        @Schema(description = "Home team name")
        private String homeTeam;

        @Schema(description = "Away team name")
        private String awayTeam;

        @Schema(description = "Match start time")
        private LocalDateTime matchStartTime;

        @Schema(description = "Is live match")
        private Boolean isLive;

        @Schema(description = "Match progress")
        private String matchProgress;

        @Schema(description = "Market type")
        private String marketType;

        @Schema(description = "Profit percentage")
        private BigDecimal profitPercentage;

        @Schema(description = "ROI percentage")
        private BigDecimal roiPercentage;

        @Schema(description = "Status")
        private String status;

        @Schema(description = "Confidence score")
        private BigDecimal confidenceScore;

        @Schema(description = "Created timestamp")
        private LocalDateTime createdAt;

        @Schema(description = "Updated timestamp")
        private LocalDateTime updatedAt;

        @Schema(description = "Last checked timestamp")
        private LocalDateTime lastCheckedAt;

        @Schema(description = "Outcomes with bookmaker and odds information")
        private List<OutcomeDTO> outcomes;
    }

    @lombok.Data
    @lombok.Builder
    @Schema(description = "Outcome DTO with bookmaker and odds")
    public static class OutcomeDTO {
        @Schema(description = "Outcome ID")
        private Long id;

        @Schema(description = "Bookmaker ID")
        private Integer bookmakerId;

        @Schema(description = "Bookmaker name")
        private BookMaker bookmakerName;

        @Schema(description = "Outcome name (e.g., Side 1, Side 2)")
        private String outcomeName;

        @Schema(description = "Current odds")
        private BigDecimal odds;

        @Schema(description = "Previous odds")
        private BigDecimal previousOdds;

        @Schema(description = "Stake amount")
        private BigDecimal stake;

        @Schema(description = "Sub-event ID")
        private String subEventId;
    }

    @lombok.Data
    @lombok.Builder
    @Schema(description = "Response containing list of arbitrage opportunities")
    public static class ArbitrageListResponse {
        @Schema(description = "Whether the request was successful")
        private boolean success;

        @Schema(description = "Number of opportunities returned")
        private Integer count;

        @Schema(description = "List of arbitrage opportunities")
        private List<ArbitrageDTO> opportunities;

        @Schema(description = "Response message")
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    @Schema(description = "Response containing paginated arbitrage opportunities")
    public static class ArbitragePageResponse {
        @Schema(description = "Whether the request was successful")
        private boolean success;

        @Schema(description = "List of arbitrage opportunities in current page")
        private List<ArbitrageDTO> content;

        @Schema(description = "Current page number (0-indexed)")
        private Integer currentPage;

        @Schema(description = "Total number of pages")
        private Integer totalPages;

        @Schema(description = "Total number of elements across all pages")
        private Long totalElements;

        @Schema(description = "Number of items per page")
        private Integer pageSize;

        @Schema(description = "Whether there is a next page")
        private Boolean hasNext;

        @Schema(description = "Whether there is a previous page")
        private Boolean hasPrevious;

        @Schema(description = "Response message")
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    @Schema(description = "Response containing single arbitrage opportunity")
    public static class ArbitrageSingleResponse {
        @Schema(description = "Whether the request was successful")
        private boolean success;

        @Schema(description = "Arbitrage opportunity")
        private ArbitrageDTO opportunity;

        @Schema(description = "Response message")
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    @Schema(description = "Health check response")
    public static class HealthResponse {
        @Schema(description = "Service status")
        private String status;

        @Schema(description = "Service name")
        private String service;
    }
}