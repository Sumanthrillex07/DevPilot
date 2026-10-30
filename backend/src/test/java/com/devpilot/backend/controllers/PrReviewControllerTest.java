package com.devpilot.backend.controllers;

import com.devpilot.backend.config.CorsConfig;
import com.devpilot.backend.config.CryptoConfig;
import com.devpilot.backend.config.SecurityConfig;
import com.devpilot.backend.dto.PrReviewCommentDto;
import com.devpilot.backend.dto.PrReviewResponse;
import com.devpilot.backend.dto.ReviewDiffRequest;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.security.GithubOAuth2UserService;
import com.devpilot.backend.services.ai.PrReviewService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrReviewController.class)
@Import({SecurityConfig.class, CorsConfig.class, CryptoConfig.class})
@ActiveProfiles("test")
class PrReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PrReviewService prReviewService;

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
    @DisplayName("POST /api/v1/repos/{id}/reviews/diff returns 401 when unauthenticated")
    void reviewDiff_returns401_whenUnauthenticated() throws Exception {
        UUID repoId = UUID.randomUUID();
        ReviewDiffRequest request = new ReviewDiffRequest("Title", "Desc", "diff --git a b");

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/reviews/diff")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/repos/{id}/reviews/diff returns 200 with PrReviewResponse when authenticated")
    void reviewDiff_returns200_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);

        ReviewDiffRequest request = new ReviewDiffRequest("Title", "Desc", "diff --git a b");
        PrReviewResponse response = new PrReviewResponse(
                repoId,
                null,
                PrReviewResponse.ReviewVerdict.APPROVED,
                10,
                "Looks clean and well structured.",
                List.of("src/App.java"),
                List.of(),
                List.of(),
                Instant.now()
        );

        when(prReviewService.reviewDiff(eq(userId), eq(repoId), any(ReviewDiffRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/reviews/diff")
                        .with(oauth2Login().oauth2User(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value(repoId.toString()))
                .andExpect(jsonPath("$.verdict").value("APPROVED"))
                .andExpect(jsonPath("$.riskScore").value(10))
                .andExpect(jsonPath("$.summary").value("Looks clean and well structured."));
    }

    @Test
    @DisplayName("POST /api/v1/repos/{id}/reviews/github/{prNumber} returns 200 with review response")
    void reviewGitHubPR_returns200_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);

        PrReviewResponse response = new PrReviewResponse(
                repoId,
                15,
                PrReviewResponse.ReviewVerdict.COMMENT,
                30,
                "Minor formatting recommendations.",
                List.of("src/Config.java"),
                List.of(new PrReviewCommentDto(
                        "src/Config.java", 10,
                        PrReviewCommentDto.CommentSeverity.SUGGESTION,
                        PrReviewCommentDto.CommentCategory.CODE_STYLE,
                        "Use final modifier",
                        "private final int port;"
                )),
                List.of(),
                Instant.now()
        );

        when(prReviewService.reviewGitHubPullRequest(userId, repoId, 15)).thenReturn(response);

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/reviews/github/15")
                        .with(oauth2Login().oauth2User(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestNumber").value(15))
                .andExpect(jsonPath("$.verdict").value("COMMENT"))
                .andExpect(jsonPath("$.comments[0].filePath").value("src/Config.java"))
                .andExpect(jsonPath("$.comments[0].severity").value("SUGGESTION"));
    }
}
