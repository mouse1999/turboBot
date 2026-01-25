package com.mouse.bet.client;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.mouse.bet.dto.BreakingBetResponse;
import com.mouse.bet.interceptor.SimpleHttpLoggingInterceptor;
import com.mouse.bet.manager.ProfileManager;
import com.mouse.bet.manager.TokenExpirationManager;
import com.mouse.bet.profile.UserAgentProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class BreakingBetClient {

    private static final String EMOJI_INIT = "🚀";
    private static final String EMOJI_SUCCESS = "✅";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_INFO = "ℹ️";
    private static final String EMOJI_TOKEN = "🔑";
    private static final String EMOJI_CLOCK = "⏰";
    private static final String EMOJI_BROWSER = "🌐";
    private static final String EMOJI_DEBUG = "🔍";

    // Playwright components
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    // OkHttp client rebuilt with fresh tokens
    private volatile OkHttpClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TokenExpirationManager tokenManager;
    private final AtomicReference<String> currentBearerToken = new AtomicReference<>();

    private ScheduledExecutorService tokenRefreshScheduler;

    @Value("${breaking-bet.api.prematch.url:https://arbs.prematch.app-api.breaking-bet.com/v1/users/bb-51233/filter/items}")
    private String prematchApiUrl;

    @Value("${breaking-bet.api.live.url:https://arbs.live.app-api.breaking-bet.com/v1/users/bb-51233/filter/items}")
    private String liveApiUrl;

    @Value("${breaking-bet.connection.timeout:30}")
    private int connectionTimeout;

    @Value("${breaking-bet.read.timeout:30}")
    private int readTimeout;

    @Value("${breaking-bet.write.timeout:30}")
    private int writeTimeout;

    @Value("${breaking-bet.context.path:./playwright-BB-context}")
    private String contextPath;

    @Value("${breaking-bet.token.refresh.interval:120}")
    private int tokenRefreshIntervalSeconds;

    @Value("${breaking-bet.headless:false}")
    private boolean headless;

    private final ProfileManager profileManager;
    private final String DEVICE_ID = "win-chrome-amd";
    private UserAgentProfile profile;

    private static final String LOGIN_URL = "https://breaking-bet.com/en/users/sign_in";
    private static final String LIVE_ARBS_URL = "https://breaking-bet.com/en/arbs/live";
    // FIXED: Changed from api.breaking-bet.com to app-api.breaking-bet.com
    private static final String TARGET_XHR_URL = "https://arbs.live.app-api.breaking-bet.com/v1/users/bb-51233/filter/items";

    @Value("${breaking-bet.login.wait.seconds:60}")
    private int loginWaitSeconds;

    @Value("${breaking-bet.token.refresh.max-retries:3}")
    private int maxRefreshRetries;

    @Value("${breaking-bet.token.refresh.retry-delay:2000}")
    private int retryDelayMillis;

    @Value("${breaking-bet.token.capture.max-wait:30}")
    private int tokenCaptureMaxWaitSeconds;

//    @PostConstruct
    public void init() {
        log.info("{} {} Initializing BreakingBetClient – manual login expected", EMOJI_INIT, EMOJI_INFO);
        try {
            initializePlaywright();
            startTokenRefreshScheduler();
            log.info("{} {} Client initialized – please log in manually in the opened browser", EMOJI_SUCCESS, EMOJI_INFO);
        } catch (Exception e) {
            log.error("{} {} Initialization failed: {}", EMOJI_ERROR, EMOJI_INIT, e.getMessage(), e);
            throw new RuntimeException("Failed to initialize BreakingBetClient", e);
        }
    }

    private void initializePlaywright() {
        log.info("{} {} Launching browser (headless: {})...", EMOJI_BROWSER, EMOJI_INFO, headless);
        playwright = Playwright.create();

        profile = profileManager.getProfile(DEVICE_ID);

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setViewportSize(null)
                .setUserAgent(profile.getUserAgent())
                .setIgnoreHTTPSErrors(true);

        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setChannel("chrome")
                .setArgs(java.util.Arrays.asList(
                        "--start-maximized",
                        "--window-size=2560,1440",
                        "--force-device-scale-factor=1",
                        "--disable-blink-features=AutomationControlled"
                )));

        Path contextFilePath = Paths.get(contextPath);
        if (Files.exists(contextFilePath)) {
            try {
                contextOptions.setStorageStatePath(contextFilePath);
                context = browser.newContext(contextOptions);
                log.info("{} {} Loaded existing context from: {}", EMOJI_SUCCESS, EMOJI_BROWSER, contextPath);
            } catch (Exception e) {
                log.warn("{} {} Failed to load context from {}: {}", EMOJI_WARNING, EMOJI_BROWSER, contextPath, e.getMessage());
                log.info("{} {} Creating new context instead", EMOJI_INFO, EMOJI_BROWSER);
                contextOptions.setViewportSize(null);
                context = browser.newContext(contextOptions);
            }
        } else {
            log.info("{} {} No existing context found at {} – creating new context", EMOJI_INFO, EMOJI_BROWSER, contextPath);
            contextOptions.setViewportSize(null);
            context = browser.newContext(contextOptions);
        }

        page = context.newPage();

        setupTokenCapture();

        // First navigate to login page and wait for user to log in
        log.info("{} {} Navigating to login page: {}", EMOJI_BROWSER, EMOJI_INFO, LOGIN_URL);
        try {
            page.navigate(LOGIN_URL, new Page.NavigateOptions().setTimeout(60000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        } catch (Exception e) {
            log.warn("{} {} Navigation/load warning (this is normal): {}", EMOJI_WARNING, EMOJI_BROWSER, e.getMessage());
        }

        log.info("{} {} Please log in manually in the browser window...", EMOJI_BROWSER, EMOJI_INFO);
        log.info("{} {} Waiting {} seconds for manual login (page may navigate after login)...", EMOJI_BROWSER, EMOJI_CLOCK, loginWaitSeconds);
        page.waitForTimeout(loginWaitSeconds * 1000);

        saveContext();

        // Now navigate to live arbs page
        log.info("{} {} Navigating to live arbs page: {}", EMOJI_BROWSER, EMOJI_INFO, LIVE_ARBS_URL);
        try {
            page.navigate(LIVE_ARBS_URL, new Page.NavigateOptions().setTimeout(60000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            log.info("{} {} Page loaded, current URL: {}", EMOJI_DEBUG, EMOJI_BROWSER, page.url());
        } catch (Exception e) {
            log.warn("{} {} Navigation/load warning: {}", EMOJI_WARNING, EMOJI_BROWSER, e.getMessage());
            log.info("{} {} Current URL after error: {}", EMOJI_DEBUG, EMOJI_BROWSER, page.url());
        }

        // Wait for token to be captured (with timeout)
        log.info("{} {} Waiting for bearer token to be captured...", EMOJI_BROWSER, EMOJI_TOKEN);
        log.info("{} {} Watching for requests to: {}", EMOJI_DEBUG, EMOJI_TOKEN, TARGET_XHR_URL);

        int waited = 0;
        while (currentBearerToken.get() == null && waited < tokenCaptureMaxWaitSeconds) {
            page.waitForTimeout(1000);
            waited++;
            if (waited % 5 == 0) {
                log.info("{} {} Still waiting for token... ({}/{} seconds)",
                        EMOJI_CLOCK, EMOJI_TOKEN, waited, tokenCaptureMaxWaitSeconds);
                log.info("{} {} Current page URL: {}", EMOJI_DEBUG, EMOJI_BROWSER, page.url());
                log.info("{} {} Current page title: {}", EMOJI_DEBUG, EMOJI_BROWSER, page.title());
            }
        }

        if (currentBearerToken.get() == null) {
            log.error("{} {} No bearer token captured after {} seconds!",
                    EMOJI_ERROR, EMOJI_TOKEN, tokenCaptureMaxWaitSeconds);
            log.error("{} {} Final page URL: {}", EMOJI_ERROR, EMOJI_BROWSER, page.url());
            log.error("{} {} Final page title: {}", EMOJI_ERROR, EMOJI_BROWSER, page.title());
            log.error("{} {} Possible causes:", EMOJI_ERROR, EMOJI_INFO);
            log.error("   1. Not logged in - check if you can see the arbs page");
            log.error("   2. Page didn't make XHR request to: {}", TARGET_XHR_URL);
            log.error("   3. Authorization header format changed");
            log.error("   4. Need to interact with page to trigger request (click filter, refresh, etc.)");
            throw new RuntimeException("Failed to capture bearer token - please ensure you're logged in and the page loaded correctly");
        }

        log.info("{} {} Bearer token successfully captured!", EMOJI_SUCCESS, EMOJI_TOKEN);
        log.info("{} {} Browser ready - token capture active", EMOJI_SUCCESS, EMOJI_BROWSER);
    }

    private void saveContext() {
        try {
            context.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get(contextPath)));
            log.info("{} {} Browser context saved to: {}", EMOJI_SUCCESS, EMOJI_BROWSER, contextPath);
        } catch (Exception e) {
            log.warn("{} {} Failed to save browser context: {}", EMOJI_WARNING, EMOJI_BROWSER, e.getMessage());
        }
    }

    private void setupTokenCapture() {
        page.onRequest(request -> {
            String url = request.url();

            // Check if it's the exact target URL we're looking for
            if (TARGET_XHR_URL.equals(url)) {
                log.info("{} {} TARGET URL MATCHED! {}", EMOJI_SUCCESS, EMOJI_TOKEN, url);
                String authHeader = request.headers().get("authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    log.info("{} {} Token extracted from Authorization header", EMOJI_SUCCESS, EMOJI_TOKEN);
                    updateBearerToken(token);
                } else {
                    log.warn("{} {} Target URL matched but no Bearer token in Authorization header!", EMOJI_WARNING, EMOJI_TOKEN);
                    log.warn("{} {} Authorization header value: {}", EMOJI_WARNING, EMOJI_TOKEN, authHeader);
                }
            }
        });

        log.info("{} {} Request interceptor set up - watching for token", EMOJI_SUCCESS, EMOJI_TOKEN);
        log.info("{} {} Listening for Authorization header on: {}", EMOJI_SUCCESS, EMOJI_TOKEN, TARGET_XHR_URL);
    }

    private void startTokenRefreshScheduler() {
        tokenRefreshScheduler = Executors.newScheduledThreadPool(1);
        tokenRefreshScheduler.scheduleAtFixedRate(this::refreshToken, 10, tokenRefreshIntervalSeconds, TimeUnit.SECONDS);
        log.info("{} {} Token refresh scheduler started (every {}s)", EMOJI_SUCCESS, EMOJI_CLOCK, tokenRefreshIntervalSeconds);
    }

    private void refreshToken() {
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRefreshRetries && !success) {
            attempt++;
            try {
                log.info("{} {} Refreshing token – navigating to live arbs page (attempt {}/{})",
                        EMOJI_TOKEN, EMOJI_CLOCK, attempt, maxRefreshRetries);

                page.navigate(LIVE_ARBS_URL, new Page.NavigateOptions().setTimeout(30000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);

                page.waitForTimeout(4000);

                success = true;
                log.info("{} {} Token refresh successful", EMOJI_SUCCESS, EMOJI_TOKEN);

            } catch (Exception e) {
                log.warn("{} {} Token refresh attempt {}/{} failed: {}",
                        EMOJI_WARNING, EMOJI_TOKEN, attempt, maxRefreshRetries, e.getMessage());

                if (attempt < maxRefreshRetries) {
                    try {
                        log.info("{} {} Retrying in {}ms...", EMOJI_CLOCK, EMOJI_TOKEN, retryDelayMillis);
                        Thread.sleep(retryDelayMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("{} {} Retry interrupted", EMOJI_ERROR, EMOJI_TOKEN);
                        return;
                    }
                } else {
                    log.error("{} {} All {} token refresh attempts failed. Will retry on next scheduled run.",
                            EMOJI_ERROR, EMOJI_TOKEN, maxRefreshRetries);
                }
            }
        }
    }

    private void updateBearerToken(String token) {
        String oldToken = currentBearerToken.getAndSet(token);
        if (oldToken == null || !oldToken.equals(token)) {
            log.info("{} {} Captured new Bearer token from live API request", EMOJI_SUCCESS, EMOJI_TOKEN);
            tokenManager.updateToken(token);
            rebuildOkHttpClient(token);
            logTokenInfo(token);
        }
    }

    private void rebuildOkHttpClient(String token) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .addInterceptor(new SimpleHttpLoggingInterceptor())
                .addInterceptor(chain -> {
                    Request req = chain.request();
                    Request newReq = req.newBuilder()
                            .header("Authorization", "Bearer " + token)
                            .header("User-Agent", profile.getUserAgent())
                            .header("Accept", "application/json")
                            .header("Origin", "https://breaking-bet.com")
                            .header("Referer", "https://breaking-bet.com/")
                            .build();
                    return chain.proceed(newReq);
                })
                .build();
        log.info("{} {} OkHttpClient rebuilt with new token", EMOJI_SUCCESS, EMOJI_INFO);
    }

    private void logTokenInfo(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return;

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode node = objectMapper.readTree(payload);

            if (node.has("exp")) {
                long exp = node.get("exp").asLong();
                long minutesLeft = (exp - Instant.now().getEpochSecond()) / 60;
                log.info("{} {} Token valid for ~{} minutes", EMOJI_CLOCK, EMOJI_TOKEN, minutesLeft);
            }
        } catch (Exception e) {
            log.debug("{} {} Could not parse token expiration", EMOJI_WARNING, EMOJI_TOKEN);
        }
    }

    // -------------------------------------------------------------------------
    // Public API methods
    // -------------------------------------------------------------------------

    public String fetchLiveArbs() throws IOException {
        ensureTokenValid();
        Request request = new Request.Builder().url(liveApiUrl).get().build();
        try (Response response = client.newCall(request).execute()) {
            return handleResponse(response, "live");
        }
    }

    public BreakingBetResponse fetchLiveArbsAsObject() throws IOException {
        return parseResponse(fetchLiveArbs());
    }

    public String fetchPrematchArbs() throws IOException {
        ensureTokenValid();
        Request request = new Request.Builder().url(prematchApiUrl).get().build();
        try (Response response = client.newCall(request).execute()) {
            return handleResponse(response, "prematch");
        }
    }

    public BreakingBetResponse fetchPrematchArbsAsObject() throws IOException {
        return parseResponse(fetchPrematchArbs());
    }

    private void ensureTokenValid() throws IOException {
        if (currentBearerToken.get() == null) {
            throw new IOException("No bearer token captured yet. Please log in manually in the browser.");
        }
    }

    private String handleResponse(Response response, String type) throws IOException {
        if (!response.isSuccessful()) {
            String body = response.body() != null ? response.body().string() : "";
            if (response.code() == 401) {
                log.warn("{} {} 401 received – token may be invalid", EMOJI_WARNING, type.toUpperCase());
            }
            throw new IOException("API error " + response.code() + ": " + body);
        }
        return response.body().string();
    }

    private BreakingBetResponse parseResponse(String json) throws IOException {
        return objectMapper.readValue(json, BreakingBetResponse.class);
    }

    @PreDestroy
    public void cleanup() {
        log.info("{} {} Shutting down...", EMOJI_INFO, EMOJI_BROWSER);
        if (tokenRefreshScheduler != null) {
            tokenRefreshScheduler.shutdownNow();
        }
        if (context != null) {
            saveContext();
            context.close();
        }
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        log.info("{} {} Shutdown complete", EMOJI_SUCCESS, EMOJI_BROWSER);
    }
}