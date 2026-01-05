package com.mouse.bet.client;

import com.mouse.bet.dto.BreakingBetResponse;
import com.mouse.bet.interceptor.SimpleHttpLoggingInterceptor;
import com.mouse.bet.manager.TokenExpirationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor  // Lombok will generate constructor for final fields
public class BreakingBetClient {

    private static final String EMOJI_INIT = "🚀";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_INFO = "ℹ️";
    private static final String EMOJI_TOKEN = "🔑";
    private static final String EMOJI_CLOCK = "⏰";
    private static final String EMOJI_REQUEST = "📤";
    private static final String EMOJI_RESPONSE = "📥";

    private OkHttpClient client;
    private final ObjectMapper objectMapper;

    // Inject TokenExpirationManager via constructor (handled by @RequiredArgsConstructor)
    private final TokenExpirationManager tokenManager;

    @Value("${breaking-bet.api.prematch.url:https://arbs.prematch.api.breaking-bet.com/v1/users/bb-51233/filter/items}")
    private String prematchApiUrl;

    @Value("${breaking-bet.api.live.url:https://arbs.live.api.breaking-bet.com/v1/users/bb-51233/filter/items}")
    private String liveApiUrl;

    @Value("${breaking-bet.bearer.token}")
    private String bearerToken;

    @Value("${breaking-bet.connection.timeout:30}")
    private int connectionTimeout;

    @Value("${breaking-bet.read.timeout:30}")
    private int readTimeout;

    @Value("${breaking-bet.write.timeout:30}")
    private int writeTimeout;

    @Value("${breaking-bet.user.id:bb-51233}")
    private String userId;

    /**
     * Initialize the OkHttp client with all interceptors and configuration
     */
    @PostConstruct
    public void init() {
        log.info("{} {} Initializing BreakingBetClient for user: {}",
                EMOJI_INIT, EMOJI_INFO, userId);

        // Decode and log token information on startup
        decodeAndLogTokenInfo(bearerToken);

        // Build OkHttp client with interceptors
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .addInterceptor(new SimpleHttpLoggingInterceptor())  // Your existing interceptor
                .addInterceptor(this::addHeadersInterceptor)          // Headers interceptor
                .retryOnConnectionFailure(true)
                .build();

        log.info("{} {} BreakingBetClient initialized successfully", EMOJI_SUCCESS, EMOJI_INFO);
        log.info("{} {} Prematch API: {}", EMOJI_INFO, EMOJI_REQUEST, prematchApiUrl);
        log.info("{} {} Live API: {}", EMOJI_INFO, EMOJI_REQUEST, liveApiUrl);
    }

    /**
     * Interceptor to add all required headers to every request
     * Includes all browser headers for proper API authentication
     * Uses token from TokenExpirationManager to ensure it's always current
     */
    private Response addHeadersInterceptor(Interceptor.Chain chain) throws IOException {
        Request original = chain.request();

        // Extract host from URL for :authority header
        String host = original.url().host();

        // Get token from manager (in case it was updated at runtime)
        String currentToken = tokenManager.getBearerToken();

        Request.Builder requestBuilder = original.newBuilder()
                // Authentication - use token from manager
                .header("Authorization", "Bearer " + currentToken)

                // Browser identification
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
                .header("sec-ch-ua", "\"Google Chrome\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand)\";v=\"24\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")

                // Content negotiation
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip, deflate, br, zstd")
                .header("Accept-Language", "en-US,en;q=0.9")

                // Origin and referrer
                .header("Origin", "https://breaking-bet.com")
                .header("Referer", "https://breaking-bet.com/")

                // Fetch metadata (CORS)
                .header("sec-fetch-dest", "empty")
                .header("sec-fetch-mode", "cors")
                .header("sec-fetch-site", "same-site")

                // Priority
                .header("priority", "u=1, i");

        log.debug("{} {} Adding headers for request to: {}", EMOJI_INFO, EMOJI_REQUEST, host);

        return chain.proceed(requestBuilder.build());
    }

