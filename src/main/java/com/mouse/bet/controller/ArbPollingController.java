package com.mouse.bet.controller;

import com.mouse.bet.enums.BookMaker;
import com.mouse.bet.orchestrator.Orchestrator;
import com.mouse.bet.service.ArbPollingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST Controller for managing and monitoring the ArbPollingService
 */
@Slf4j
@RestController
@RequestMapping("/api/arb-polling")
@RequiredArgsConstructor
public class ArbPollingController {

    private final ArbPollingService arbPollingService;
    private final Orchestrator orchestrator;

    /**
     * Get current polling service status
     * GET /api/arb-polling/status
     */
    @GetMapping("/status")
    public ResponseEntity<ArbPollingService.PollingStatus> getStatus() {
        log.info("Fetching polling service status");
        ArbPollingService.PollingStatus status = arbPollingService.getStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Get detailed system status including orchestrator
     * GET /api/arb-polling/system-status
     */
    @GetMapping("/system-status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        log.info("Fetching system status");

        ArbPollingService.PollingStatus pollingStatus = arbPollingService.getStatus();
        Orchestrator.QueueStats queueStats = orchestrator.getQueueStats();
        Set<BookMaker> registeredWorkers = orchestrator.getRegisteredWorkers();

        Map<String, Object> systemStatus = new HashMap<>();
        systemStatus.put("pollingService", pollingStatus);
        systemStatus.put("orchestrator", Map.of(
                "queueStats", queueStats,
                "registeredWorkers", registeredWorkers,
                "workerCount", registeredWorkers.size()
        ));
        systemStatus.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(systemStatus);
    }

    /**
     * Start the polling service
     * POST /api/arb-polling/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startPolling() {
        log.info("Starting polling service via API");

        try {
            arbPollingService.start();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Polling service started successfully");
            response.put("status", arbPollingService.getStatus());
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error starting polling service", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to start polling service");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Stop the polling service
     * POST /api/arb-polling/stop
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopPolling() {
        log.info("Stopping polling service via API");

        try {
            arbPollingService.stop();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Polling service stopped successfully");
            response.put("status", arbPollingService.getStatus());
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error stopping polling service", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to stop polling service");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Restart the polling service
     * POST /api/arb-polling/restart
     */
    @PostMapping("/restart")
    public ResponseEntity<Map<String, Object>> restartPolling() {
        log.info("Restarting polling service via API");

        try {
            arbPollingService.restart();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Polling service restarted successfully");
            response.put("status", arbPollingService.getStatus());
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error restarting polling service", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to restart polling service");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Manually trigger a poll cycle
     * POST /api/arb-polling/trigger
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerPoll() {
        log.info("Manually triggering poll cycle via API");

        try {
            arbPollingService.triggerPoll();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Poll cycle triggered successfully");
            response.put("status", arbPollingService.getStatus());
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error triggering poll", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to trigger poll");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Update allowed bookmakers
     * PUT /api/arb-polling/bookmakers
     * Body: ["BET365", "BETWAY", "SPORTYBET"]
     */
    @PutMapping("/bookmakers")
    public ResponseEntity<Map<String, Object>> updateBookmakers(@RequestBody Set<String> bookmakerNames) {
        log.info("Updating allowed bookmakers via API | Bookmakers: {}", bookmakerNames);

        try {
            // Parse bookmaker names to enum
            Set<BookMaker> bookmakers = bookmakerNames.stream()
                    .map(name -> {
                        try {
                            return BookMaker.valueOf(name.toUpperCase().trim());
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid bookmaker name: {}", name);
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());

            if (bookmakers.isEmpty() && !bookmakerNames.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "No valid bookmakers provided");
                errorResponse.put("invalidNames", bookmakerNames);
                errorResponse.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.badRequest().body(errorResponse);
            }

            arbPollingService.updateAllowedBookmakers(bookmakers);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Allowed bookmakers updated successfully");
            response.put("bookmakers", bookmakers);
            response.put("status", arbPollingService.getStatus());
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error updating bookmakers", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to update bookmakers");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Update minimum profit percentage
     * PUT /api/arb-polling/min-profit
     * Body: { "minProfit": 2.5 }
     */
    @PutMapping("/min-profit")
    public ResponseEntity<Map<String, Object>> updateMinProfit(@RequestBody Map<String, Double> request) {
        log.info("Updating minimum profit percentage via API | Request: {}", request);

        try {
            Double minProfit = request.get("minProfit");

            if (minProfit == null || minProfit < 0) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid minimum profit value");
                errorResponse.put("message", "minProfit must be a positive number");
                errorResponse.put("timestamp", java.time.LocalDateTime.now());

                return ResponseEntity.badRequest().body(errorResponse);
            }

            arbPollingService.updateMinProfitPercentage(minProfit);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Minimum profit percentage updated successfully");
            response.put("minProfit", minProfit);
            response.put("status", arbPollingService.getStatus());
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error updating minimum profit", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to update minimum profit");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Force cleanup of orchestrator queues
     * POST /api/arb-polling/cleanup
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> forceCleanup() {
        log.info("Forcing orchestrator queue cleanup via API");

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
     * GET /api/arb-polling/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        ArbPollingService.PollingStatus status = arbPollingService.getStatus();
        Orchestrator.QueueStats queueStats = orchestrator.getQueueStats();

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("pollingRunning", status.running());
        health.put("pollingEnabled", status.enabled());
        health.put("queueStats", queueStats);
        health.put("registeredWorkers", orchestrator.getRegisteredWorkers().size());
        health.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(health);
    }
}