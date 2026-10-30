package com.devpilot.backend.controllers;

import com.devpilot.backend.dto.IndexStatusResponse;
import com.devpilot.backend.dto.RepositoryResponse;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.services.RepoService;
import lombok.RequiredArgsConstructor;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.services.indexing.IndexingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 *   <tr><td>POST</td><td>/api/v1/repos/{id}/index</td>
 *       <td>Triggers asynchronous indexing for a repository. Returns 202 Accepted.</td></tr>
 *   <tr><td>GET</td><td>/api/v1/repos/{id}/status</td>
 *       <td>Get indexing progress for a repo. Used by the SPA polling loop.</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/repos")
@RequiredArgsConstructor
public class RepoController {

    private final CurrentUser currentUser;
    private final RepoService repoService;
    private final IndexingService indexingService;

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
     * Triggers asynchronous repository indexing.
     * Returns 202 Accepted with the updated repository state (IndexStatus.INDEXING).
     */
    @PostMapping("/{id}/index")
    public ResponseEntity<RepositoryResponse> index(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        ConnectedRepository repo = indexingService.startIndexing(id, userId);
        indexingService.indexAsync(id, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(repoService.toResponse(repo));
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
