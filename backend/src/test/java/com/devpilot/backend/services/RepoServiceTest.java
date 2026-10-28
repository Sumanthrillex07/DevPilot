package com.devpilot.backend.services;

import com.devpilot.backend.dto.IndexStatusResponse;
import com.devpilot.backend.dto.RepositoryResponse;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.IndexStatus;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.github.GithubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RepoService}.
 *
 * <p><b>Testing strategy:</b><br>
 * We use {@code @ExtendWith(MockitoExtension.class)} (pure Mockito, no Spring context)
 * because {@link RepoService} has no Spring-specific behaviour that needs the container.
 * This makes tests ~100x faster than {@code @SpringBootTest}.
 *
 * <p>All collaborators ({@link GithubApiClient}, {@link ConnectedRepositoryRepository},
 * {@link UserService}) are mocked. We're testing the service's own logic:
 * the upsert merge, the IDOR check, the DTO mapping.
 */
@ExtendWith(MockitoExtension.class)
class RepoServiceTest {

    @Mock
    private ConnectedRepositoryRepository repositoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private GithubApiClient githubApiClient;

    @InjectMocks
    private RepoService repoService;

    private UUID userId;
    private User stubUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        stubUser = new User();
        stubUser.setId(userId);
        stubUser.setAccessToken("encrypted-token");
    }

    // ---- syncAndListRepos ----

    @Test
    @DisplayName("syncAndListRepos: calls GitHub, upserts repos, returns sorted list")
    void syncAndListRepos_callsGitHubAndUpserts() {
        when(userService.requiredById(userId)).thenReturn(stubUser);
        when(userService.decryptAccessToken(stubUser)).thenReturn("raw-token");
        when(githubApiClient.listUserRepos("raw-token")).thenReturn(List.of(
                repoMap(1001L, "octocat/b-repo", "main", false),
                repoMap(1002L, "octocat/a-repo", "main", false)
        ));
        when(repositoryRepository.findByUserIdAndGithubRepoId(eq(userId), anyLong()))
                .thenReturn(Optional.empty());
        when(repositoryRepository.save(any(ConnectedRepository.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<RepositoryResponse> result = repoService.syncAndListRepos(userId);

        assertThat(result).hasSize(2);
        // Must be sorted A before B
        assertThat(result.get(0).fullName()).isEqualTo("octocat/a-repo");
        assertThat(result.get(1).fullName()).isEqualTo("octocat/b-repo");
        verify(repositoryRepository, times(2)).save(any(ConnectedRepository.class));
    }

    @Test
    @DisplayName("syncAndListRepos: existing repo retains its IndexStatus (upsert preserves state)")
    void syncAndListRepos_existingRepoKeepsIndexStatus() {
        ConnectedRepository existing = new ConnectedRepository();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setGithubRepoId(1001L);
        existing.setIndexStatus(IndexStatus.READY);
        existing.setChunkCount(42);

        when(userService.requiredById(userId)).thenReturn(stubUser);
        when(userService.decryptAccessToken(stubUser)).thenReturn("raw-token");
        when(githubApiClient.listUserRepos("raw-token")).thenReturn(List.of(
                repoMap(1001L, "octocat/my-repo", "main", false)
        ));
        when(repositoryRepository.findByUserIdAndGithubRepoId(userId, 1001L))
                .thenReturn(Optional.of(existing));
        when(repositoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        List<RepositoryResponse> result = repoService.syncAndListRepos(userId);

        assertThat(result).hasSize(1);
        // The IndexStatus must be preserved — sync must not reset it to PENDING
        assertThat(result.get(0).indexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(result.get(0).chunkCount()).isEqualTo(42);
    }

    @Test
    @DisplayName("syncAndListRepos: empty GitHub response returns empty list")
    void syncAndListRepos_emptyGitHubResponse() {
        when(userService.requiredById(userId)).thenReturn(stubUser);
        when(userService.decryptAccessToken(stubUser)).thenReturn("raw-token");
        when(githubApiClient.listUserRepos("raw-token")).thenReturn(List.of());

        List<RepositoryResponse> result = repoService.syncAndListRepos(userId);

        assertThat(result).isEmpty();
        verify(repositoryRepository, never()).save(any());
    }

    // ---- listStored ----

    @Test
    @DisplayName("listStored: returns DB repos without calling GitHub")
    void listStored_returnsStoredRepos_noGitHubCall() {
        ConnectedRepository repo = storedRepo(UUID.randomUUID(), "octocat/stored-repo");
        when(repositoryRepository.findByUserIdOrderByFullNameAsc(userId))
                .thenReturn(List.of(repo));

        List<RepositoryResponse> result = repoService.listStored(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("octocat/stored-repo");
        verifyNoInteractions(githubApiClient);
    }

    // ---- requireOwned ----

    @Test
    @DisplayName("requireOwned: returns repo when it exists and belongs to user")
    void requireOwned_returnsRepo_whenOwnedByUser() {
        UUID repoId = UUID.randomUUID();
        ConnectedRepository repo = storedRepo(repoId, "octocat/my-repo");
        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));

        ConnectedRepository result = repoService.requireOwned(repoId, userId);

        assertThat(result.getId()).isEqualTo(repoId);
    }

    @Test
    @DisplayName("requireOwned: throws NotFoundException when repo not found (IDOR prevention)")
    void requireOwned_throwsNotFound_whenNotOwned() {
        UUID repoId = UUID.randomUUID();
        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> repoService.requireOwned(repoId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Repository not found");
    }

    // ---- status ----

    @Test
    @DisplayName("status: maps entity fields to IndexStatusResponse correctly")
    void status_mapsFieldsCorrectly() {
        UUID repoId = UUID.randomUUID();
        ConnectedRepository repo = storedRepo(repoId, "octocat/my-repo");
        repo.setIndexStatus(IndexStatus.INDEXING);
        repo.setFilesTotal(100);
        repo.setFilesProcessed(50);
        repo.setChunkCount(200);

        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));

        IndexStatusResponse result = repoService.status(repoId, userId);

        assertThat(result.repositoryId()).isEqualTo(repoId);
        assertThat(result.indexStatus()).isEqualTo(IndexStatus.INDEXING);
        assertThat(result.filesTotal()).isEqualTo(100);
        assertThat(result.filesProcessed()).isEqualTo(50);
        assertThat(result.chunkCount()).isEqualTo(200);
    }

    // ---- helpers ----

    private static Map<String, Object> repoMap(long id, String fullName, String branch, boolean isPrivate) {
        return Map.of(
                "id", id,
                "full_name", fullName,
                "name", fullName.split("/")[1],
                "default_branch", branch,
                "private", isPrivate
        );
    }

    private static ConnectedRepository storedRepo(UUID id, String fullName) {
        ConnectedRepository repo = new ConnectedRepository();
        repo.setId(id);
        repo.setUserId(UUID.randomUUID());
        repo.setGithubRepoId(999L);
        repo.setFullName(fullName);
        String[] parts = fullName.split("/", 2);
        repo.setOwner(parts[0]);
        repo.setName(parts[1]);
        repo.setDefaultBranch("main");
        repo.setIndexStatus(IndexStatus.PENDING);
        return repo;
    }
}
