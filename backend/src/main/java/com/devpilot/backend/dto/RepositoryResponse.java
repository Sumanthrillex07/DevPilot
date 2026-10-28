package com.devpilot.backend.dto;

import com.devpilot.backend.entities.IndexStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a {@link com.devpilot.backend.entities.ConnectedRepository} sent to the frontend.
 *
 * <p>Includes both the metadata fields (name, language, etc.) and the current
 * indexing state (status, progress counters, error message). This way the SPA
 * can show a complete card — name, description, index status — with a single response.
 *
 * <p><b>Why {@code @JsonProperty("isPrivate")}?</b><br>
 * Java records with a boolean component named {@code isPrivate} generate a getter called
 * {@code isPrivate()} (not {@code private()}, which is a keyword). Jackson would normally
 * serialise this as {@code "private"} because it strips the "is" prefix from boolean
 * getters. The annotation forces the JSON key to be {@code "isPrivate"} so the frontend
 * doesn't have to deal with a confusingly named field.
 */
public record RepositoryResponse(
        UUID id,
        Long githubRepoId,
        String owner,
        String name,
        String fullName,
        @JsonProperty("isPrivate") boolean isPrivate,
        String defaultBranch,
        String language,
        String htmlUrl,
        String description,
        IndexStatus indexStatus,
        Instant indexedAt,
        int chunkCount,
        int filesTotal,
        int filesProcessed,
        String errorMessage
) {}
