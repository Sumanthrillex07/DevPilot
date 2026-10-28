package com.devpilot.backend.controllers;

import com.devpilot.backend.entities.User;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security integration tests for {@link AuthController}.
 *
 * <p><b>Testing strategy:</b><br>
 * We use {@code @SpringBootTest} (full context) + {@code @AutoConfigureMockMvc} so that
 * the real Spring Security filter chain is exercised — not a thin web slice. This is
 * important because we want to verify that:
 * <ul>
 *   <li>The security rules in {@code SecurityConfig} are applied correctly</li>
 *   <li>Unauthenticated requests to /me return 401, not 302</li>
 *   <li>Authenticated requests (simulated with {@code oauth2Login()}) return 200</li>
 *   <li>Logout returns 204 and invalidates the session</li>
 * </ul>
 *
 * <p><b>How we simulate authentication:</b><br>
 * {@code SecurityMockMvcRequestPostProcessors.oauth2Login()} creates a fake OAuth2
 * authentication in the test's security context. We configure it with an
 * {@link AppUserPrincipal} that wraps a stub {@link User} — exactly what the real
 * login flow would produce.
 *
 * <p><b>{@code @MockBean CurrentUser} usage:</b><br>
 * We mock {@link CurrentUser} because the real one reads from
 * {@code SecurityContextHolder}. MockMvc's security request processors set up the
 * context differently from a real servlet request, so it's cleaner to mock
 * {@code currentUser.require()} directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUser currentUser;

    // ---- Helper: build a realistic stub User ----

    private static User stubUser() {
        User u = new User();
        u.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        u.setGithubId(99999L);
        u.setGithubUsername("octocat");
        u.setDisplayName("The Octocat");
        u.setAvatarUrl("https://avatars.githubusercontent.com/u/583231?v=4");
        u.setAccessToken("encrypted-token-placeholder");
        u.setTokenScopes("read:user,repo");
        return u;
    }

    private static AppUserPrincipal stubPrincipal() {
        return new AppUserPrincipal(stubUser(), Map.of("login", "octocat"));
    }

    // ---- Test: /login-url is always public ----

    @Test
    @DisplayName("GET /api/v1/auth/login-url returns the OAuth2 initiation URL without auth")
    void loginUrl_isPublic_and_returnsGithubOauthUrl() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/oauth2/authorization/github"));
    }

    // ---- Test: /me requires authentication ----

    @Test
    @DisplayName("GET /api/v1/auth/me returns 401 when not authenticated")
    void me_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me returns 200 with user profile when authenticated")
    void me_returns200_withUserProfile_whenAuthenticated() throws Exception {
        AppUserPrincipal principal = stubPrincipal();
        when(currentUser.require()).thenReturn(principal);

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.githubUsername").value("octocat"))
                .andExpect(jsonPath("$.displayName").value("The Octocat"))
                .andExpect(jsonPath("$.avatarUrl").isNotEmpty())
                // access_token must NEVER be in the response
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me response never contains the access token")
    void me_neverExposesAccessToken() throws Exception {
        AppUserPrincipal principal = stubPrincipal();
        when(currentUser.require()).thenReturn(principal);

        String responseBody = mockMvc.perform(get("/api/v1/auth/me")
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Paranoia check: raw substring search
        if (responseBody.contains("encrypted-token") || responseBody.contains("access_token")) {
            throw new AssertionError("Access token leaked into /me response: " + responseBody);
        }
    }

    // ---- Test: /health is always public ----

    @Test
    @DisplayName("GET /api/v1/health is accessible without authentication")
    void health_isPublic_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    // ---- Test: Logout ----

    @Test
    @DisplayName("POST /api/v1/auth/logout returns 204 and clears the session")
    void logout_returns204_whenAuthenticated() throws Exception {
        AppUserPrincipal principal = stubPrincipal();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(oauth2Login().oauth2User(principal))
                        .with(csrf()))          // logout is CSRF-protected by default
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout returns 204 even when not authenticated (idempotent)")
    void logout_returns204_whenNotAuthenticated() throws Exception {
        // Spring Security's default logout behavior when no session exists:
        // it still calls the success handler which returns 204
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ---- Test: Other protected endpoints return 401, not redirect ----

    @Test
    @DisplayName("Protected API endpoints return 401 JSON (not 302 redirect) when unauthenticated")
    void protectedEndpoint_returns401Json_notRedirect() throws Exception {
        // Any endpoint under /api/v1/** that we haven't explicitly permitted
        mockMvc.perform(get("/api/v1/repositories"))
                .andExpect(status().isUnauthorized())
                // Content-Type should NOT be a login redirect page
                .andExpect(header().doesNotExist("Location"));
    }
}
