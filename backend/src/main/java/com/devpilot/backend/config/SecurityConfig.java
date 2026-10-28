package com.devpilot.backend.config;

import com.devpilot.backend.security.GithubOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

/**
 * Spring Security configuration — updated in Part 3 to add full GitHub OAuth2 login.
 *
 * <h3>Overall auth flow</h3>
 * <pre>
 * Browser                  Backend                          GitHub
 *   |---GET /api/v1/auth/login-url--->|                        |
 *   |<--{ url: "/oauth2/authorization/github" }----------------|
 *   |---GET /oauth2/authorization/github----------------------->|
 *   |                                 |<-- code + state --------|
 *   |                                 |--- exchange for token -->|
 *   |                                 |<-- access_token --------|
 *   |                                 |--- GET /user ----------->|
 *   |                                 |<-- { id, login, ... } --|
 *   |                                 |  upsertFromGitHub()      |
 *   |<-- 302 redirect to /auth/callback (with session cookie) --|
 * </pre>
 *
 * <h3>Key design decisions</h3>
 * <ul>
 *   <li><b>CSRF disabled</b>: Our SPA sends JSON, never HTML form POST. The session
 *       cookie uses {@code SameSite=Lax}, which blocks the common CSRF attack vector
 *       (cross-site form submissions) at the browser level.</li>
 *   <li><b>No JWT</b>: Session cookies are simpler, server-revocable, and avoid
 *       the "token leakage via localStorage" attack. HttpOnly prevents JS access.</li>
 *   <li><b>401 instead of login redirect</b>: API clients must receive a machine-readable
 *       response. {@link HttpStatusEntryPoint} achieves this.</li>
 *   <li><b>Success handler redirects to frontend</b>: After OAuth2 callback, Spring
 *       redirects the browser to {@code {frontendUrl}/auth/callback} where the SPA
 *       can call /me to get user data and show the dashboard.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GithubOAuth2UserService githubOAuth2UserService;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationSuccessHandler oauth2SuccessHandler,
            AuthenticationFailureHandler oauth2FailureHandler) throws Exception {

        http
            // Use the CorsConfigurationSource bean from CorsConfig
            .cors(Customizer.withDefaults())

            // CSRF disabled — see Javadoc above for rationale
            .csrf(csrf -> csrf.disable())

            // Create a session only when needed (i.e., on successful login)
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // Authorization rules (order matters — first match wins)
            .authorizeHttpRequests(auth -> auth
                    // Login initiation URL and the OAuth2 redirect URIs are always public
                    .requestMatchers(
                            "/api/v1/auth/login-url",
                            "/oauth2/**",
                            "/login/oauth2/**",
                            "/error")
                    .permitAll()
                    // Health checks are public
                    .requestMatchers("/api/v1/health", "/actuator/health").permitAll()
                    // Preflight OPTIONS requests must be allowed without auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Every other API call requires authentication
                    .requestMatchers("/api/v1/**").authenticated()
                    .anyRequest().permitAll()
            )

            // Return 401 JSON for unauthenticated API calls — not a redirect to /login
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            // GitHub OAuth2 login
            .oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo
                            // Our service that upserts the user after GitHub login
                            .userService(githubOAuth2UserService))
                    // Redirect browser to frontend /auth/callback after success
                    .successHandler(oauth2SuccessHandler)
                    // Redirect browser to frontend /login?error=oauth_failed on failure
                    .failureHandler(oauth2FailureHandler)
            )

            // Logout
            .logout(logout -> logout
                    .logoutUrl("/api/v1/auth/logout")
                    // Return 204 No Content instead of a redirect — API-style response
                    .logoutSuccessHandler((request, response, authentication) ->
                            response.setStatus(HttpStatus.NO_CONTENT.value()))
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("DEVPILOT_SESSION")
            );

        return http.build();
    }

    /**
     * After successful GitHub login, redirect the browser to the frontend's callback route.
     * The SPA can then call GET /api/v1/auth/me to fetch the logged-in user's profile.
     */
    @Bean
    AuthenticationSuccessHandler oauth2SuccessHandler(
            @Value("${devpilot.frontend-url}") String frontendUrl) {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl(frontendUrl + "/auth/callback");
        return handler;
    }

    /**
     * On OAuth2 failure (e.g. user denied access), redirect to the frontend login page
     * with an error parameter so the UI can display a user-friendly message.
     */
    @Bean
    AuthenticationFailureHandler oauth2FailureHandler(
            @Value("${devpilot.frontend-url}") String frontendUrl) {
        SimpleUrlAuthenticationFailureHandler handler = new SimpleUrlAuthenticationFailureHandler();
        handler.setDefaultFailureUrl(frontendUrl + "/login?error=oauth_failed");
        return handler;
    }
}
