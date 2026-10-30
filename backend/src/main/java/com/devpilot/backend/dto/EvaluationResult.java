package com.devpilot.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Detailed evaluation report assessing context relevance, answer groundedness,
 * citation validity, and hallucination detection.
 */
public record EvaluationResult(
        double contextRelevanceScore,
        double groundednessScore,
        double citationValidityScore,
        double overallScore,
        List<String> detectedHallucinations,
        List<String> recommendations,
        Instant evaluatedAt
) {}
