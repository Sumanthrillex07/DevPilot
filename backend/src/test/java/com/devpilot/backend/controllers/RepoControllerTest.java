package com.devpilot.backend.controllers;

import com.devpilot.backend.dto.IndexStatusResponse;
import com.devpilot.backend.dto.RepositoryResponse;
import com.devpilot.backend.entities.IndexStatus;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.security.GithubOAuth2UserService;
import com.devpilot.backend.services.RepoService;
import com.devpilot.backend.entities.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.devpilot.backend.config.CorsConfig;
import com.devpilot.backend.config.CryptoConfig;
import com.devpilot.backend.config.SecurityConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link RepoController}.
 *
 * <p>We use {@code @WebMvcTest} for speed — no JPA, no DataSource, no GitHub calls.
 * {@link RepoService} and {@link CurrentUser} are mocked so we can test:
 * <ul>
 *   <li>Security: unauthenticated requests return 401</li>
 *   <li>Routing: correct endpoint, path variable, query param wiring</li>
 *   <li>Response shape: JSON fields are correctly serialised</li>
 * </ul>
 */
@WebMvcTest(RepoController.class)
@Import({SecurityConfig.class, CorsConfig.class, CryptoConfig.class})
@ActiveProfiles("test")
class RepoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepoService repoService;

    @MockBean
    private CurrentUser currentUser;

    @MockBean
    private GithubOAuth2UserService githubOAuth2UserService;

    // ---- helpers ----

    private static AppUserPrincipal stubPrincipal(UUID userId) {
        User u = new User();
        u.setId(userId);
        u.setGithubUsername("octocat");
        return new AppUserPrincipal(u, Map.of("login", "octocat"));
    }

    private static RepositoryResponse stubRepoResponse(UUID id) {
        return new RepositoryResponse(
                id, 1001L, "octocat", "my-repo", "octocat/my-repo",
                false, "main", "Java",
                "https://github.com/octocat/my-repo",
                "A test repo",
                IndexStatus.PENDING, null, 0, 0, 0, null
        );
    }

    // ---- GET /api/v1/repos ----

    @Test
    @DisplayName("GET /api/v1/repos returns 401 when not authenticated")
    void listRepos_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/repos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/repos returns 200 with repo list when authenticated (refresh=true)")
    void listRepos_returns200_withRepoList_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);
        when(repoService.syncAndListRepos(userId)).thenReturn(List.of(stubRepoResponse(repoId)));

        mockMvc.perform(get("/api/v1/repos")
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("octocat/my-repo"))
                .andExpect(jsonPath("$[0].indexStatus").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/repos?refresh=false returns stored repos without GitHub call")
    void listRepos_refresh_false_callsListStored() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);
        when(repoService.listStored(userId)).thenReturn(List.of(stubRepoResponse(repoId)));

        mockMvc.perform(get("/api/v1/repos?refresh=false")
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].owner").value("octocat"));
    }

    // ---- GET /api/v1/repos/{id} ----

    @Test
    @DisplayName("GET /api/v1/repos/{id} returns 401 when not authenticated")
    void getRepo_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/repos/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/repos/{id} returns 200 with single repo when authenticated")
    void getRepo_returns200_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);
        when(repoService.requireOwned(repoId, userId))
                .thenReturn(new com.devpilot.backend.entities.ConnectedRepository());
        when(repoService.toResponse(any())).thenReturn(stubRepoResponse(repoId));

        mockMvc.perform(get("/api/v1/repos/" + repoId)
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(repoId.toString()));
    }

    // ---- GET /api/v1/repos/{id}/status ----

    @Test
    @DisplayName("GET /api/v1/repos/{id}/status returns 401 when not authenticated")
    void status_returns401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/repos/" + UUID.randomUUID() + "/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/repos/{id}/status returns indexing progress when authenticated")
    void status_returnsProgress_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        IndexStatusResponse statusResponse = new IndexStatusResponse(
                repoId, IndexStatus.INDEXING, 100, 45, 0, null, null);

        when(currentUser.require()).thenReturn(principal);
        when(repoService.status(repoId, userId)).thenReturn(statusResponse);

        mockMvc.perform(get("/api/v1/repos/" + repoId + "/status")
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexStatus").value("INDEXING"))
                .andExpect(jsonPath("$.filesTotal").value(100))
                .andExpect(jsonPath("$.filesProcessed").value(45));
    }
}
