package com.devpilot.backend.services;

import com.devpilot.backend.entities.User;
import com.devpilot.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Application-level user operations: upsert on GitHub login, token decryption,
 * and lookup by ID.
 *
 * <p><b>Upsert pattern explained:</b><br>
 * Every time a user logs in with GitHub, OAuth2 calls {@code upsertFromGitHub}.
 * We either:
 * <ul>
 *   <li>Create a new row if the GitHub ID has never been seen, or</li>
 *   <li>Update the existing row with the latest display name, avatar, and token.</li>
 * </ul>
 * This is "upsert" (update + insert). The single {@code @Transactional} annotation
 * ensures both the read and write happen in the same database transaction, so we
 * never create duplicate users even under concurrent logins.
 *
 * <p><b>Token encryption:</b><br>
 * The raw GitHub access token is encrypted with AES-256 (via {@link TextEncryptor}
 * from {@code CryptoConfig}) before being stored in the {@code users.access_token}
 * column. Later, when we need to call the GitHub API on behalf of the user, we decrypt
 * it with {@link #decryptAccessToken(User)}.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TextEncryptor tokenEncryptor;

    /**
     * Creates or updates a user record from the data returned by GitHub's OAuth2 flow.
     *
     * @param attributes  Raw attribute map from GitHub's /user API
     *                    (keys: "id", "login", "name", "avatar_url", ...)
     * @param accessToken Plain-text GitHub access token — will be encrypted before storage
     * @param scopes      Comma-separated OAuth2 scopes granted (e.g. "read:user,repo")
     * @return The persisted (and possibly newly created) {@link User} entity
     */
    @Transactional
    public User upsertFromGitHub(Map<String, Object> attributes, String accessToken, String scopes) {
        Long githubId = toLong(attributes.get("id"));
        String login = String.valueOf(attributes.get("login"));

        // GitHub users may not have a display name set — fall back to login
        String name = attributes.get("name") != null
                ? String.valueOf(attributes.get("name"))
                : login;

        String avatarUrl = attributes.get("avatar_url") != null
                ? String.valueOf(attributes.get("avatar_url"))
                : null;

        // Encrypt before touching the DB — the raw token never reaches the column
        String encryptedToken = tokenEncryptor.encrypt(accessToken);

        // Upsert: find existing by GitHub ID or create a brand-new User
        User user = userRepository.findByGithubId(githubId).orElseGet(User::new);
        user.setGithubId(githubId);
        user.setGithubUsername(login);
        user.setDisplayName(name);
        user.setAvatarUrl(avatarUrl);
        user.setAccessToken(encryptedToken);
        user.setTokenScopes(scopes);

        return userRepository.save(user);
    }

    /**
     * Loads a user by our internal UUID. Used by services that receive the UUID
     * from {@link com.devpilot.backend.security.AppUserPrincipal#getId()}.
     *
     * @throws IllegalArgumentException if no user with that ID exists
     */
    @Transactional(readOnly = true)
    public User requiredById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    /**
     * Decrypts and returns the raw GitHub access token for this user.
     * Call this only when you need to make a GitHub API request on behalf of the user.
     */
    public String decryptAccessToken(User user) {
        return tokenEncryptor.decrypt(user.getAccessToken());
    }

    // ---- private helpers ----

    /**
     * GitHub returns the user ID as a JSON Number. Depending on the JSON library,
     * it may arrive as an Integer or a Long. This helper handles both safely.
     */
    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
