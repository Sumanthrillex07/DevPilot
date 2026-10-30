package com.devpilot.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated benchmark results for a repository across standard RAG test cases.
 */
public record BenchmarkSuiteResult(
        UUID repositoryId,
        int totalCases,
        int passedCases,
        double averageLatencyMs,
        double averageRelevanceScore,
        double overallBenchmarkScore,
        List<BenchmarkCaseResult> cases,
        Instant executedAt
) {}
