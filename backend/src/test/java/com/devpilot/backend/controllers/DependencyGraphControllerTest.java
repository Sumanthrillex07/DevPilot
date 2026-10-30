package com.devpilot.backend.controllers;

import com.devpilot.backend.config.CorsConfig;
import com.devpilot.backend.config.CryptoConfig;
import com.devpilot.backend.config.SecurityConfig;
import com.devpilot.backend.dto.DependencyGraphResponse;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.security.GithubOAuth2UserService;
import com.devpilot.backend.services.graph.CodeRelation;
import com.devpilot.backend.services.graph.DependencyGraphService;
import com.devpilot.backend.services.graph.RelationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DependencyGraphController.class)
@Import({SecurityConfig.class, CorsConfig.class, CryptoConfig.class})
@ActiveProfiles("test")
class DependencyGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DependencyGraphService dependencyGraphService;

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
    @DisplayName("GET /api/v1/repos/{id}/graph returns 401 when unauthenticated")
    void getGraph_returns401_whenUnauthenticated() throws Exception {
        UUID repoId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/repos/" + repoId + "/graph"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/repos/{id}/graph returns 200 with graph data when authenticated")
    void getGraph_returns200_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);

        DependencyGraphResponse response = new DependencyGraphResponse(
                repoId,
                2,
                1,
                Set.of("src/App.java", "src/Util.java"),
                List.of(new CodeRelation("src/App.java", "src/Util.java", RelationType.IMPORTS, 5))
        );

        when(dependencyGraphService.getGraphResponse(userId, repoId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/repos/" + repoId + "/graph")
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value(repoId.toString()))
                .andExpect(jsonPath("$.totalNodes").value(2))
                .andExpect(jsonPath("$.totalEdges").value(1))
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.edges[0].source").value("src/App.java"))
                .andExpect(jsonPath("$.edges[0].target").value("src/Util.java"))
                .andExpect(jsonPath("$.edges[0].type").value("IMPORTS"));
    }
}
