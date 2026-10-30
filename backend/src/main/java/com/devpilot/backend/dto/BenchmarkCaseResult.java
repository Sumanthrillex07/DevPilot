package com.devpilot.backend.dto;

/**
 * Result of a single automated RAG benchmark query case.
 */
public record BenchmarkCaseResult(
        String caseName,
        String query,
        int retrievedCitationsCount,
        long latencyMs,
        double contextRelevanceScore,
        double citationValidityScore,
        boolean passed
) {}
