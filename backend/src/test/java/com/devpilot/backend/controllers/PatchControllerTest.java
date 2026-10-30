package com.devpilot.backend.controllers;

import com.devpilot.backend.config.CorsConfig;
import com.devpilot.backend.config.CryptoConfig;
import com.devpilot.backend.config.SecurityConfig;
import com.devpilot.backend.dto.*;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.security.GithubOAuth2UserService;
import com.devpilot.backend.services.ai.PatchGenerationService;
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

@WebMvcTest(PatchController.class)
@Import({SecurityConfig.class, CorsConfig.class, CryptoConfig.class})
@ActiveProfiles("test")
class PatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PatchGenerationService patchService;

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
    @DisplayName("POST /generate returns 401 when unauthenticated")
    void generatePatch_unauthenticated() throws Exception {
        UUID repoId = UUID.randomUUID();
        GeneratePatchRequest request = new GeneratePatchRequest("Fix bug");

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/patches/generate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /generate returns 200 with GeneratedPatchResponse when authenticated")
    void generatePatch_authenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        GeneratePatchRequest request = new GeneratePatchRequest("Fix NPE", "Calculator.java", null, List.of());
        PatchValidationResult validation = PatchValidationResult.success(List.of(), 1, 1, 1, true);

        GeneratedPatchResponse response = new GeneratedPatchResponse(
                UUID.randomUUID(),
                "Fix NPE",
                "Patched null check",
                "diff --git a/Calculator.java b/Calculator.java",
                List.of("Calculator.java"),
                validation,
                List.of(new CitationDto("Calculator.java", 1, 10, "java")),
                Instant.now()
        );

        when(patchService.generatePatch(eq(repoId), any(GeneratePatchRequest.class), eq(userId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/patches/generate")
                        .with(csrf())
                        .with(oauth2Login().oauth2User(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction").value("Fix NPE"))
                .andExpect(jsonPath("$.explanation").value("Patched null check"))
                .andExpect(jsonPath("$.affectedFiles[0]").value("Calculator.java"))
                .andExpect(jsonPath("$.validation.valid").value(true));
    }

    @Test
    @DisplayName("POST /validate returns 200 with PatchValidationResult")
    void validatePatch_authenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        ValidatePatchRequest request = new ValidatePatchRequest("diff --git a b", "test.txt", "content");
        PatchValidationResult validation = PatchValidationResult.success(List.of(), 1, 1, 0, true);

        when(patchService.validatePatch(eq(repoId), any(ValidatePatchRequest.class), eq(userId)))
                .thenReturn(validation);

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/patches/validate")
                        .with(csrf())
                        .with(oauth2Login().oauth2User(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.dryRunSuccess").value(true));
    }

    @Test
    @DisplayName("POST /dry-run returns 200 with DryRunPatchResponse")
    void dryRunPatch_authenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        DryRunPatchRequest request = new DryRunPatchRequest("diff --git a b", "line 1");
        DryRunPatchResponse response = DryRunPatchResponse.success("line 1 modified", 1, 1);

        when(patchService.dryRunPatch(eq(repoId), any(DryRunPatchRequest.class), eq(userId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/repos/" + repoId + "/patches/dry-run")
                        .with(csrf())
                        .with(oauth2Login().oauth2User(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.patchedContent").value("line 1 modified"));
    }
}
