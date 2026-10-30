package com.devpilot.backend.services.indexing;

import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.IndexStatus;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.exceptions.BadRequestException;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.UserService;
import com.devpilot.backend.services.github.GitHubRateLimiter;
import com.devpilot.backend.services.github.GithubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IndexingService}.
 *
 * <p>Uses Mockito to test all state transitions, validation, and orchestration logic
 * without requiring live GitHub or vector database connections.
 */
@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private ConnectedRepositoryRepository repositoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private GithubApiClient gitHubApiClient;

    @Mock
    private CodeFileFilter fileFilter;

    @Mock
    private CodeChunker codeChunker;

    @Mock
    private GitHubRateLimiter rateLimiter;

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private IndexingService indexingService;

    private UUID userId;
    private UUID repoId;
    private ConnectedRepository testRepo;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();

        testRepo = ConnectedRepository.builder()
                .id(repoId)
                .userId(userId)
                .owner("octocat")
                .name("hello-world")
                .fullName("octocat/hello-world")
                .defaultBranch("main")
                .indexStatus(IndexStatus.PENDING)
                .build();

        testUser = new User();
        testUser.setId(userId);
        testUser.setAccessToken("enc-token");
    }

    // --- startIndexing Tests ---

    @Test
    @DisplayName("startIndexing: transitions to INDEXING and resets counters when eligible")
    void startIndexing_transitionsToIndexing() {
        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(testRepo));
        when(repositoryRepository.save(any(ConnectedRepository.class))).thenAnswer(i -> i.getArgument(0));

        ConnectedRepository result = indexingService.startIndexing(repoId, userId);

        assertThat(result.getIndexStatus()).isEqualTo(IndexStatus.INDEXING);
        assertThat(result.getFilesProcessed()).isZero();
        assertThat(result.getFilesTotal()).isZero();
        assertThat(result.getChunkCount()).isZero();
        assertThat(result.getErrorMessage()).isNull();
        verify(repositoryRepository).save(testRepo);
    }

    @Test
    @DisplayName("startIndexing: throws BadRequestException when repo is already indexing")
    void startIndexing_throwsBadRequest_whenAlreadyIndexing() {
        testRepo.setIndexStatus(IndexStatus.INDEXING);
        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(testRepo));

        assertThatThrownBy(() -> indexingService.startIndexing(repoId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already being indexed");

        verify(repositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("startIndexing: throws NotFoundException when repo does not exist or wrong user")
    void startIndexing_throwsNotFound_whenNotOwned() {
        when(repositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> indexingService.startIndexing(repoId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Repository not found");
    }

    // --- indexAsync / doIndex Orchestration Tests ---

    @Test
    @DisplayName("indexAsync: successfully fetches tree, chunks files, adds to vector store, and marks READY")
    void indexAsync_completesSuccessfully() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(testRepo));
        when(userService.requiredById(userId)).thenReturn(testUser);
        when(userService.decryptAccessToken(testUser)).thenReturn("raw-token");

        // Mock GitHub tree response with 2 files
        Map<String, Object> tree = Map.of(
                "tree", List.of(
                        Map.of("type", "blob", "path", "src/App.java", "size", 100),
                        Map.of("type", "blob", "path", "src/Util.java", "size", 200)
                )
        );
        when(gitHubApiClient.getRepoTree("raw-token", "octocat", "hello-world", "main")).thenReturn(tree);
        when(fileFilter.isEligible(anyString(), anyLong(), anyLong())).thenReturn(true);

        when(gitHubApiClient.getFileContent("raw-token", "octocat", "hello-world", "src/App.java"))
                .thenReturn("public class App {}");
        when(gitHubApiClient.getFileContent("raw-token", "octocat", "hello-world", "src/Util.java"))
                .thenReturn("public class Util {}");

        Document doc1 = new Document("App text");
        Document doc2 = new Document("Util text");
        when(codeChunker.chunkFileToDocuments(eq(repoId.toString()), eq("src/App.java"), anyString()))
                .thenReturn(List.of(doc1));
        when(codeChunker.chunkFileToDocuments(eq(repoId.toString()), eq("src/Util.java"), anyString()))
                .thenReturn(List.of(doc2));

        when(repositoryRepository.save(any(ConnectedRepository.class))).thenAnswer(i -> i.getArgument(0));

        indexingService.indexAsync(repoId, userId);

        // Verify vectors deleted for clean re-index
        verify(vectorStore).delete(any(Filter.Expression.class));

        // Verify documents added to vector store
        verify(vectorStore, atLeastOnce()).add(anyList());

        // Verify rate limiter called for each processed file
        verify(rateLimiter, times(2)).pause();

        // Verify final state is READY with 2 files and 2 chunks
        assertThat(testRepo.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(testRepo.getFilesTotal()).isEqualTo(2);
        assertThat(testRepo.getFilesProcessed()).isEqualTo(2);
        assertThat(testRepo.getChunkCount()).isEqualTo(2);
        assertThat(testRepo.getIndexedAt()).isNotNull();
    }

    @Test
    @DisplayName("indexAsync: marks status as FAILED when unexpected error occurs during indexing")
    void indexAsync_marksFailed_onError() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(testRepo));
        when(userService.requiredById(userId)).thenReturn(testUser);
        when(userService.decryptAccessToken(testUser)).thenReturn("raw-token");

        // Simulate network failure when contacting GitHub
        when(gitHubApiClient.getRepoTree("raw-token", "octocat", "hello-world", "main"))
                .thenThrow(new RuntimeException("GitHub API unavailable"));

        indexingService.indexAsync(repoId, userId);

        assertThat(testRepo.getIndexStatus()).isEqualTo(IndexStatus.FAILED);
        assertThat(testRepo.getErrorMessage()).contains("GitHub API unavailable");
        verify(repositoryRepository, atLeastOnce()).save(testRepo);
    }
}
