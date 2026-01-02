package com.mouse.bet.config;

import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Data
@Configuration
public class WindowConfig {
    private final List<String> BROWSER_FlAGS= Arrays.asList(
            // === Core Stealth Flags ===
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-blink-features=AutomationControlled",
            "--disable-infobars",
            "--disable-extensions",
            "--disable-default-apps",
            "--disable-popup-blocking",
            "--disable-notifications",
            "--mute-audio",
            "--no-first-run",
            "--lang=en-US",
            "--disable-background-timer-throttling",
            "--disable-backgrounding-occluded-windows",
            "--disable-renderer-backgrounding");
}
