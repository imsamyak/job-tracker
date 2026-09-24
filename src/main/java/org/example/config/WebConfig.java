package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * This app is a personal, local-only tool (the server itself isn't exposed to the internet), so the API
 * accepts any origin rather than trying to keep an exact allowlist in step with the frontend's dev port
 * and the Chrome extension's per-install id.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "DELETE");
    }
}
