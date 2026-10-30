package com.devpilot.backend.dto;

/**
 * An inline code review comment pointing to a specific line in a changed file.
 */
public record PrReviewCommentDto(
        String filePath,
        int lineNumber,
        CommentSeverity severity,
        CommentCategory category,
        String comment,
        String suggestedChange
) {
    public enum CommentSeverity {
        CRITICAL,
        WARNING,
        INFO,
        SUGGESTION
    }

    public enum CommentCategory {
        SECURITY,
        BUG_RISK,
        PERFORMANCE,
        MAINTAINABILITY,
        CODE_STYLE
    }
}
