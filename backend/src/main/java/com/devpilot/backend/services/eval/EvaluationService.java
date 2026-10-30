package com.devpilot.backend.services.eval;

import com.devpilot.backend.dto.*;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.observability.DevPilotMetrics;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.ai.CodeContextRetriever;
import com.devpilot.backend.services.ai.RetrievedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating RAG quality evaluations and automated repository benchmark suites.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final ConnectedRepositoryRepository repositoryRepository;
    private final CodeContextRetriever contextRetriever;
    private final RagEvaluator ragEvaluator;
    private final DevPilotMetrics metrics;

    private static final List<BenchmarkCaseDef> BENCHMARK_CASES = List.of(
            new BenchmarkCaseDef("Authentication & Security", "Find user authentication, login endpoints, and security configuration"),
            new BenchmarkCaseDef("Persistence & Entities", "Locate repository database entities and data persistence models"),
            new BenchmarkCaseDef("Controllers & REST APIs", "Identify service layer business logic and API controllers")
    );

    private record BenchmarkCaseDef(String name, String query) {}

    public EvaluationService(
            ConnectedRepositoryRepository repositoryRepository,
            CodeContextRetriever contextRetriever,
            RagEvaluator ragEvaluator,
            DevPilotMetrics metrics
    ) {
        this.repositoryRepository = repositoryRepository;
        this.contextRetriever = contextRetriever;
        this.ragEvaluator = ragEvaluator;
        this.metrics = metrics;
    }

    /**
     * Evaluates a single RAG chat or agent response against the retrieved context.
     */
    public EvaluationResult evaluateInteraction(UUID repoId, EvaluationRequest request, UUID userId) {
        verifyRepositoryAccess(repoId, userId);
        return ragEvaluator.evaluate(
                request.query(),
                request.answer(),
                request.retrievedContext(),
                request.citations()
        );
    }

    /**
     * Runs an automated benchmark evaluation suite against the indexed repository in pgvector.
     */
    public BenchmarkSuiteResult runBenchmarkSuite(UUID repoId, UUID userId) {
        verifyRepositoryAccess(repoId, userId);

        List<BenchmarkCaseResult> caseResults = new ArrayList<>();
        long totalLatency = 0;
        double totalRelevance = 0;
        int passedCount = 0;

        for (BenchmarkCaseDef def : BENCHMARK_CASES) {
            long start = System.currentTimeMillis();
            RetrievedContext context = contextRetriever.retrieve(repoId, def.query());
            long latency = System.currentTimeMillis() - start;
            totalLatency += latency;

            boolean hasMatches = !context.citations().isEmpty() &&
                    !context.contextText().equals("(no matching code chunks found)");

            metrics.recordRagRetrieval(latency, context.citations().size(), hasMatches);

            double relevance = ragEvaluator.computeContextRelevance(def.query(), context.contextText());
            double citationValidity = ragEvaluator.computeCitationValidity(context.citations(), context.contextText());
            boolean passed = hasMatches && relevance > 0.1;

            if (passed) {
                passedCount++;
            }
            totalRelevance += relevance;

            caseResults.add(new BenchmarkCaseResult(
                    def.name(),
                    def.query(),
                    context.citations().size(),
                    latency,
                    Math.round(relevance * 100.0) / 100.0,
                    Math.round(citationValidity * 100.0) / 100.0,
                    passed
            ));
        }

        double avgLatency = (double) totalLatency / BENCHMARK_CASES.size();
        double avgRelevance = totalRelevance / BENCHMARK_CASES.size();
        double overallScore = (double) passedCount / BENCHMARK_CASES.size();

        log.info("Completed RAG benchmark suite for repo {}: passed {}/{}, avgLatency={}ms",
                repoId, passedCount, BENCHMARK_CASES.size(), avgLatency);

        return new BenchmarkSuiteResult(
                repoId,
                BENCHMARK_CASES.size(),
                passedCount,
                Math.round(avgLatency * 10.0) / 10.0,
                Math.round(avgRelevance * 100.0) / 100.0,
                Math.round(overallScore * 100.0) / 100.0,
                caseResults,
                Instant.now()
        );
    }

    private ConnectedRepository verifyRepositoryAccess(UUID repoId, UUID userId) {
        ConnectedRepository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + repoId));
        if (!repo.getUserId().equals(userId)) {
            throw new UnauthorizedException("You do not have access to repository " + repoId);
        }
        return repo;
    }
}
