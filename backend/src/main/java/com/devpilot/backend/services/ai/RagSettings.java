package com.devpilot.backend.services.ai;

/**
 * Shared configuration constants for Retrieval-Augmented Generation (RAG)
 * and repository indexing.
 *
 * <p>Centralising metadata keys ensures that the keys written during indexing
 * (Part 5 & 6) match the keys queried during retrieval and citation mapping (Part 7).
 */
public final class RagSettings {

    /** How many code chunks to retrieve from pgvector per user question. */
    public static final int TOP_K_CHUNKS = 8;

    /** Maximum time (ms) to keep an SSE connection alive during streaming generation. */
    public static final long STREAM_TIMEOUT_MS = 180_000L;

    // --- Vector Document Metadata Keys ---
    public static final String METADATA_REPO_ID = "repoId";
    public static final String METADATA_FILE_PATH = "filePath";
    public static final String METADATA_FILE_NAME = "fileName";
    public static final String METADATA_LANGUAGE = "language";
    public static final String METADATA_START_LINE = "startLine";
    public static final String METADATA_END_LINE = "endLine";
    public static final String METADATA_CHUNK_INDEX = "chunkIndex";
    public static final String METADATA_CONTENT_HASH = "contentHash";

    // --- Indexing Defaults ---
    public static final int DEFAULT_CHUNK_SIZE_TOKENS = 800;
    public static final int DEFAULT_CHUNK_OVERLAP_TOKENS = 100;
    public static final long DEFAULT_MAX_FILE_BYTES = 300_000L; // 300 KB

    private RagSettings() {
        // Prevent instantiation of constant utility class
    }
}
