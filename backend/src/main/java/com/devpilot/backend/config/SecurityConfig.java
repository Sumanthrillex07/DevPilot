package com.devpilot.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Security configuration for Part 1.
 *
 * <p>This is a minimal configuration that:
 * <ul>
 *   <li>Applies CORS rules from {@link CorsConfig}</li>
 *   <li>Disables CSRF (safe for SPA + CORS + session cookie architecture — see docs/security.md)</li>
 *   <li>Permits the health endpoint without authentication</li>
 *   <li>Returns 401 (not redirect to login) for unauthenticated API requests</li>
 *   <li>Requires authentication for all other /api/v1/** paths</li>
 * </ul>
 *
 * <p>Full OAuth2 GitHub login is added in Part 3. For now, all /api/v1/** except /health
 * will return 401 if called without a session — which is the correct behavior
 * even before the login flow is implemented.
 *
 * <p>Security notes:
 * <ul>
 *   <li>CSRF: Disabled because our SPA does not use form-based submission and the
 *       session cookie has SameSite=Lax which mitigates CSRF for cross-site navigation.</li>
 *   <li>Session creation: IF_REQUIRED so Spring creates a session on login but does
 *       not create one for anonymous API calls.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Apply CORS rules defined in CorsConfig
                .cors(cors -> cors.configure(http))

                // Disable CSRF — SPA with CORS and session cookies does not need it
                .csrf(AbstractHttpConfigurer::disable)

                // Session management — create session when needed (login), not eagerly
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Health check is always public — no credentials needed
                        .requestMatchers("/api/v1/health").permitAll()
                        // Actuator health is public for infrastructure checks
                        .requestMatchers("/actuator/health").permitAll()
                        // OPTIONS preflight requests must be allowed without auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // Everything else under /api/v1 requires authentication
                        // (OAuth2 login is added in Part 3)
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )

                // Return 401 JSON response for unauthenticated API calls.
                // This is important: the default behavior redirects to a login page,
                // which breaks API clients that expect JSON error responses.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }
}
