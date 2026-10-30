package com.devpilot.backend.controllers;

import com.devpilot.backend.dto.BenchmarkSuiteResult;
import com.devpilot.backend.dto.EvaluationRequest;
import com.devpilot.backend.dto.EvaluationResult;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.services.eval.EvaluationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for RAG retrieval quality evaluations and repository benchmark suites.
 */
@RestController
@RequestMapping("/api/v1/repos/{repoId}/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * Evaluates a single RAG chat or agent interaction for relevance, groundedness, and citations.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<EvaluationResult> evaluateInteraction(
            @PathVariable UUID repoId,
            @Valid @RequestBody EvaluationRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        EvaluationResult result = evaluationService.evaluateInteraction(repoId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    /**
     * Executes the automated RAG benchmark suite against the indexed repository in pgvector.
     */
    @GetMapping("/benchmark")
    public ResponseEntity<BenchmarkSuiteResult> runBenchmark(
            @PathVariable UUID repoId,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        BenchmarkSuiteResult result = evaluationService.runBenchmarkSuite(repoId, principal.getId());
        return ResponseEntity.ok(result);
    }
}
