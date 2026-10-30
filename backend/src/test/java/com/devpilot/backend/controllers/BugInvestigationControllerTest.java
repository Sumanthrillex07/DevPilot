package com.devpilot.backend.controllers;

import com.devpilot.backend.config.CorsConfig;
import com.devpilot.backend.config.CryptoConfig;
import com.devpilot.backend.config.SecurityConfig;
import com.devpilot.backend.dto.BugReportResponse;
import com.devpilot.backend.dto.CitationDto;
import com.devpilot.backend.dto.InvestigateBugRequest;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.security.GithubOAuth2UserService;
import com.devpilot.backend.services.ai.BugInvestigationService;
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

@WebMvcTest(BugInvestigationController.class)
@Import({SecurityConfig.class, CorsConfig.class, CryptoConfig.class})
@ActiveProfiles("test")
class BugInvestigationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BugInvestigationService bugInvestigationService;

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
    @DisplayName("POST /api/v1/repos/{id}/investigate returns 401 when unauthenticated")
    void investigate_returns401_whenUnauthenticated() throws Exception {
        UUID repoId = UUID.randomUUID();
        InvestigateBugRequest request = new InvestigateBugRequest("NPE error", null, null, null);

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/investigate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/repos/{id}/investigate returns 200 with BugReportResponse when authenticated")
    void investigate_returns200_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);

        InvestigateBugRequest request = new InvestigateBugRequest(
                "NPE error", "NullPointerException at line 10", null, null
        );

        BugReportResponse response = new BugReportResponse(
                repoId,
                "Token null in auth",
                "Line 10 does not check null",
                List.of("src/Auth.java"),
                List.of("Call login with null"),
                "Add null check",
                List.of(new CitationDto("src/Auth.java", 10, 20, "Java")),
                Instant.now()
        );

        when(bugInvestigationService.investigate(eq(userId), eq(repoId), any(InvestigateBugRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/investigate")
                        .with(oauth2Login().oauth2User(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value(repoId.toString()))
                .andExpect(jsonPath("$.summary").value("Token null in auth"))
                .andExpect(jsonPath("$.rootCause").value("Line 10 does not check null"))
                .andExpect(jsonPath("$.suspectedFiles[0]").value("src/Auth.java"))
                .andExpect(jsonPath("$.citations[0].filePath").value("src/Auth.java"));
    }
}
