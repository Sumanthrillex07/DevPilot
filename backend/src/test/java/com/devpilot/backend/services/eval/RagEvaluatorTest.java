package com.devpilot.backend.services.eval;

import com.devpilot.backend.dto.CitationDto;
import com.devpilot.backend.dto.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagEvaluatorTest {

    private final RagEvaluator evaluator = new RagEvaluator();

    @Test
    @DisplayName("Computes high context relevance when query tokens match code context")
    void computeContextRelevance_high() {
        String query = "GitHub authentication and user login";
        String context = "public class AuthController { public void githubLogin(User user) { ... } }";

        double relevance = evaluator.computeContextRelevance(query, context);

        assertThat(relevance).isGreaterThan(0.6);
    }

    @Test
    @DisplayName("Returns 0.0 relevance when context has no matching chunks")
    void computeContextRelevance_noMatches() {
        double relevance = evaluator.computeContextRelevance("authentication", "(no matching code chunks found)");
        assertThat(relevance).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Detects hallucinations when answer invents symbols absent from context")
    void computeGroundedness_detectsHallucinations() {
        String answer = "Use the `PaymentGatewayService` and `StripeClient` to process invoices.";
        String context = "public class UserService { public User findById(UUID id) {} }";
        List<String> hallucinations = new ArrayList<>();

        double groundedness = evaluator.computeGroundedness(answer, context, hallucinations);

        assertThat(groundedness).isLessThan(1.0);
        assertThat(hallucinations).hasSize(2);
        assertThat(hallucinations).anyMatch(h -> h.contains("PaymentGatewayService"));
        assertThat(hallucinations).anyMatch(h -> h.contains("StripeClient"));
    }

    @Test
    @DisplayName("Computes high citation validity when file paths and lines are properly formed")
    void computeCitationValidity_valid() {
        String context = "file: src/main/java/com/devpilot/UserService.java\npublic class UserService {}";
        List<CitationDto> citations = List.of(
                new CitationDto("src/main/java/com/devpilot/UserService.java", 1, 50, "java")
        );

        double validity = evaluator.computeCitationValidity(citations, context);

        assertThat(validity).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Performs full composite evaluation with recommendations")
    void evaluate_composite() {
        String query = "Find the User entity";
        String context = "public class User { private UUID id; private String username; } // User entity";
        String answer = "The `User` entity contains `id` and `username`.";
        List<CitationDto> citations = List.of(
                new CitationDto("User.java", 1, 10, "java")
        );

        EvaluationResult result = evaluator.evaluate(query, answer, context, citations);

        assertThat(result.overallScore()).isGreaterThan(0.8);
        assertThat(result.detectedHallucinations()).isEmpty();
        assertThat(result.recommendations()).isNotEmpty();
    }
}
