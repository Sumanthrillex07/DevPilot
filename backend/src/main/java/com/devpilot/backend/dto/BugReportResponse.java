package com.devpilot.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Structured diagnostic response produced by the Bug Investigation Agent.
 */
public record BugReportResponse(
        UUID repositoryId,
        String summary,
        String rootCause,
        List<String> suspectedFiles,
        List<String> reproductionSteps,
        String suggestedFix,
        List<CitationDto> citations,
        Instant generatedAt
) {
}
