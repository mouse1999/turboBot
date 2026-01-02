package com.mouse.bet.manager;



import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Manages JWT token lifecycle and expiration
 * Checks token validity and provides warnings
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Data
public class TokenExpirationManager {

    private static final String EMOJI_TOKEN = "🔑";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_INFO = "ℹ️";
    private static final String EMOJI_CLOCK = "⏰";

    private final ObjectMapper objectMapper;

    @Value("${breaking-bet.bearer.token}")
    private String bearerToken;

    @Value("${breaking-bet.token.file.path:./token.txt}")
    private String tokenFilePath;

    private long expirationTimestamp;
    private boolean tokenValid;

    @PostConstruct
    public void init() {
        log.info("{} {} Initializing Token Expiration Manager...", EMOJI_TOKEN, EMOJI_INFO);
        validateToken();

        if (!tokenValid) {
            log.error("");
            log.error("╔════════════════════════════════════════════════════════════╗");
            log.error("║                    TOKEN EXPIRED ERROR                     ║");
            log.error("╚════════════════════════════════════════════════════════════╝");
            log.error("");
            log.error("{} {} YOUR TOKEN HAS EXPIRED!", EMOJI_ERROR, EMOJI_TOKEN);
            log.error("");
            log.error("To fix this issue:");
            log.error("");
            log.error("1. Open https://breaking-bet.com in your browser");
            log.error("2. Login to your account");
            log.error("3. Open Developer Tools (F12)");
            log.error("4. Go to Network tab");
            log.error("5. Refresh the page");
            log.error("6. Look for requests to 'arbs.live.api.breaking-bet.com'");
            log.error("7. Click on the request");
            log.error("8. Find 'Authorization' header");
            log.error("9. Copy the Bearer token (everything after 'Bearer ')");
            log.error("10. Update 'breaking-bet.bearer.token' in application.properties");
            log.error("");
            log.error("OR save the token to a file at: {}", tokenFilePath);
            log.error("");
            log.error("╚════════════════════════════════════════════════════════════╝");
            log.error("");

            // Try to load from file
            String fileToken = loadTokenFromFile();
            if (fileToken != null && !fileToken.isEmpty()) {
                log.info("{} {} Found token in file, checking validity...",
                        EMOJI_INFO, EMOJI_TOKEN);
                bearerToken = fileToken;
                validateToken();

                if (tokenValid) {
                    log.info("{} {} Token from file is valid!", EMOJI_SUCCESS, EMOJI_TOKEN);
                    log.warn("⚠️  IMPORTANT: Update your application.properties with this token!");
                }
            }
        }
    }

    /**
     * Validate the current bearer token
     */
    private void validateToken() {
        try {
            String[] parts = bearerToken.split("\\.");

            if (parts.length != 3) {
                log.error("{} {} Invalid JWT format - expected 3 parts, got {}",
                        EMOJI_ERROR, EMOJI_TOKEN, parts.length);
                tokenValid = false;
                return;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payload = objectMapper.readTree(payloadJson);

            if (!payload.has("exp")) {
                log.error("{} {} No expiration claim in token!", EMOJI_ERROR, EMOJI_TOKEN);
                tokenValid = false;
                return;
            }

            expirationTimestamp = payload.get("exp").asLong();
            long currentTimestamp = Instant.now().getEpochSecond();
            long secondsUntilExpiry = expirationTimestamp - currentTimestamp;

            Instant expiration = Instant.ofEpochSecond(expirationTimestamp);
            String formattedDate = DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss z")
                    .withZone(ZoneId.systemDefault())
                    .format(expiration);

            if (secondsUntilExpiry < 0) {
                long secondsExpired = Math.abs(secondsUntilExpiry);
                long hoursExpired = secondsExpired / 3600;
                long daysExpired = secondsExpired / 86400;

                log.error("{} {} TOKEN EXPIRED {} days, {} hours ago!",
                        EMOJI_ERROR, EMOJI_TOKEN, daysExpired, hoursExpired % 24);
                log.error("{} {} Expiration date: {}", EMOJI_ERROR, EMOJI_CLOCK, formattedDate);
                tokenValid = false;
                return;
            }

            long daysRemaining = secondsUntilExpiry / 86400;
            long hoursRemaining = (secondsUntilExpiry % 86400) / 3600;

            if (secondsUntilExpiry < 3600) { // Less than 1 hour
                log.error("{} {} TOKEN EXPIRES IN {} MINUTES!",
                        EMOJI_ERROR, EMOJI_TOKEN, secondsUntilExpiry / 60);
                tokenValid = true;
            } else if (secondsUntilExpiry < 86400) { // Less than 1 day
                log.warn("{} {} Token expires in {} hours!",
                        EMOJI_WARNING, EMOJI_TOKEN, hoursRemaining);
                tokenValid = true;
            } else {
                log.info("{} {} Token is valid for {} days, {} hours",
                        EMOJI_SUCCESS, EMOJI_TOKEN, daysRemaining, hoursRemaining);
                tokenValid = true;
            }

            log.info("{} {} Token expiration: {}", EMOJI_CLOCK, EMOJI_INFO, formattedDate);

        } catch (Exception e) {
            log.error("{} {} Failed to validate token: {}",
                    EMOJI_ERROR, EMOJI_TOKEN, e.getMessage(), e);
            tokenValid = false;
        }
    }

    /**
     * Try to load token from external file
     */
    private String loadTokenFromFile() {
        try {
            Path path = Paths.get(tokenFilePath);
            if (Files.exists(path)) {
                String token = Files.readString(path).trim();
                log.info("{} {} Loaded token from file: {}",
                        EMOJI_SUCCESS, EMOJI_TOKEN, tokenFilePath);
                return token;
            }
        } catch (IOException e) {
            log.debug("Could not load token from file: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Save token to external file for backup
     */
    public void saveTokenToFile(String token) {
        try {
            Path path = Paths.get(tokenFilePath);
            Files.writeString(path, token);
            log.info("{} {} Token saved to file: {}",
                    EMOJI_SUCCESS, EMOJI_TOKEN, tokenFilePath);
        } catch (IOException e) {
            log.error("{} {} Failed to save token to file: {}",
                    EMOJI_ERROR, EMOJI_TOKEN, e.getMessage());
        }
    }



    /**
     * Get seconds until token expiration
     */
    public long getSecondsUntilExpiration() {
        return expirationTimestamp - Instant.now().getEpochSecond();
    }



    /**
     * Update the bearer token at runtime
     */
    public void updateToken(String newToken) {
        this.bearerToken = newToken;
        validateToken();

        if (tokenValid) {
            log.info("{} {} Token updated successfully!", EMOJI_SUCCESS, EMOJI_TOKEN);
            saveTokenToFile(newToken);
        } else {
            log.error("{} {} New token is also invalid or expired!",
                    EMOJI_ERROR, EMOJI_TOKEN);
        }
    }

    /**
     * Throw exception if token is invalid - use before making API calls
     */
    public void requireValidToken() {
        if (!tokenValid) {
            throw new IllegalStateException(
                    "Bearer token is expired or invalid. Please update your token in application.properties. " +
                            "See logs above for instructions on how to obtain a new token."
            );
        }
    }
}