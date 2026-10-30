package com.devpilot.backend.services.indexing;

import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.IndexStatus;
import com.devpilot.backend.exceptions.BadRequestException;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.UserService;
import com.devpilot.backend.services.ai.RagSettings;
import com.devpilot.backend.services.github.GitHubRateLimiter;
import com.devpilot.backend.services.github.GithubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service that orchestrates asynchronous indexing of repository source code into pgvector.
 *
 * <p><b>Lifecycle of an Indexing Job:</b>
 * <ol>
 *   <li>Synchronous validation ({@link #startIndexing}): Checks ownership, verifies not already indexing,
 *       sets state to {@link IndexStatus#INDEXING}, resets progress counters, and returns immediately.</li>
 *   <li>Asynchronous processing ({@link #indexAsync}): Dispatched to {@code indexingExecutor} thread pool.</li>
 *   <li>Vector Cleanup: Deletes existing vectors for this {@code repoId} to prevent duplicates on re-index.</li>
 *   <li>Discovery & Filtering: Recursively fetches the GitHub Git tree, filters eligible code files via {@link CodeFileFilter}.</li>
 *   <li>Batch Chunking & Ingestion: Fetches content, splits with line numbers via {@link CodeChunker},
 *       flushes documents in batches of 32 to {@link VectorStore}, and updates progress counters every 5 files.</li>
 *   <li>Rate Limiting: Pauses between requests via {@link GitHubRateLimiter} to stay safely below secondary limits.</li>
 *   <li>Completion: Marks state as {@link IndexStatus#READY} (or {@link IndexStatus#FAILED} on error).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingService {

    private static final int VECTOR_BATCH_SIZE = 32;
    private static final int PROGRESS_EVERY_N_FILES = 5;

    private final ConnectedRepositoryRepository repositoryRepository;
    private final UserService userService;
    private final GithubApiClient gitHubApiClient;
    private final CodeFileFilter fileFilter;
    private final CodeChunker codeChunker;
    private final GitHubRateLimiter rateLimiter;
    private final VectorStore vectorStore;
    private final com.devpilot.backend.services.graph.DependencyGraphService dependencyGraphService;
    private final com.devpilot.backend.services.graph.CodeRelationshipExtractor relationshipExtractor;

    @Value("${devpilot.indexing.max-file-bytes:" + RagSettings.DEFAULT_MAX_FILE_BYTES + "}")
    private long maxFileBytes;

    /**
     * Prepares and starts the indexing process synchronously.
     * Transitions repository state to {@link IndexStatus#INDEXING}.
     *
     * @throws NotFoundException   if repository doesn't exist or isn't owned by userId
     * @throws BadRequestException if repository is already actively indexing
     */
    @Transactional
    public ConnectedRepository startIndexing(UUID repoId, UUID userId) {
        ConnectedRepository repo = repositoryRepository.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));

        if (repo.getIndexStatus() == IndexStatus.INDEXING) {
            throw new BadRequestException("Repository is already being indexed");
        }

        repo.setIndexStatus(IndexStatus.INDEXING);
        repo.setFilesProcessed(0);
        repo.setFilesTotal(0);
        repo.setChunkCount(0);
        repo.setErrorMessage(null);
        repo.setUpdatedAt(Instant.now());

        return repositoryRepository.save(repo);
    }

    /**
     * Executes the heavy indexing pipeline asynchronously in the background.
     */
    @Async("indexingExecutor")
    public void indexAsync(UUID repoId, UUID userId) {
        try {
            doIndex(repoId, userId);
        } catch (Exception ex) {
            log.error("Indexing failed for repository {}", repoId, ex);
            markFailed(repoId, ex.getMessage());
        }
    }

    private void doIndex(UUID repoId, UUID userId) {
        ConnectedRepository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));

        String token = userService.decryptAccessToken(userService.requiredById(userId));

        // Delete existing vectors for this repository to guarantee idempotent re-indexing
        deleteExistingVectors(repoId.toString());

        // 1. Fetch entire recursive tree from GitHub
        Map<String, Object> tree = gitHubApiClient.getRepoTree(
                token, repo.getOwner(), repo.getName(), repo.getDefaultBranch());
        List<String> filePaths = listIndexableFiles(tree);

        updateProgress(repoId, filePaths.size(), 0, 0, IndexStatus.INDEXING, null);

        List<Document> batch = new ArrayList<>();
        int processed = 0;
        int totalChunks = 0;

        // 2. Process each file
        for (String path : filePaths) {
            try {
                String content = gitHubApiClient.getFileContent(
                        token, repo.getOwner(), repo.getName(), path);
                List<Document> chunks = codeChunker.chunkFileToDocuments(repoId.toString(), path, content);
                batch.addAll(chunks);
                totalChunks += chunks.size();

                if (dependencyGraphService != null && relationshipExtractor != null) {
                    var analysis = relationshipExtractor.extract(path, content);
                    for (var rel : analysis.relations()) {
                        dependencyGraphService.addRelation(repoId, rel);
                    }
                }

                if (batch.size() >= VECTOR_BATCH_SIZE) {
                    vectorStore.add(batch);
                    batch.clear();
                }
            } catch (Exception ex) {
                log.warn("Skipping file {} in {}: {}", path, repo.getFullName(), ex.getMessage());
            }

            processed++;
            if (processed % PROGRESS_EVERY_N_FILES == 0 || processed == filePaths.size()) {
                updateProgress(repoId, filePaths.size(), processed, totalChunks, IndexStatus.INDEXING, null);
            }

            rateLimiter.pause();
        }

        // 3. Flush final remaining vector batch
        if (!batch.isEmpty()) {
            vectorStore.add(batch);
        }

        markReady(repoId, filePaths.size(), processed, totalChunks, repo.getFullName());
    }

    @SuppressWarnings("unchecked")
    private List<String> listIndexableFiles(Map<String, Object> tree) {
        if (tree == null || tree.get("tree") == null) {
            return List.of();
        }

        List<Map<String, Object>> entries = (List<Map<String, Object>>) tree.get("tree");
        return entries.stream()
                .filter(entry -> "blob".equals(String.valueOf(entry.get("type"))))
                .filter(entry -> {
                    String path = String.valueOf(entry.get("path"));
                    long size = entry.get("size") instanceof Number n ? n.longValue() : 0L;
                    return fileFilter.isEligible(path, size, maxFileBytes);
                })
                .map(entry -> String.valueOf(entry.get("path")))
                .toList();
    }

    private void deleteExistingVectors(String repoId) {
        try {
            var filter = new FilterExpressionBuilder().eq(RagSettings.METADATA_REPO_ID, repoId).build();
            vectorStore.delete(filter);
        } catch (Exception ex) {
            log.warn("Could not delete existing vectors for repo {}: {}", repoId, ex.getMessage());
        }
    }

    @Transactional
    protected void updateProgress(
            UUID repoId,
            int total,
            int processed,
            int chunks,
            IndexStatus status,
            String error) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setFilesTotal(total);
            repo.setFilesProcessed(processed);
            repo.setChunkCount(chunks);
            repo.setIndexStatus(status);
            repo.setErrorMessage(error);
            repo.setUpdatedAt(Instant.now());
            repositoryRepository.save(repo);
        });
    }

    @Transactional
    protected void markReady(UUID repoId, int totalFiles, int processedFiles, int totalChunks, String fullName) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setIndexStatus(IndexStatus.READY);
            repo.setFilesTotal(totalFiles);
            repo.setFilesProcessed(processedFiles);
            repo.setChunkCount(totalChunks);
            repo.setIndexedAt(Instant.now());
            repo.setErrorMessage(null);
            repo.setUpdatedAt(Instant.now());
            repositoryRepository.save(repo);
        });
        log.info("Successfully indexed {} files ({} chunks) for {}", processedFiles, totalChunks, fullName);
    }

    @Transactional
    protected void markFailed(UUID repoId, String message) {
        repositoryRepository.findById(repoId).ifPresent(repo -> {
            repo.setIndexStatus(IndexStatus.FAILED);
            repo.setErrorMessage(message != null && message.length() > 2000
                    ? message.substring(0, 2000)
                    : message);
            repo.setUpdatedAt(Instant.now());
            repositoryRepository.save(repo);
        });
    }
}
