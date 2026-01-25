//package com.mouse.bet.controller;
//
//import com.mouse.bet.window.Player;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.Parameter;
//import io.swagger.v3.oas.annotations.responses.ApiResponses;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * REST Controller for managing betting windows through the Player.
// *
// * Provides endpoints for:
// * - Starting/stopping all windows
// * - Pausing/resuming windows
// * - Restarting windows
// * - Checking status
// * - Individual window control
// */
//@Slf4j
//@RestController
//@RequestMapping("/api/v1/player")
//@RequiredArgsConstructor
//@Tag(name = "Player Management", description = "APIs for controlling betting windows player")
//@CrossOrigin(origins = "*", maxAge = 3600)
//public class PlayerController {
//
//    private static final String EMOJI_API = "🌐";
//    private static final String EMOJI_SUCCESS = "✅";
//    private static final String EMOJI_ERROR = "❌";
//    private static final String EMOJI_WARNING = "⚠️";
//
//    private final Player player;
//
//    // ========================================================================
//    // WINDOW LIFECYCLE ENDPOINTS
//    // ========================================================================
//
//    /**
//     * Start all betting windows
//     */
//    @PostMapping("/start")
//    @Operation(
//            summary = "Start all betting windows",
//            description = "Starts MSport, SportyBet, and OneWin windows in parallel"
//    )
////    @ApiResponses(value = {
////            @ApiResponse(responseCode = "200", description = "Windows started successfully"),
////            @ApiResponse(responseCode = "409", description = "Windows already running"),
////            @ApiResponse(responseCode = "500", description = "Failed to start windows")
////    })
//    public ResponseEntity<ApiResponse> startWindows() {
//        log.info("{} {} POST /api/v1/player/start - Starting all windows", EMOJI_API, EMOJI_SUCCESS);
//
//        try {
//            if (player.getIsRunning().get()) {
//                log.warn("{} {} Windows are already running", EMOJI_WARNING, EMOJI_API);
//                return ResponseEntity.status(HttpStatus.CONFLICT)
//                        .body(ApiResponse.error("Windows are already running"));
//            }
//
//            player.startWindows();
//
//            log.info("{} {} All windows started successfully", EMOJI_SUCCESS, EMOJI_API);
//            return ResponseEntity.ok(
//                    ApiResponse.success("All betting windows started successfully", player.getStatus())
//            );
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to start windows: {}", EMOJI_ERROR, EMOJI_API, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to start windows: " + e.getMessage()));
//        }
//    }
//
//    /**
//     * Stop all betting windows
//     */
//    @PostMapping("/stop")
//    @Operation(
//            summary = "Stop all betting windows",
//            description = "Gracefully stops all running betting windows"
//    )
////    @ApiResponses({
////            @ApiResponse(responseCode = "200", description = "Windows stopped successfully"),
////            @ApiResponse(responseCode = "409", description = "Windows not running"),
////            @ApiResponse(responseCode = "500", description = "Error during shutdown")
////    })
//    public ResponseEntity<ApiResponse> stopWindows() {
//        log.info("{} {} POST /api/v1/player/stop - Stopping all windows", EMOJI_API, EMOJI_WARNING);
//
//        try {
//            if (!player.getIsRunning().get()) {
//                log.warn("{} {} Windows are not running", EMOJI_WARNING, EMOJI_API);
//                return ResponseEntity.status(HttpStatus.CONFLICT)
//                        .body(ApiResponse.error("Windows are not running"));
//            }
//
//            player.stopWindows();
//
//            log.info("{} {} All windows stopped successfully", EMOJI_SUCCESS, EMOJI_API);
//            return ResponseEntity.ok(
//                    ApiResponse.success("All betting windows stopped successfully", player.getStatus())
//            );
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to stop windows: {}", EMOJI_ERROR, EMOJI_API, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to stop windows: " + e.getMessage()));
//        }
//    }
//
//    /**
//     * Restart all betting windows
//     */
//    @PostMapping("/restart")
//    @Operation(
//            summary = "Restart all betting windows",
//            description = "Stops and restarts all betting windows with a 3-second delay"
//    )
////    @ApiResponses({
////            @ApiResponse(responseCode = "200", description = "Windows restarted successfully"),
////            @ApiResponse(responseCode = "500", description = "Failed to restart windows")
////    })
//    public ResponseEntity<ApiResponse> restartWindows() {
//        log.info("{} {} POST /api/v1/player/restart - Restarting all windows", EMOJI_API, EMOJI_WARNING);
//
//        try {
//            player.restartWindows();
//
//            log.info("{} {} All windows restarted successfully", EMOJI_SUCCESS, EMOJI_API);
//            return ResponseEntity.ok(
//                    ApiResponse.success("All betting windows restarted successfully", player.getStatus())
//            );
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to restart windows: {}", EMOJI_ERROR, EMOJI_API, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to restart windows: " + e.getMessage()));
//        }
//    }
//
//    // ========================================================================
//    // PAUSE/RESUME ENDPOINTS
//    // ========================================================================
//
//    /**
//     * Pause all betting windows
//     */
//    @PostMapping("/pause")
//    @Operation(
//            summary = "Pause all betting windows",
//            description = "Pauses betting operations on all windows (windows remain open)"
//    )
////    @ApiResponses({
////            @ApiResponse(responseCode = "200", description = "Windows paused successfully"),
////            @ApiResponse(responseCode = "409", description = "Windows not running"),
////            @ApiResponse(responseCode = "500", description = "Failed to pause windows")
////    })
//    public ResponseEntity<ApiResponse> pauseWindows() {
//        log.info("{} {} POST /api/v1/player/pause - Pausing all windows", EMOJI_API, EMOJI_WARNING);
//
//        try {
//            if (!player.getIsRunning().get()) {
//                log.warn("{} {} Cannot pause - windows are not running", EMOJI_WARNING, EMOJI_API);
//                return ResponseEntity.status(HttpStatus.CONFLICT)
//                        .body(ApiResponse.error("Cannot pause - windows are not running"));
//            }
//
//            player.pauseWindows();
//
//            log.info("{} {} All windows paused successfully", EMOJI_SUCCESS, EMOJI_API);
//            return ResponseEntity.ok(
//                    ApiResponse.success("All betting windows paused successfully", player.getStatus())
//            );
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to pause windows: {}", EMOJI_ERROR, EMOJI_API, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to pause windows: " + e.getMessage()));
//        }
//    }
//
//    /**
//     * Resume all betting windows
//     */
//    @PostMapping("/resume")
//    @Operation(
//            summary = "Resume all betting windows",
//            description = "Resumes betting operations on all paused windows"
//    )
////    @ApiResponses({
////            @ApiResponse(responseCode = "200", description = "Windows resumed successfully"),
////            @ApiResponse(responseCode = "409", description = "Windows not running"),
////            @ApiResponse(responseCode = "500", description = "Failed to resume windows")
////    })
//    public ResponseEntity<ApiResponse> resumeWindows() {
//        log.info("{} {} POST /api/v1/player/resume - Resuming all windows", EMOJI_API, EMOJI_SUCCESS);
//
//        try {
//            if (!player.getIsRunning().get()) {
//                log.warn("{} {} Cannot resume - windows are not running", EMOJI_WARNING, EMOJI_API);
//                return ResponseEntity.status(HttpStatus.CONFLICT)
//                        .body(ApiResponse.error("Cannot resume - windows are not running"));
//            }
//
//            player.resumeWindows();
//
//            log.info("{} {} All windows resumed successfully", EMOJI_SUCCESS, EMOJI_API);
//            return ResponseEntity.ok(
//                    ApiResponse.success("All betting windows resumed successfully", player.getStatus())
//            );
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to resume windows: {}", EMOJI_ERROR, EMOJI_API, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to resume windows: " + e.getMessage()));
//        }
//    }
//
//    // ========================================================================
//    // STATUS ENDPOINTS
//    // ========================================================================
//
//    /**
//     * Get player status
//     */
//    @GetMapping("/status")
//    @Operation(
//            summary = "Get player status",
//            description = "Returns detailed status of the player and all betting windows"
//    )
////    @ApiResponses({
////            @ApiResponse(responseCode = "200", description = "Status retrieved successfully")
////    })
//    public ResponseEntity<ApiResponse> getStatus() {
//        log.debug("{} {} GET /api/v1/player/status", EMOJI_API, EMOJI_SUCCESS);
//
//        try {
//            Player.PlayerStatus status = player.getStatus();
//
//            Map<String, Object> detailedStatus = new HashMap<>();
//            detailedStatus.put("playerStatus", status);
//            detailedStatus.put("mSportRunning", player.isMSportRunning());
//            detailedStatus.put("sportyRunning", player.isSportyRunning());
//            detailedStatus.put("oneWinRunning", player.isOneWinRunning());
//            detailedStatus.put("timestamp", LocalDateTime.now());
//
//            return ResponseEntity.ok(
//                    ApiResponse.success("Player status retrieved successfully", detailedStatus)
//            );
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to get status: {}", EMOJI_ERROR, EMOJI_API, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to get status: " + e.getMessage()));
//        }
//    }
//
//    /**
//     * Get simple health check
//     */
//    @GetMapping("/health")
//    @Operation(
//            summary = "Health check",
//            description = "Simple health check endpoint - returns OK if player is operational"
//    )
////    @ApiResponses({
////            @ApiResponse(responseCode = "200", description = "Player is healthy"),
////            @ApiResponse(responseCode = "503", description = "Player is shutting down")
////    })
//    public ResponseEntity<ApiResponse> healthCheck() {
//        log.debug("{} {} GET /api/v1/player/health", EMOJI_API, EMOJI_SUCCESS);
//
//        try {
//            if (player.getIsShuttingDown().get()) {
//                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
//                        .body(ApiResponse.error("Player is shutting down"));
//            }
//
//            Map<String, Object> health = new HashMap<>();
//            health.put("status", "UP");
//            health.put("running", player.getIsRunning().get());
//            health.put("timestamp", LocalDateTime.now());
//
//            return ResponseEntity.ok(ApiResponse.success("Player is healthy", health));
//
//        } catch (Exception e) {
//            log.error("{} {} Health check failed: {}", EMOJI_ERROR, EMOJI_API, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Health check failed: " + e.getMessage()));
//        }
//    }
//
//    // ========================================================================
//    // INDIVIDUAL WINDOW CONTROL
//    // ========================================================================
//
//    /**
//     * Pause individual window
//     */
//    @PostMapping("/pause/{window}")
//    @Operation(
//            summary = "Pause individual window",
//            description = "Pauses a specific betting window (msport, sporty, or onewin)"
//    )
////    @ApiResponses({
////            @ApiResponse(responseCode = "200", description = "Window paused successfully"),
////            @ApiResponse(responseCode = "400", description = "Invalid window name"),
////            @ApiResponse(responseCode = "500", description = "Failed to pause window")
////    })
//    public ResponseEntity<ApiResponse> pauseWindow(
//            @Parameter(description = "Window name: msport, sporty, or onewin", required = true)
//            @PathVariable String window) {
//
//        log.info("{} {} POST /api/v1/player/pause/{} - Pausing window", EMOJI_API, EMOJI_WARNING, window);
//
//        try {
//            switch (window.toLowerCase()) {
//                case "msport":
//                    player.getMSportWindow().pause();
//                    break;
//                case "sporty":
//                    player.getSportyWindow().pause();
//                    break;
//                case "onewin":
//                    player.getOneWinWindow().pause();
//                    break;
//                default:
//                    return ResponseEntity.badRequest()
//                            .body(ApiResponse.error("Invalid window name. Use: msport, sporty, or onewin"));
//            }
//
//            log.info("{} {} Window {} paused successfully", EMOJI_SUCCESS, EMOJI_API, window);
//            return ResponseEntity.ok(
//                    ApiResponse.success(window + " window paused successfully", player.getStatus())
//            );
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to pause window {}: {}", EMOJI_ERROR, EMOJI_API, window, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to pause window: " + e.getMessage()));
//        }
//    }
//
//    /**
//     * Resume individual window
//     */
//    @PostMapping("/resume/{window}")
//    @Operation(
//            summary = "Resume individual window",
//            description = "Resumes a specific betting window (msport, sporty, or onewin)"
//    )
////    @ApiResponses({
////            @ApiResponse(responseCode = "200", description = "Window resumed successfully"),
////            @ApiResponse(responseCode = "400", description = "Invalid window name"),
////            @ApiResponse(responseCode = "500", description = "Failed to resume window")
////    })
//    public ResponseEntity<ApiResponse> resumeWindow(
//            @Parameter(description = "Window name: msport, sporty, or onewin", required = true)
//            @PathVariable String window) {
//
//        log.info("{} {} POST /api/v1/player/resume/{} - Resuming window", EMOJI_API, EMOJI_SUCCESS, window);
//
//        try {
//            switch (window.toLowerCase()) {
//                case "msport":
//                    player.getMSportWindow().resume();
//                    break;
//                case "sporty":
//                    player.getSportyWindow().resume();
//                    break;
//                case "onewin":
//                    player.getOneWinWindow().resume();
//                    break;
//                default:
//                    return ResponseEntity.badRequest()
//                            .body(ApiResponse.error("Invalid window name. Use: msport, sporty, or onewin"));
//            }
//
//            log.info("{} {} Window {} resumed successfully", EMOJI_SUCCESS, EMOJI_API, window);
//            return ResponseEntity.ok(
//                    ApiResponse.success(window + " window resumed successfully", player.getStatus())
//            );
//
//        } catch (Exception e) {
//            log.error("{} {} Failed to resume window {}: {}", EMOJI_ERROR, EMOJI_API, window, e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ApiResponse.error("Failed to resume window: " + e.getMessage()));
//        }
//    }
//
//    // ========================================================================
//    // RESPONSE DTO
//    // ========================================================================
//
//    /**
//     * Standard API response wrapper
//     */
//    @lombok.Data
//    @lombok.Builder
//    @lombok.AllArgsConstructor
//    @lombok.NoArgsConstructor
//    public static class ApiResponse {
//        private boolean success;
//        private String message;
//        private Object data;
//        private LocalDateTime timestamp;
//
//        public static ApiResponse success(String message, Object data) {
//            return ApiResponse.builder()
//                    .success(true)
//                    .message(message)
//                    .data(data)
//                    .timestamp(LocalDateTime.now())
//                    .build();
//        }
//
//        public static ApiResponse error(String message) {
//            return ApiResponse.builder()
//                    .success(false)
//                    .message(message)
//                    .data(null)
//                    .timestamp(LocalDateTime.now())
//                    .build();
//        }
//    }
//}