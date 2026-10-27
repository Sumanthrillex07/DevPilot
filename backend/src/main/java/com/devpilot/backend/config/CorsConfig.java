package com.devpilot.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration.
 *
 * <p>We restrict allowed origins to the configured frontend URL only.
 * This is a security control: it prevents other websites from making
 * credentialed cross-origin requests to our API on behalf of a logged-in user.
 *
 * <p>The {@code allowCredentials(true)} setting is required so the browser
 * sends the session cookie with cross-origin requests (frontend on :5173,
 * backend on :8080 are different origins).
 *
 * <p>Security note: Never use {@code setAllowedOriginPatterns("*")} with
 * {@code allowCredentials(true)} — that bypasses the CORS protection entirely.
 */
@Configuration
public class CorsConfig {

    /**
     * Reads allowed origins from the DEVPILOT_CORS_ALLOWED_ORIGINS environment variable.
     * Multiple origins can be comma-separated, e.g.:
     *   DEVPILOT_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://devpilot.example.com
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${devpilot.cors.allowed-origins}") String allowedOriginsRaw) {

        List<String> origins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
