package com.devpilot.backend.controllers;

import com.devpilot.backend.config.CorsConfig;
import com.devpilot.backend.config.CryptoConfig;
import com.devpilot.backend.config.SecurityConfig;
import com.devpilot.backend.dto.ChatMessageRequest;
import com.devpilot.backend.dto.ChatMessageResponse;
import com.devpilot.backend.dto.ChatSessionResponse;
import com.devpilot.backend.dto.CitationDto;
import com.devpilot.backend.dto.CreateChatSessionRequest;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.security.GithubOAuth2UserService;
import com.devpilot.backend.services.ChatService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc WebMvcTest for {@link ChatController}.
 */
@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, CorsConfig.class, CryptoConfig.class})
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

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

    // ---- POST /api/v1/chat/sessions ----

    @Test
    @DisplayName("POST /api/v1/chat/sessions returns 401 when unauthenticated")
    void createSession_returns401_whenUnauthenticated() throws Exception {
        CreateChatSessionRequest request = new CreateChatSessionRequest(UUID.randomUUID(), "Refactor discussion");

        mockMvc.perform(post("/api/v1/chat/sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/chat/sessions returns 200 with new session response when authenticated")
    void createSession_returns200_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);

        CreateChatSessionRequest request = new CreateChatSessionRequest(repoId, "Architecture discussion");
        ChatSessionResponse response = new ChatSessionResponse(
                sessionId, userId, repoId, "Architecture discussion", Instant.now()
        );

        when(chatService.createSession(eq(userId), any(CreateChatSessionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/chat/sessions")
                        .with(oauth2Login().oauth2User(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.repositoryId").value(repoId.toString()))
                .andExpect(jsonPath("$.title").value("Architecture discussion"));
    }

    // ---- GET /api/v1/chat/sessions ----

    @Test
    @DisplayName("GET /api/v1/chat/sessions returns 401 when unauthenticated")
    void listSessions_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/chat/sessions").param("repositoryId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/chat/sessions returns 200 with session list when authenticated")
    void listSessions_returns200_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);

        ChatSessionResponse session1 = new ChatSessionResponse(
                UUID.randomUUID(), userId, repoId, "Session 1", Instant.now()
        );
        ChatSessionResponse session2 = new ChatSessionResponse(
                UUID.randomUUID(), userId, repoId, "Session 2", Instant.now()
        );

        when(chatService.listSessions(userId, repoId)).thenReturn(List.of(session1, session2));

        mockMvc.perform(get("/api/v1/chat/sessions")
                        .param("repositoryId", repoId.toString())
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Session 1"))
                .andExpect(jsonPath("$[1].title").value("Session 2"));
    }

    // ---- GET /api/v1/chat/sessions/{id} ----

    @Test
    @DisplayName("GET /api/v1/chat/sessions/{id} returns 401 when unauthenticated")
    void getMessages_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/chat/sessions/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/chat/sessions/{id} returns 200 with message list when authenticated")
    void getMessages_returns200_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);

        ChatMessageResponse msg1 = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, com.devpilot.backend.entities.MessageRole.USER, "How does auth work?",
                null, Instant.now()
        );
        ChatMessageResponse msg2 = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, com.devpilot.backend.entities.MessageRole.ASSISTANT, "Auth uses OAuth2 tokens.",
                List.of(new CitationDto("SecurityConfig.java", 15, 30, "Java")),
                Instant.now()
        );

        when(chatService.getMessages(userId, sessionId)).thenReturn(List.of(msg1, msg2));

        mockMvc.perform(get("/api/v1/chat/sessions/" + sessionId)
                        .with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].citations[0].filePath").value("SecurityConfig.java"));
    }

    // ---- POST /api/v1/chat/sessions/{id}/messages ----

    @Test
    @DisplayName("POST /api/v1/chat/sessions/{id}/messages returns 401 when unauthenticated")
    void sendMessage_returns401_whenUnauthenticated() throws Exception {
        ChatMessageRequest request = new ChatMessageRequest("Hello");

        mockMvc.perform(post("/api/v1/chat/sessions/" + UUID.randomUUID() + "/messages")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/chat/sessions/{id}/messages returns 200 text/event-stream when authenticated")
    void sendMessage_returns200_eventStream_whenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AppUserPrincipal principal = stubPrincipal(userId);

        when(currentUser.require()).thenReturn(principal);

        SseEmitter emitter = new SseEmitter();
        when(chatService.streamReply(eq(userId), eq(sessionId), eq("Where is the user principal?")))
                .thenReturn(emitter);

        ChatMessageRequest request = new ChatMessageRequest("Where is the user principal?");

        mockMvc.perform(post("/api/v1/chat/sessions/" + sessionId + "/messages")
                        .with(oauth2Login().oauth2User(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        emitter.complete();
    }
}
