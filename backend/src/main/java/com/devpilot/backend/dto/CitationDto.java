package com.devpilot.backend.dto;

/**
 * Citation pointing to a specific file and line range in the repository.
 *
 * <p>Sent alongside AI assistant responses so the frontend can display interactive
 * citation chips (e.g. {@code UserService.java:42-88}) and navigate to GitHub file lines.
 */
public record CitationDto(
        String filePath,
        Integer startLine,
        Integer endLine,
        String language
) {}
