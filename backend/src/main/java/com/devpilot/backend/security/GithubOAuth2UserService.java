package com.devpilot.backend.security;

import com.devpilot.backend.entities.User;
import com.devpilot.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Custom OAuth2 user service that hooks into the GitHub login flow.
 *
 * <p><b>How OAuth2 login works in Spring Security:</b><br>
 * After GitHub redirects back with an authorization code, Spring Security:
 * <ol>
 *   <li>Exchanges the code for an access token (handled automatically)</li>
 *   <li>Calls <em>this service</em> with an {@link OAuth2UserRequest} containing that token</li>
 *   <li>Uses the returned {@link OAuth2User} as the authentication principal</li>
 * </ol>
 *
 * <p><b>Our extension points:</b>
 * <ul>
 *   <li>We let the {@link DefaultOAuth2UserService} (the "delegate") call GitHub's
 *       /user API and parse the attributes — no need to rewrite that logic.</li>
 *   <li>We then call {@link UserService#upsertFromGitHub} to persist/update the user
 *       in our own database.</li>
 *   <li>Finally, we return an {@link AppUserPrincipal} — our custom wrapper — so
 *       that every subsequent request can access our internal UUID without a DB call.</li>
 * </ul>
 *
 * <p><b>Token scopes handling:</b><br>
 * GitHub often omits the {@code scope} claim from the token response. When absent,
 * we fall back to a safe default string so the column is never null.
 */
@Service
@RequiredArgsConstructor
public class GithubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserService userService;

    /**
     * Spring's built-in implementation that fetches user attributes from the
     * provider's userinfo endpoint (for GitHub: https://api.github.com/user).
     * We delegate to it so we don't have to rewrite the HTTP + JSON parsing logic.
     */
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // Step 1: Let the delegate fetch and parse GitHub's /user response
        OAuth2User githubUser = delegate.loadUser(userRequest);

        // Step 2: Extract the plain-text access token (we'll encrypt it in UserService)
        String accessToken = userRequest.getAccessToken().getTokenValue();

        // Step 3: Extract granted scopes, falling back to a default if GitHub omits them
        String scopes = (userRequest.getAccessToken().getScopes() != null
                && !userRequest.getAccessToken().getScopes().isEmpty())
                ? String.join(",", userRequest.getAccessToken().getScopes())
                : "read:user,repo";

        // Step 4: Upsert into our database and return our custom principal
        User user = userService.upsertFromGitHub(githubUser.getAttributes(), accessToken, scopes);
        return new AppUserPrincipal(user, githubUser.getAttributes());
    }
}
