package com.mouse.bet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward all non-API, non-static file paths to React's index.html
        // The regex [^\.]* ensures files with extensions (like .js, .css, .ico) are NOT forwarded

        // Root level routes: /arbitrage, /bets, /dashboard, etc.
        registry.addViewController("/{spring:[^\\.]*}")
                .setViewName("forward:/index.html");

        // Nested routes: /arbitrage/123, /bets/history, etc.
        registry.addViewController("/**/{spring:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}