    /**
     * Decode JWT bearer token and log comprehensive information
     * Extracts and logs: expiration date, user info, resources, bookmakers
     */
    private void decodeAndLogTokenInfo(String token) {
        try {
            log.info("{} {} ============================================", EMOJI_TOKEN, EMOJI_INFO);
            log.info("{} {} Decoding Bearer Token Information", EMOJI_TOKEN, EMOJI_INFO);
            log.info("{} {} ============================================", EMOJI_TOKEN, EMOJI_INFO);

            // JWT tokens have 3 parts: header.payload.signature
            String[] parts = token.split("\\.");

            if (parts.length != 3) {
                log.error("{} {} Invalid JWT token format - expected 3 parts, got {}",
                        EMOJI_ERROR, EMOJI_TOKEN, parts.length);
                return;
            }

            // Decode header (first part) - algorithm info
            try {
                String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
                JsonNode header = objectMapper.readTree(headerJson);
                log.info("{} {} Token Algorithm: {}",
                        EMOJI_TOKEN, EMOJI_INFO, header.get("alg").asText());
            } catch (Exception e) {
                log.debug("Could not decode token header: {}", e.getMessage());
            }

            // Decode payload (second part) - main token data
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadJson);

            log.info("{} {} Token Payload:", EMOJI_TOKEN, EMOJI_INFO);

            // Extract and log user information
            if (payload.has("user")) {
                JsonNode user = payload.get("user");
                if (user.has("id")) {
                    String userId = user.get("id").asText();
                    log.info("{} {}   User ID: {}", EMOJI_TOKEN, EMOJI_INFO, userId);
                }
            }

            // Extract and log expiration information (CRITICAL)
            if (payload.has("exp")) {
                long expTimestamp = payload.get("exp").asLong();
                Instant expiration = Instant.ofEpochSecond(expTimestamp);
                Instant now = Instant.now();

                String formattedDate = DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss z")
                        .withZone(ZoneId.systemDefault())
                        .format(expiration);

                long secondsUntilExpiry = expTimestamp - now.getEpochSecond();
                long daysUntilExpiry = secondsUntilExpiry / 86400;
                long hoursUntilExpiry = (secondsUntilExpiry % 86400) / 3600;
                long minutesUntilExpiry = (secondsUntilExpiry % 3600) / 60;

                log.info("{} {}   Expiration Date: {}", EMOJI_CLOCK, EMOJI_TOKEN, formattedDate);
                log.info("{} {}   Time Until Expiry: {} days, {} hours, {} minutes",
                        EMOJI_CLOCK, EMOJI_INFO, daysUntilExpiry, hoursUntilExpiry, minutesUntilExpiry);

                // Warning levels based on time remaining
                if (secondsUntilExpiry < 0) {
                    log.error("{} {} ⚠️  TOKEN HAS EXPIRED! ⚠️", EMOJI_ERROR, EMOJI_TOKEN);
                    log.error("{} {} Token expired on: {}", EMOJI_ERROR, EMOJI_TOKEN, formattedDate);
                    log.error("{} {} Please renew your token immediately!", EMOJI_ERROR, EMOJI_WARNING);
                } else if (secondsUntilExpiry < 3600) { // Less than 1 hour
                    log.error("{} {} ⚠️  TOKEN EXPIRES IN LESS THAN 1 HOUR! ⚠️",
                            EMOJI_ERROR, EMOJI_TOKEN);
                    log.error("{} {} Only {} minutes remaining!",
                            EMOJI_ERROR, EMOJI_WARNING, minutesUntilExpiry);
                } else if (secondsUntilExpiry < 86400) { // Less than 1 day
                    log.warn("{} {} ⚠️  TOKEN EXPIRES SOON! ⚠️", EMOJI_WARNING, EMOJI_TOKEN);
                    log.warn("{} {} Only {} hours, {} minutes remaining",
                            EMOJI_WARNING, EMOJI_CLOCK, hoursUntilExpiry, minutesUntilExpiry);
                } else if (secondsUntilExpiry < 604800) { // Less than 1 week
                    log.warn("{} {} Token expires in {} days",
                            EMOJI_WARNING, EMOJI_CLOCK, daysUntilExpiry);
                } else {
                    log.info("{} {} Token is valid for {} days",
                            EMOJI_SUCCESS, EMOJI_CLOCK, daysUntilExpiry);
                }
            } else {
                log.warn("{} {} No expiration claim found in token", EMOJI_WARNING, EMOJI_TOKEN);
            }

            // Log resources/permissions
            if (payload.has("res")) {
                JsonNode resources = payload.get("res");
                log.info("{} {}   Resources/Permissions:", EMOJI_TOKEN, EMOJI_INFO);
                for (JsonNode resource : resources) {
                    log.info("{} {}     - {}", EMOJI_TOKEN, EMOJI_INFO, resource.asText());
                }
            }

            // Log bookmakers information
            if (payload.has("bookmakers")) {
                JsonNode bookmakers = payload.get("bookmakers");
                log.info("{} {}   Bookmakers: {} bookmakers configured",
                        EMOJI_TOKEN, EMOJI_INFO, bookmakers.size());

                // Log first few bookmaker IDs
                if (bookmakers.isArray() && bookmakers.size() > 0) {
                    StringBuilder bookmakerIds = new StringBuilder();
                    int displayCount = Math.min(10, bookmakers.size());
                    for (int i = 0; i < displayCount; i++) {
                        bookmakerIds.append(bookmakers.get(i).asInt());
                        if (i < displayCount - 1) bookmakerIds.append(", ");
                    }
                    if (bookmakers.size() > 10) {
                        bookmakerIds.append("... (").append(bookmakers.size() - 10).append(" more)");
                    }
                    log.info("{} {}     IDs: {}", EMOJI_TOKEN, EMOJI_INFO, bookmakerIds);
                }
            }

            log.info("{} {} ============================================", EMOJI_TOKEN, EMOJI_INFO);

        } catch (IllegalArgumentException e) {
            log.error("{} {} Failed to decode token - Invalid Base64: {}",
                    EMOJI_ERROR, EMOJI_TOKEN, e.getMessage());
        } catch (Exception e) {
            log.error("{} {} Failed to decode token: {}",
                    EMOJI_ERROR, EMOJI_TOKEN, e.getMessage(), e);
        }
    }

    /**
     * Fetches current prematch arbitrage opportunities
     * @return JSON string response containing prematch arbs
     * @throws IOException if request fails
     */
    public String fetchPrematchArbs() throws IOException {
        // Check token validity before making request
        tokenManager.requireValidToken();

        log.info("{} {} Fetching prematch arbs...", EMOJI_REQUEST, EMOJI_INFO);

        Request request = new Request.Builder()
                .url(prematchApiUrl)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            return handleResponse(response, "prematch");
        } catch (IOException e) {
            log.error("{} {} Error fetching prematch arbs: {}",
                    EMOJI_ERROR, EMOJI_REQUEST, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Fetches and parses prematch arbitrage opportunities as objects
     * @return Parsed BreakingBetResponse object
     * @throws IOException if request fails
     */
    public BreakingBetResponse fetchPrematchArbsAsObject() throws IOException {
        String jsonResponse = fetchPrematchArbs();
        return parseResponse(jsonResponse);
    }

    /**
     * Fetches current live arbitrage opportunities
     * @return JSON string response containing live arbs
     * @throws IOException if request fails
     */
    public String fetchLiveArbs() throws IOException {
        // Check token validity before making request
        tokenManager.requireValidToken();

        log.info("{} {} Fetching live arbs...", EMOJI_REQUEST, EMOJI_INFO);

        Request request = new Request.Builder()
                .url(liveApiUrl)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            return handleResponse(response, "live");
        } catch (IOException e) {
            log.error("{} {} Error fetching live arbs: {}",
                    EMOJI_ERROR, EMOJI_REQUEST, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Fetches and parses live arbitrage opportunities as objects
     * @return Parsed BreakingBetResponse object
     * @throws IOException if request fails
     */
    public BreakingBetResponse fetchLiveArbsAsObject() throws IOException {
        String jsonResponse = fetchLiveArbs();
        return parseResponse(jsonResponse);
    }

    /**
     * Parse JSON response string into BreakingBetResponse object
     * @param jsonResponse Raw JSON string
     * @return Parsed BreakingBetResponse object
     * @throws IOException if parsing fails
     */
    private BreakingBetResponse parseResponse(String jsonResponse) throws IOException {
        try {
            BreakingBetResponse response = objectMapper.readValue(jsonResponse, BreakingBetResponse.class);

            log.info("{} {} Parsed response: {} items, {} events",
                    EMOJI_SUCCESS, EMOJI_INFO,
                    response.getItems() != null ? response.getItems().size() : 0,
                    response.getEvents() != null ? response.getEvents().size() : 0);

            return response;
        } catch (Exception e) {
            log.error("{} {} Failed to parse response into object: {}",
                    EMOJI_ERROR, EMOJI_INFO, e.getMessage());
            throw new IOException("Failed to parse Breaking-Bet response", e);
        }
    }

    /**
     * Handle API response with detailed logging
     */
    private String handleResponse(Response response, String type) throws IOException {
        if (!response.isSuccessful()) {
            String errorBody = response.body() != null ? response.body().string() : "No body";

            log.error("{} {} {} request failed:", EMOJI_ERROR, EMOJI_RESPONSE, type);
            log.error("{} {}   Status Code: {}", EMOJI_ERROR, EMOJI_INFO, response.code());
            log.error("{} {}   Message: {}", EMOJI_ERROR, EMOJI_INFO, response.message());
            log.error("{} {}   Body: {}", EMOJI_ERROR, EMOJI_INFO, errorBody);

            // Check for specific error cases
            if (response.code() == 401) {
                log.error("{} {} Authentication failed - Token may be expired or invalid!",
                        EMOJI_ERROR, EMOJI_TOKEN);
                // Re-check token expiration
                long secondsRemaining = getSecondsUntilExpiration();
                if (secondsRemaining < 0) {
                    log.error("{} {} Token has expired! Please renew.", EMOJI_ERROR, EMOJI_TOKEN);
                }
            } else if (response.code() == 403) {
                log.error("{} {} Access forbidden - Check permissions/resources in token",
                        EMOJI_ERROR, EMOJI_TOKEN);
            } else if (response.code() == 429) {
                log.error("{} {} Rate limit exceeded - Too many requests",
                        EMOJI_ERROR, EMOJI_WARNING);
            }

            throw new IOException("Unexpected code " + response.code() + ": " + errorBody);
        }

        String responseBody = response.body().string();

        log.info("{} {} {} arbs fetched successfully",
                EMOJI_SUCCESS, EMOJI_RESPONSE, type);
        log.info("{} {}   Response size: {} bytes",
                EMOJI_SUCCESS, EMOJI_INFO, responseBody.length());

        // Try to parse and log count of arbs if possible
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.isArray()) {
                log.info("{} {}   Arbs count: {}", EMOJI_SUCCESS, EMOJI_INFO, root.size());
            } else if (root.has("items") && root.get("items").isArray()) {
                log.info("{} {}   Arbs count: {}", EMOJI_SUCCESS, EMOJI_INFO,
                        root.get("items").size());
            }
        } catch (Exception e) {
            log.debug("Could not parse arbs count: {}", e.getMessage());
        }

        return responseBody;
    }

    /**
     * Check if bearer token is valid and not expiring soon
     * @return true if token is valid for more than 24 hours, false otherwise
     */
    public boolean isTokenValid() {
        long secondsRemaining = getSecondsUntilExpiration();
        boolean isValid = secondsRemaining > 86400; // More than 24 hours

        if (!isValid) {
            if (secondsRemaining < 0) {
                log.warn("{} {} Token has expired!", EMOJI_WARNING, EMOJI_TOKEN);
            } else {
                log.warn("{} {} Token expires in less than 24 hours!",
                        EMOJI_WARNING, EMOJI_TOKEN);
            }
        }

        return isValid;
    }

    /**
     * Get seconds until token expiration
     * @return seconds until expiration, or -1 if already expired/invalid token
     */
    public long getSecondsUntilExpiration() {
        try {
            String[] parts = bearerToken.split("\\.");
            if (parts.length != 3) {
                log.warn("{} {} Invalid token format", EMOJI_WARNING, EMOJI_TOKEN);
                return -1;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadJson);

            if (payload.has("exp")) {
                long expTimestamp = payload.get("exp").asLong();
                return expTimestamp - Instant.now().getEpochSecond();
            }

            log.warn("{} {} No expiration found in token", EMOJI_WARNING, EMOJI_TOKEN);
            return -1;
        } catch (Exception e) {
            log.error("{} {} Error getting expiration time: {}",
                    EMOJI_ERROR, EMOJI_TOKEN, e.getMessage());
            return -1;
        }
    }

    /**
     * Get token expiration as formatted string
     * @return formatted expiration date or "Unknown" if error
     */
    public String getTokenExpirationFormatted() {
        try {
            String[] parts = bearerToken.split("\\.");
            if (parts.length != 3) {
                return "Invalid Token";
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadJson);

            if (payload.has("exp")) {
                long expTimestamp = payload.get("exp").asLong();
                Instant expiration = Instant.ofEpochSecond(expTimestamp);

                return DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss z")
                        .withZone(ZoneId.systemDefault())
                        .format(expiration);
            }

            return "Unknown";
        } catch (Exception e) {
            log.error("{} {} Error formatting expiration: {}",
                    EMOJI_ERROR, EMOJI_TOKEN, e.getMessage());
            return "Error";
        }
    }
}