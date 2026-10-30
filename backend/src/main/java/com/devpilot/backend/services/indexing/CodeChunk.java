package com.devpilot.backend.services.indexing;

import com.devpilot.backend.services.ai.RagSettings;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable representation of a chunk of source code extracted from a repository file.
 *
 * <p>Unlike basic text splitters that only retain text, {@code CodeChunk} preserves
 * the exact 1-indexed line range ({@link #startLine()} to {@link #endLine()}) and
 * a deterministic SHA-256 hash. This enables:
 * <ul>
 *   <li>Line-level citations in chat answers (e.g., {@code App.java#L24-L58})</li>
 *   <li>Direct links to GitHub lines in the UI</li>
 *   <li>Deduplication and incremental indexing via {@link #contentHash()}</li>
 * </ul>
 */
public record CodeChunk(
        String repoId,
        String filePath,
        String fileName,
        String language,
        int startLine,
        int endLine,
        int chunkIndex,
        String contentHash,
        String text
) {

    /**
     * Converts this chunk into a Spring AI {@link Document} for insertion into pgvector.
     * Metadata keys match {@link RagSettings}.
     */
    public Document toDocument() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(RagSettings.METADATA_REPO_ID, repoId);
        metadata.put(RagSettings.METADATA_FILE_PATH, filePath);
        metadata.put(RagSettings.METADATA_FILE_NAME, fileName);
        metadata.put(RagSettings.METADATA_LANGUAGE, language);
        metadata.put(RagSettings.METADATA_START_LINE, startLine);
        metadata.put(RagSettings.METADATA_END_LINE, endLine);
        metadata.put(RagSettings.METADATA_CHUNK_INDEX, chunkIndex);
        metadata.put(RagSettings.METADATA_CONTENT_HASH, contentHash);

        return new Document(text, metadata);
    }
}
