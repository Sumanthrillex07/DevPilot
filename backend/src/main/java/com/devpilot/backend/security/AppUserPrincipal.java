package com.devpilot.backend.security;

import com.devpilot.backend.entities.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps our {@link User} JPA entity as a Spring Security {@link OAuth2User}.
 *
 * <p><b>Why do we need this wrapper?</b><br>
 * Spring Security's {@code OAuth2LoginAuthenticationFilter} demands an {@link OAuth2User}
 * as the principal. The default implementation ({@code DefaultOAuth2User}) only holds
 * the raw attributes returned from GitHub's /user API — it knows nothing about our
 * database User ID or any app-specific fields.
 *
 * <p>By creating {@code AppUserPrincipal} we can:
 * <ul>
 *   <li>Store our internal UUID so controllers never have to query the DB again</li>
 *   <li>Keep the raw GitHub attributes for any code that needs them</li>
 *   <li>Assign roles (e.g. ROLE_USER) at login time</li>
 * </ul>
 *
 * <p>The {@link CurrentUser} component extracts this principal from the
 * {@code SecurityContextHolder} in every request that needs the logged-in user.
 */
public class AppUserPrincipal implements OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;

    public AppUserPrincipal(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    /** Our internal database UUID for this user. */
    public UUID getId() {
        return user.getId();
    }

    /** Full JPA entity — use sparingly; prefer getId() in most cases. */
    public User getUser() {
        return user;
    }

    // ---- OAuth2User contract ----

    /** Raw attributes from GitHub's /user API. */
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Grants a single {@code ROLE_USER} authority to every authenticated user.
     * Role-based access control can be expanded here in future parts.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AuthorityUtils.createAuthorityList("ROLE_USER");
    }

    /**
     * The "name" of this principal in Spring Security terms.
     * We use our UUID string (not the GitHub login) so that this value is
     * stable even if the user renames their GitHub account.
     */
    @Override
    public String getName() {
        return user.getId().toString();
    }
}
