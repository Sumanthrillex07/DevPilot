package com.devpilot.backend.services;

import com.devpilot.backend.dto.IndexStatusResponse;
import com.devpilot.backend.dto.RepositoryResponse;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.github.GithubApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic for connected repositories.
 *
 * <p><b>The sync model:</b><br>
 * We do not store a cached copy of the GitHub repo list indefinitely. Instead, every
 * time the frontend calls {@code GET /api/v1/repos?refresh=true}, we call GitHub's
 * /user/repos API and update our database. This is called a "sync on read" pattern.
 *
 * <p>Pros:
 * <ul>
 *   <li>Data is always fresh — no stale cache to invalidate</li>
 *   <li>Handles repos being added, renamed, or deleted on GitHub automatically</li>
 * </ul>
 * Cons:
 * <ul>
 *   <li>Every page load makes a real API call (mitigated by {@code ?refresh=false})</li>
 * </ul>
 *
 * <p><b>Upsert pattern:</b><br>
 * For each repo returned by GitHub, we either create a new row or update the existing one
 * (matched by user_id + github_repo_id). This preserves our indexing state ({@code IndexStatus},
 * chunk counts, etc.) across syncs — a previously-indexed repo keeps its indexed state.
 */
@Service
@RequiredArgsConstructor
public class RepoService {

    private final ConnectedRepositoryRepository repositoryRepository;
    private final UserService userService;
    private final GithubApiClient githubApiClient;

    /**
     * Calls GitHub to get the latest repo list, upserts each repo into our database,
     * and returns the merged result sorted alphabetically by full_name.
     *
     * <p><b>Why return sorted?</b><br>
     * Sorting at the service layer (not in SQL) because we're sorting in-memory after
     * the upsert. We have the full list in memory anyway so a comparator sort is cheap.
     *
     * @param userId The current user's internal UUID (from the session principal)
     */
    @Transactional
    public List<RepositoryResponse> syncAndListRepos(UUID userId) {
        User user = userService.requiredById(userId);
        String accessToken = userService.decryptAccessToken(user);

        // Fetch all repos from GitHub (paginated, up to 1,000)
        List<Map<String, Object>> remoteRepos = githubApiClient.listUserRepos(accessToken);

        List<ConnectedRepository> saved = new ArrayList<>();

        for (Map<String, Object> remote : remoteRepos) {
            Long githubRepoId = toLong(remote.get("id"));

            // Upsert: find existing row or start with a blank entity
            ConnectedRepository repo = repositoryRepository
                    .findByUserIdAndGithubRepoId(userId, githubRepoId)
                    .orElseGet(ConnectedRepository::new);

            // Parse owner from "owner/repo" full_name (most reliable source)
            String fullName = String.valueOf(remote.get("full_name"));
            String[] parts = fullName.split("/", 2);

            repo.setUserId(userId);
            repo.setGithubRepoId(githubRepoId);
            repo.setFullName(fullName);
            repo.setOwner(parts.length > 0 ? parts[0] : extractOwnerFromMap(remote));
            repo.setName(parts.length > 1 ? parts[1] : String.valueOf(remote.get("name")));
            repo.setPrivate(Boolean.TRUE.equals(remote.get("private")));
            repo.setDefaultBranch(remote.get("default_branch") != null
                    ? String.valueOf(remote.get("default_branch"))
                    : "main");
            repo.setLanguage(remote.get("language") != null
                    ? String.valueOf(remote.get("language"))
                    : null);
            repo.setHtmlUrl(remote.get("html_url") != null
                    ? String.valueOf(remote.get("html_url"))
                    : null);
            repo.setDescription(remote.get("description") != null
                    ? String.valueOf(remote.get("description"))
                    : null);
            repo.setUpdatedAt(Instant.now());

            saved.add(repositoryRepository.save(repo));
        }

        // Sort alphabetically for a stable, predictable list
        return saved.stream()
                .sorted((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns the stored repository list from our database without calling GitHub.
     * Used when {@code ?refresh=false} is passed — fast, no API call.
     */
    @Transactional(readOnly = true)
    public List<RepositoryResponse> listStored(UUID userId) {
        return repositoryRepository.findByUserIdOrderByFullNameAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Loads a repository that must exist AND belong to the given user.
     *
     * <p>We always check ownership — never just {@code findById}. This prevents
     * User A from accessing User B's data by guessing a UUID (IDOR attack).
     *
     * @throws NotFoundException (HTTP 404) if the repo doesn't exist or doesn't belong to userId
     */
    @Transactional(readOnly = true)
    public ConnectedRepository requireOwned(UUID repoId, UUID userId) {
        return repositoryRepository.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));
    }

    /**
     * Returns the current indexing status/progress for a repository.
     */
    @Transactional(readOnly = true)
    public IndexStatusResponse status(UUID repoId, UUID userId) {
        ConnectedRepository repo = requireOwned(repoId, userId);
        return new IndexStatusResponse(
                repo.getId(),
                repo.getIndexStatus(),
                repo.getFilesTotal(),
                repo.getFilesProcessed(),
                repo.getChunkCount(),
                repo.getIndexedAt(),
                repo.getErrorMessage()
        );
    }

    /**
     * Converts a {@link ConnectedRepository} entity to its API response form.
     * Public so it can be reused by RepoController and future indexing endpoints.
     */
    public RepositoryResponse toResponse(ConnectedRepository repo) {
        return new RepositoryResponse(
                repo.getId(),
                repo.getGithubRepoId(),
                repo.getOwner(),
                repo.getName(),
                repo.getFullName(),
                repo.isPrivate(),
                repo.getDefaultBranch(),
                repo.getLanguage(),
                repo.getHtmlUrl(),
                repo.getDescription(),
                repo.getIndexStatus(),
                repo.getIndexedAt(),
                repo.getChunkCount(),
                repo.getFilesTotal(),
                repo.getFilesProcessed(),
                repo.getErrorMessage()
        );
    }

    // ---- private helpers ----

    /**
     * GitHub sometimes returns the owner as a nested "owner" object (with "login" key)
     * rather than inline in full_name. This handles both cases.
     */
    private static String extractOwnerFromMap(Map<String, Object> remote) {
        Object ownerObj = remote.get("owner");
        if (ownerObj instanceof Map<?, ?> ownerMap && ownerMap.get("login") != null) {
            return String.valueOf(ownerMap.get("login"));
        }
        return "";
    }

    /**
     * GitHub returns numeric IDs as JSON Number, which Jackson may parse as Integer or Long.
     * This helper normalises both to Long.
     */
    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
