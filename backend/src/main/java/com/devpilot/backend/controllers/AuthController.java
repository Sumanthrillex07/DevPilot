package com.devpilot.backend.controllers;

import com.devpilot.backend.dto.UserResponse;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Authentication endpoints.
 *
 * <p>These endpoints are the "glue" between our SPA and Spring Security's
 * OAuth2 machinery. The SPA never implements the OAuth2 authorization code
 * flow itself — it simply asks this controller for the URL to redirect to,
 * and then calls /me after the redirect comes back.
 *
 * <h3>Endpoints</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Auth required</th><th>Purpose</th></tr>
 *   <tr><td>GET</td><td>/api/v1/auth/login-url</td><td>No</td>
 *       <td>Returns the OAuth2 initiation URL so the SPA can start GitHub login</td></tr>
 *   <tr><td>GET</td><td>/api/v1/auth/me</td><td>Yes</td>
 *       <td>Returns the currently logged-in user's profile as {@link UserResponse}</td></tr>
 *   <tr><td>POST</td><td>/api/v1/auth/logout</td><td>No (handled by Spring Security)</td>
 *       <td>Configured in {@code SecurityConfig} — not a controller method</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CurrentUser currentUser;

    /**
     * Returns the URL the browser should navigate to in order to start the
     * GitHub OAuth2 authorization code flow.
     *
     * <p>Spring Security's {@code OAuth2AuthorizationRequestRedirectFilter}
     * handles this URL ({@code /oauth2/authorization/github}) automatically.
     * We expose it here so the frontend doesn't need to hard-code it.
     */
    @GetMapping("/login-url")
    public Map<String, String> loginUrl() {
        return Map.of("url", "/oauth2/authorization/github");
    }

    /**
     * Returns the profile of the currently authenticated user.
     *
     * <p>The SPA calls this endpoint after the OAuth2 callback redirect to
     * get the user's identity (name, avatar, etc.) for display in the UI.
     *
     * @return 200 OK with {@link UserResponse} body if authenticated;
     *         401 Unauthorized if no valid session exists (handled by Spring Security)
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        AppUserPrincipal principal = currentUser.require();
        User user = principal.getUser();

        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getGithubId(),
                user.getGithubUsername(),
                user.getDisplayName(),
                user.getAvatarUrl()
        ));
    }
}
