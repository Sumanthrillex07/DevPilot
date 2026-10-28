package com.devpilot.backend.security;

import com.devpilot.backend.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Convenience component that extracts the currently-logged-in user's principal
 * from Spring Security's {@link SecurityContextHolder}.
 *
 * <p><b>Usage pattern in controllers:</b>
 * <pre>{@code
 * @Autowired CurrentUser currentUser;
 *
 * @GetMapping("/me")
 * public ResponseEntity<UserResponse> me() {
 *     AppUserPrincipal principal = currentUser.require();
 *     // ... use principal.getId(), principal.getUser() ...
 * }
 * }</pre>
 *
 * <p><b>Why a component instead of a method parameter annotation?</b><br>
 * Spring MVC supports {@code @AuthenticationPrincipal} as a method argument resolver,
 * but it couples every controller method signature to the security type. Using a
 * dedicated component:
 * <ul>
 *   <li>Is easier to mock in tests — just stub {@code currentUser.require()}</li>
 *   <li>Works identically in service-layer methods called outside MVC context</li>
 *   <li>Fails fast with a clear {@link UnauthorizedException} rather than a NPE</li>
 * </ul>
 */
@Component
public class CurrentUser {

    /**
     * Returns the {@link AppUserPrincipal} for the current request.
     *
     * @throws UnauthorizedException if there is no authentication or the principal
     *                               is not an {@link AppUserPrincipal} (e.g. anonymous)
     */
    public AppUserPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new UnauthorizedException("Not authenticated");
        }
        return principal;
    }
}
