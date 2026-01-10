package com.mouse.bet.controller;

import com.mouse.bet.dto.QueueArbRequest;
import com.mouse.bet.entity.ArbOutcome;
import com.mouse.bet.entity.ArbitrageOpportunity;
import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.orchestrator.Orchestrator;
import com.mouse.bet.repository.ArbitrageRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * REST Controller for managing and monitoring the Orchestrator
 */
@Slf4j
@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
public class OrchestratorController {

    private final Orchestrator orchestrator;
    private final ArbitrageRepository arbitrageRepository;

    /**
     * Queue an arbitrage opportunity with custom stakes
     * POST /api/orchestrator/queue-with-stakes
     * Body: {
     *   "externalId": "ARB-2025-001",  // or "id": 12345
     *   "stakes": {
     *     "BET365": 476.19,
     *     "BETWAY": 285.71,
     *     "SPORTYBET": 250.00
     *   }
     * }
     */
    @PostMapping("/queue-with-stakes")
    public ResponseEntity<Map<String, Object>> queueWithStakes(@Valid @RequestBody QueueArbRequest request) {

        // Validate request has identifier
        if (!request.hasValidIdentifier()) {
            log.warn("Queue request rejected - missing id and externalId");

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid request");
            errorResponse.put("message", "Either 'id' or 'externalId' is required");
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validate stakes are provided
        if (request.getStakes() == null || request.getStakes().isEmpty()) {
            log.warn("Queue request rejected - missing stakes");

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid request");
            errorResponse.put("message", "Stakes are required");
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.badRequest().body(errorResponse);
        }

        String identifier = request.getExternalId() != null ?
                "externalId=" + request.getExternalId() : "id=" + request.getId();

        log.info("Attempting to queue arbitrage with custom stakes | {} | Stakes: {}",
                identifier, request.getStakes());

        try {
            // Fetch arbitrage from repository
            Optional<ArbitrageOpportunity> arbOptional;

            if (request.getExternalId() != null) {
                arbOptional = arbitrageRepository.findByExternalId(request.getExternalId());
            } else {
                arbOptional = arbitrageRepository.findById(request.getId());
            }

            if (arbOptional.isEmpty()) {
                log.warn("Arbitrage not found | {}", identifier);

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Arbitrage not found");
                errorResponse.put("message", "No arbitrage found with " + identifier);
                errorResponse.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            ArbitrageOpportunity arb = arbOptional.get();

            // Validate arbitrage has outcomes
            if (arb.getOutcomes() == null || arb.getOutcomes().isEmpty()) {
                log.warn("Arbitrage has no outcomes | {} | ArbId: {}", identifier, arb.getId());

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid arbitrage");
                errorResponse.put("message", "Arbitrage has no outcomes");
                errorResponse.put("arbId", arb.getId());
                errorResponse.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Update stakes for each outcome
            Map<BookMaker, BigDecimal> updatedStakes = new HashMap<>();
            Set<BookMaker> missingStakes = new java.util.HashSet<>();

            for (ArbOutcome outcome : arb.getOutcomes()) {
                BookMaker bookmaker = outcome.getBookmakerName();

                if (request.hasStakeForBookmaker(bookmaker)) {
                    BigDecimal newStake = request.getStakeForBookmaker(bookmaker);
                    BigDecimal oldStake = outcome.getStake();

                    outcome.setStake(newStake);
                    updatedStakes.put(bookmaker, newStake);

                    log.info("Updated stake | ArbId: {} | Bookmaker: {} | OldStake: {} | NewStake: {}",
                            arb.getId(), bookmaker, oldStake, newStake);
                } else {
                    missingStakes.add(bookmaker);
                    log.warn("No stake provided for outcome | ArbId: {} | Bookmaker: {}",
                            arb.getId(), bookmaker);
                }
            }

            // Validate all stakes were provided if required
            if (request.isValidateAllStakes() && !missingStakes.isEmpty()) {
                log.warn("Missing stakes for some bookmakers | ArbId: {} | MissingStakes: {}",
                        arb.getId(), missingStakes);

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Missing stakes");
                errorResponse.put("message", "Stakes required for all bookmakers: " + missingStakes);
                errorResponse.put("missingBookmakers", missingStakes);
                errorResponse.put("arbId", arb.getId());
                errorResponse.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Save updated arbitrage
            arbitrageRepository.save(arb);

            log.info("Stakes updated successfully | ArbId: {} | UpdatedStakes: {} | TotalStake: {}",
                    arb.getId(), updatedStakes, request.getTotalStake());

            // Try to queue (non-blocking)
            boolean queued = orchestrator.tryLoadArb(arb);

            if (queued) {
                log.info("Arbitrage queued successfully | ArbId: {} | ExternalId: {} | Profit: {}% | TotalStake: {}",
                        arb.getId(), arb.getExternalId(), arb.getProfitPercentage(), request.getTotalStake());

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Arbitrage queued successfully with custom stakes");
                response.put("arbitrage", Map.of(
                        "id", arb.getId(),
                        "externalId", arb.getExternalId(),
                        "profitPercentage", arb.getProfitPercentage(),
                        "status", arb.getStatus(),
                        "outcomesCount", arb.getOutcomes().size()
                ));
                response.put("stakes", updatedStakes);
                response.put("totalStake", request.getTotalStake());
                response.put("queueStats", orchestrator.getQueueStats());
                response.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.ok(response);

            } else {
                log.warn("Failed to queue arbitrage (queue full) | ArbId: {} | ExternalId: {}",
                        arb.getId(), arb.getExternalId());

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Queue full");
                errorResponse.put("message", "Orchestrator queue is currently full. Try again later.");
                errorResponse.put("arbId", arb.getId());
                errorResponse.put("externalId", arb.getExternalId());
                errorResponse.put("queueStats", orchestrator.getQueueStats());
                errorResponse.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
            }

        } catch (Exception e) {
            log.error("Error queuing arbitrage with stakes | {} | Error: {}",
                    identifier, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Queue an arbitrage opportunity by ID
     * POST /api/orchestrator/queue/by-id
     * Body: { "id": 12345 }
     */
    @PostMapping("/queue/by-id")
    public ResponseEntity<Map<String, Object>> queueById(@RequestBody Map<String, Long> request) {
        Long id = request.get("id");

        if (id == null) {
            log.warn("Queue request rejected - missing id");

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid request");
            errorResponse.put("message", "id is required");
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.badRequest().body(errorResponse);
        }

        log.info("Attempting to queue arbitrage by id | Id: {}", id);

        try {
            // Fetch arbitrage from repository
            Optional<ArbitrageOpportunity> arbOptional = arbitrageRepository.findById(id);

            if (arbOptional.isEmpty()) {
                log.warn("Arbitrage not found -|- Id: {}", id);

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Arbitrage not found");
                errorResponse.put("message", "No arbitrage found with id: " + id);
                errorResponse.put("id", id);
                errorResponse.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            ArbitrageOpportunity arb = arbOptional.get();

            // Validate arbitrage has outcomes
            if (arb.getOutcomes() == null || arb.getOutcomes().isEmpty()) {
                log.warn("Arbitrage has no outcomes | Id: {} | ExternalId: {}", id, arb.getExternalId());

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid arbitrage");
                errorResponse.put("message", "Arbitrage has no outcomes");
                errorResponse.put("id", id);
                errorResponse.put("externalId", arb.getExternalId());
                errorResponse.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Try to queue (non-blocking)
            boolean queued = orchestrator.tryLoadArb(arb);

            if (queued) {
                log.info("Arbitrage queued successfully | Id: {} | ExternalId: {} | Profit: {}%",
                        id, arb.getExternalId(), arb.getProfitPercentage());

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Arbitrage queued successfully");
                response.put("arbitrage", Map.of(
                        "id", arb.getId(),
                        "externalId", arb.getExternalId(),
                        "profitPercentage", arb.getProfitPercentage(),
                        "status", arb.getStatus(),
                        "outcomesCount", arb.getOutcomes().size(),
                        "homeTeam", arb.getHomeTeam() != null ? arb.getHomeTeam() : "N/A",
                        "awayTeam", arb.getAwayTeam() != null ? arb.getAwayTeam() : "N/A"
                ));
                response.put("queueStats", orchestrator.getQueueStats());
                response.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.ok(response);

            } else {
                log.warn("Failed to queue arbitrage (queue full) | Id: {} | ExternalId: {}",
                        id, arb.getExternalId());

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Queue full");
                errorResponse.put("message", "Orchestrator queue is currently full. Try again later.");
                errorResponse.put("id", id);
                errorResponse.put("externalId", arb.getExternalId());
                errorResponse.put("queueStats", orchestrator.getQueueStats());
                errorResponse.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
            }

        } catch (Exception e) {
            log.error("Error queuing arbitrage by id | Id: {} | Error: {}",
                    id, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("id", id);
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Queue an arbitrage with both id and externalId (flexible)
     * POST /api/orchestrator/queue
     * Body: { "id": 12345, "externalId": "ARB-2025-001" }
     */
//    @PostMapping("/queue")
//    public ResponseEntity<Map<String, Object>> queueArbitrage(@RequestBody Map<String, Object> request) {
//        Long id = request.get("id") != null ?
//                Long.valueOf(request.get("id").toString()) : null;
//        String externalId = request.get("externalId") != null ?
//                request.get("externalId").toString() : null;
//
//        if (id == null && externalId == null) {
//            log.warn("Queue request rejected - missing both id and externalId");
//
//            Map<String, Object> errorResponse = new HashMap<>();
//            errorResponse.put("error", "Invalid request");
//            errorResponse.put("message", "Either 'id' or 'externalId' is required");
//            errorResponse.put("timestamp", java.time.LocalDateTime.now());
//
//            return ResponseEntity.badRequest().body(errorResponse);
//        }
//
//        // Prefer externalId if provided
//        if (externalId != null) {
//            Map<String, String> externalIdRequest = new HashMap<>();
//            externalIdRequest.put("externalId", externalId);
//            return externalIdRequest;
//        } else {
//            Map<String, Long> idRequest = new HashMap<>();
//            idRequest.put("id", id);
//            return queueById(idRequest);
//        }
//    }

    /**
     * Get orchestrator status
     * GET /api/orchestrator/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        log.info("Fetching orchestrator status");

        Orchestrator.QueueStats queueStats = orchestrator.getQueueStats();
        Set<BookMaker> registeredWorkers = orchestrator.getRegisteredWorkers();

        Map<String, Object> status = new HashMap<>();
        status.put("queueStats", queueStats);
        status.put("registeredWorkers", registeredWorkers);
        status.put("workerCount", registeredWorkers.size());
        status.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(status);
    }

    /**
     * Get queue statistics
     * GET /api/orchestrator/queue-stats
     */
    @GetMapping("/queue-stats")
    public ResponseEntity<Orchestrator.QueueStats> getQueueStats() {
        log.info("Fetching queue statistics");
        Orchestrator.QueueStats stats = orchestrator.getQueueStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get registered workers
     * GET /api/orchestrator/workers
     */
    @GetMapping("/workers")
    public ResponseEntity<Map<String, Object>> getWorkers() {
        log.info("Fetching registered workers");

        Set<BookMaker> workers = orchestrator.getRegisteredWorkers();

        Map<String, Object> response = new HashMap<>();
        response.put("workers", workers);
        response.put("count", workers.size());
        response.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    /**
     * Force cleanup of orchestrator queues
     * POST /api/orchestrator/cleanup
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> forceCleanup() {
        log.info("Forcing orchestrator queue clean-up via API");

        try {
            Orchestrator.QueueStats beforeStats = orchestrator.getQueueStats();
            orchestrator.forceCleanup();
            Orchestrator.QueueStats afterStats = orchestrator.getQueueStats();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Queue cleanup completed");
            response.put("before", beforeStats);
            response.put("after", afterStats);
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during cleanup", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to cleanup queues");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Health check endpoint
     * GET /api/orchestrator/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Orchestrator.QueueStats stats = orchestrator.getQueueStats();
        Set<BookMaker> workers = orchestrator.getRegisteredWorkers();

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("queueStats", stats);
        health.put("registeredWorkers", workers.size());
        health.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(health);
    }
}