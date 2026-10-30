package com.devpilot.backend.controllers;

import com.devpilot.backend.config.CorsConfig;
import com.devpilot.backend.config.CryptoConfig;
import com.devpilot.backend.config.SecurityConfig;
import com.devpilot.backend.dto.BenchmarkSuiteResult;
import com.devpilot.backend.dto.CitationDto;
import com.devpilot.backend.dto.EvaluationRequest;
import com.devpilot.backend.dto.EvaluationResult;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.security.GithubOAuth2UserService;
import com.devpilot.backend.services.eval.EvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvaluationController.class)
@Import({SecurityConfig.class, CorsConfig.class, CryptoConfig.class})
@ActiveProfiles("test")
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EvaluationService evaluationService;

    @MockBean
    private CurrentUser currentUser;

    @MockBean
    private GithubOAuth2UserService githubOAuth2UserService;

    private static AppUserPrincipal stubPrincipal(UUID userId) {
        User u = new User();
        u.setId(userId);
        u.setGithubUsername("octocat");
        return new AppUserPrincipal(u, Map.of("login", "octocat"));
    }

    @Test
    @DisplayName("POST /evaluate returns 401 when unauthenticated")
    void evaluate_unauthenticated() throws Exception {
        UUID repoId = UUID.randomUUID();
        EvaluationRequest request = new EvaluationRequest("query", "answer");

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/evaluation/evaluate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /evaluate returns 200 with EvaluationResult when authenticated")
    void evaluate_authenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        EvaluationRequest request = new EvaluationRequest(
                "query",
                "answer",
                "context",
                List.of(new CitationDto("File.java", 1, 5, "java"))
        );
        EvaluationResult result = new EvaluationResult(
                0.9,
                1.0,
                1.0,
                0.96,
                List.of(),
                List.of("Looks great"),
                Instant.now()
        );

        when(evaluationService.evaluateInteraction(eq(repoId), any(EvaluationRequest.class), eq(userId)))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/evaluation/evaluate")
                        .with(csrf())
                        .with(oauth2Login().oauth2User(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallScore").value(0.96))
                .andExpect(jsonPath("$.groundednessScore").value(1.0));
    }

    @Test
    @DisplayName("GET /benchmark returns 200 with BenchmarkSuiteResult when authenticated")
    void benchmark_authenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        BenchmarkSuiteResult suiteResult = new BenchmarkSuiteResult(
                repoId,
                3,
                3,
                120.5,
                0.88,
                1.0,
                List.of(),
                Instant.now()
        );

        when(evaluationService.runBenchmarkSuite(eq(repoId), eq(userId)))
                .thenReturn(suiteResult);

        mockMvc.perform(get("/api/v1/repos/" + repoId + "/evaluation/benchmark")
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").value(3))
                .andExpect(jsonPath("$.passedCases").value(3))
                .andExpect(jsonPath("$.overallBenchmarkScore").value(1.0));
    }
}
