package com.devpilot.backend.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request payload for evaluating the quality, relevance, and groundedness of a RAG interaction.
 */
public record EvaluationRequest(
        @NotBlank(message = "Query cannot be blank")
        String query,
        @NotBlank(message = "Answer cannot be blank")
        String answer,
        String retrievedContext,
        List<CitationDto> citations
) {
    public EvaluationRequest(String query, String answer) {
        this(query, answer, null, List.of());
    }
}
