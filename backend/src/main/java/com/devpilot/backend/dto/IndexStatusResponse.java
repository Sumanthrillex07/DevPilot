package com.devpilot.backend.dto;

import com.devpilot.backend.entities.IndexStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight response for polling the indexing progress of a single repository.
 *
 * <p>The SPA uses this in a short-polling loop after clicking "Index Repository".
 * It only needs to know the current status, progress counters, and any error message —
 * not the full repository metadata. Keeping it small reduces payload size.
 */
public record IndexStatusResponse(
        UUID repositoryId,
        IndexStatus indexStatus,
        int filesTotal,
        int filesProcessed,
        int chunkCount,
        Instant indexedAt,
        String errorMessage
) {}
