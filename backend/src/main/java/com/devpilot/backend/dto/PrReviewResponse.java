package com.devpilot.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Structured Pull Request review response.
 */
public record PrReviewResponse(
        UUID repositoryId,
        Integer pullRequestNumber,
        ReviewVerdict verdict,
        int riskScore,
        String summary,
        List<String> examinedFiles,
        List<PrReviewCommentDto> comments,
        List<CitationDto> citations,
        Instant reviewedAt
) {
    public enum ReviewVerdict {
        APPROVED,
        COMMENT,
        REQUEST_CHANGES
    }
}
