package com.devpilot.backend.services.eval;

import com.devpilot.backend.dto.*;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.observability.DevPilotMetrics;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.ai.CodeContextRetriever;
import com.devpilot.backend.services.ai.RetrievedContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private ConnectedRepositoryRepository repositoryRepository;

    @Mock
    private CodeContextRetriever contextRetriever;

    private final RagEvaluator ragEvaluator = new RagEvaluator();
    private DevPilotMetrics metrics;
    private EvaluationService evaluationService;

    private UUID userId;
    private UUID repoId;
    private ConnectedRepository repo;

    @BeforeEach
    void setUp() {
        metrics = new DevPilotMetrics(new SimpleMeterRegistry());
        evaluationService = new EvaluationService(
                repositoryRepository, contextRetriever, ragEvaluator, metrics
        );

        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();
        repo = ConnectedRepository.builder()
                .id(repoId)
                .userId(userId)
                .owner("octocat")
                .name("hello-world")
                .fullName("octocat/hello-world")
                .defaultBranch("main")
                .build();
    }

    @Test
    @DisplayName("Evaluates single interaction successfully for repository owner")
    void evaluateInteraction_success() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        EvaluationRequest request = new EvaluationRequest(
                "How does login work?",
                "The `login()` method authenticates using credentials.",
                "public void login() { authenticate(); }",
                List.of(new CitationDto("Auth.java", 1, 10, "java"))
        );

        EvaluationResult result = evaluationService.evaluateInteraction(repoId, request, userId);

        assertThat(result).isNotNull();
        assertThat(result.overallScore()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Throws UnauthorizedException when requesting user does not own repo")
    void evaluateInteraction_unauthorized() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));
        UUID otherUser = UUID.randomUUID();

        EvaluationRequest request = new EvaluationRequest("query", "answer");
        assertThatThrownBy(() -> evaluationService.evaluateInteraction(repoId, request, otherUser))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Throws NotFoundException when repository is not found")
    void evaluateInteraction_notFound() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.empty());

        EvaluationRequest request = new EvaluationRequest("query", "answer");
        assertThatThrownBy(() -> evaluationService.evaluateInteraction(repoId, request, userId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Runs automated RAG benchmark suite across repository")
    void runBenchmarkSuite_success() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        RetrievedContext context = new RetrievedContext(
                List.of(new CitationDto("SecurityConfig.java", 1, 20, "java")),
                "public class SecurityConfig { SecurityFilterChain filterChain() {} }"
        );
        when(contextRetriever.retrieve(any(UUID.class), anyString())).thenReturn(context);

        BenchmarkSuiteResult suiteResult = evaluationService.runBenchmarkSuite(repoId, userId);

        assertThat(suiteResult).isNotNull();
        assertThat(suiteResult.totalCases()).isEqualTo(3);
        assertThat(suiteResult.cases()).hasSize(3);
        assertThat(suiteResult.overallBenchmarkScore()).isGreaterThan(0.0);
    }
}
