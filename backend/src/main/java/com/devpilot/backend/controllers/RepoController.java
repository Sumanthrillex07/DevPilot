package com.devpilot.backend.controllers;

import com.devpilot.backend.dto.IndexStatusResponse;
import com.devpilot.backend.dto.RepositoryResponse;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.services.RepoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for connected repository operations.
 *
 * <p>All endpoints require authentication (enforced by {@code SecurityConfig}).
 * The {@link CurrentUser} helper extracts the authenticated user's UUID from the
 * session without hitting the database.
 *
 * <h3>Endpoints</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/v1/repos</td>
 *       <td>List repos. {@code ?refresh=true} (default) syncs from GitHub first.</td></tr>
 *   <tr><td>GET</td><td>/api/v1/repos/{id}</td>
 *       <td>Get a single repo by our internal UUID. Returns 404 if not owned by caller.</td></tr>
 *   <tr><td>GET</td><td>/api/v1/repos/{id}/status</td>
 *       <td>Get indexing progress for a repo. Used by the SPA polling loop.</td></tr>
 * </table>
 *
 * <p><b>Note on indexing trigger ({@code POST /{id}/index}):</b><br>
 * The indexing endpoint is intentionally left out of Part 4 — it will be added in
 * Part 5 alongside the actual indexing logic. Wiring an endpoint that does nothing
 * yet would be misleading.
 */
@RestController
@RequestMapping("/api/v1/repos")
@RequiredArgsConstructor
public class RepoController {

    private final CurrentUser currentUser;
    private final RepoService repoService;

    /**
     * Lists repositories for the authenticated user.
     *
     * @param refresh If {@code true} (default), syncs with GitHub before returning.
     *                If {@code false}, returns the stored list instantly (no API call).
     */
    @GetMapping
    public List<RepositoryResponse> list(
            @RequestParam(name = "refresh", defaultValue = "true") boolean refresh) {
        UUID userId = currentUser.require().getId();
        if (refresh) {
            return repoService.syncAndListRepos(userId);
        }
        return repoService.listStored(userId);
    }

    /**
     * Returns a single repository by its internal UUID.
     * Returns 404 if the repo doesn't exist or doesn't belong to the caller.
     */
    @GetMapping("/{id}")
    public RepositoryResponse get(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return repoService.toResponse(repoService.requireOwned(id, userId));
    }

    /**
     * Returns the current indexing status for a repository.
     * The SPA polls this endpoint after triggering indexing.
     */
    @GetMapping("/{id}/status")
    public IndexStatusResponse status(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return repoService.status(id, userId);
    }
}
