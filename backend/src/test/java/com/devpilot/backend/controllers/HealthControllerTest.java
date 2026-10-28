package com.devpilot.backend.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.devpilot.backend.config.CorsConfig;
import com.devpilot.backend.config.CryptoConfig;
import com.devpilot.backend.config.SecurityConfig;
import com.devpilot.backend.security.GithubOAuth2UserService;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link HealthController}.
 *
 * <p>@WebMvcTest loads only the web layer — the Spring MVC configuration,
 * filters, and controllers. It does NOT load the full Spring context,
 * making it fast and focused.
 *
 * <p>We import SecurityConfig and CorsConfig explicitly because they affect
 * how requests are handled (security rules, CORS headers).
 *
 * <p>Design decision: We test the security behavior here because the health
 * endpoint's "permit all" rule is part of its contract — it must be callable
 * without credentials. If a future change accidentally makes it require auth,
 * this test catches it.
 */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, CorsConfig.class, CryptoConfig.class})
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * SecurityConfig has a required constructor dependency on GithubOAuth2UserService.
     * In a @WebMvcTest slice, @Service beans are NOT loaded — only the web layer is.
     * We must @MockBean it so Spring can satisfy the SecurityConfig constructor.
     *
     * Why not use @SpringBootTest here?
     * @WebMvcTest is faster (no JPA, no DataSource, no full context) and is the right
     * tool for testing controller + security filter chain in isolation.
     */
    @MockBean
    private GithubOAuth2UserService githubOAuth2UserService;

    // ---- Happy path ----

    @Test
    void health_returnsOkWithStatusUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void health_timestampIsAnIso8601String() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                // ISO-8601 timestamps contain 'T' and 'Z'
                .andExpect(jsonPath("$.timestamp", containsString("T")));
    }

    // ---- Security: health must be public ----

    @Test
    void health_isAccessibleWithoutAuthentication() throws Exception {
        // No authentication header, no session cookie.
        // Verifies the SecurityConfig permits /api/v1/health.
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    // ---- Security: protected endpoints return 401 ----

    @Test
    void unknownProtectedEndpoint_returnsUnauthorized() throws Exception {
        // Any /api/v1/** path that is not /health should return 401
        // when no authentication is provided.
        // This verifies the security rule "all other /api/v1 paths require auth".
        mockMvc.perform(get("/api/v1/repositories"))
                .andExpect(status().isUnauthorized());
    }

    // ---- CORS: OPTIONS preflight must be permitted ----

    @Test
    void optionsPreflight_isAllowed() throws Exception {
        // Browsers send an OPTIONS request before cross-origin POST/PUT/DELETE.
        // If OPTIONS returns 403 or 401, the actual request never happens.
        mockMvc.perform(options("/api/v1/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    // ---- Content type ----

    @Test
    void health_returnsJsonContentType() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        org.springframework.http.MediaType.APPLICATION_JSON));
    }
}
