package com.devpilot.backend.services;

import com.devpilot.backend.dto.ChatMessageResponse;
import com.devpilot.backend.dto.ChatSessionResponse;
import com.devpilot.backend.dto.CreateChatSessionRequest;
import com.devpilot.backend.entities.ChatMessage;
import com.devpilot.backend.entities.ChatSession;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.MessageRole;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.repositories.ChatMessageRepository;
import com.devpilot.backend.repositories.ChatSessionRepository;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatPersistenceServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ConnectedRepositoryRepository connectedRepositoryRepository;

    @InjectMocks
    private ChatPersistenceService chatPersistenceService;

    private UUID userId;
    private UUID repoId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
    }

    @Test
    @DisplayName("createSession succeeds when repository exists and belongs to user")
    void testCreateSessionSuccess() {
        ConnectedRepository repo = ConnectedRepository.builder()
                .id(repoId)
                .userId(userId)
                .fullName("user/repo")
                .build();

        when(connectedRepositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            s.setId(sessionId);
            s.setCreatedAt(Instant.now());
            return s;
        });

        CreateChatSessionRequest request = new CreateChatSessionRequest(repoId, "Bug Investigation");
        ChatSessionResponse response = chatPersistenceService.createSession(userId, request);

        assertThat(response.id()).isEqualTo(sessionId);
        assertThat(response.title()).isEqualTo("Bug Investigation");
        assertThat(response.repositoryId()).isEqualTo(repoId);
        verify(chatSessionRepository).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("createSession throws NotFoundException when repository is not owned by user")
    void testCreateSessionRepoNotOwned() {
        when(connectedRepositoryRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.empty());

        CreateChatSessionRequest request = new CreateChatSessionRequest(repoId, "Test");
        assertThatThrownBy(() -> chatPersistenceService.createSession(userId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Repository not found with ID");

        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("listSessions returns mapped response records")
    void testListSessions() {
        ChatSession s1 = ChatSession.builder().id(UUID.randomUUID()).userId(userId).repositoryId(repoId).title("S1").createdAt(Instant.now()).build();
        ChatSession s2 = ChatSession.builder().id(UUID.randomUUID()).userId(userId).repositoryId(repoId).title("S2").createdAt(Instant.now()).build();

        when(chatSessionRepository.findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repoId))
                .thenReturn(List.of(s1, s2));

        List<ChatSessionResponse> responses = chatPersistenceService.listSessions(userId, repoId);
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).title()).isEqualTo("S1");
        assertThat(responses.get(1).title()).isEqualTo("S2");
    }

    @Test
    @DisplayName("saveMessage saves user message when session exists")
    void testSaveMessageSuccess() {
        when(chatSessionRepository.existsById(sessionId)).thenReturn(true);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(Instant.now());
            return msg;
        });

        ChatMessageResponse response = chatPersistenceService.saveMessage(sessionId, MessageRole.USER, "Explain main()", null);

        assertThat(response.content()).isEqualTo("Explain main()");
        assertThat(response.role()).isEqualTo(MessageRole.USER);
        assertThat(response.sessionId()).isEqualTo(sessionId);
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("saveMessage throws NotFoundException when session does not exist")
    void testSaveMessageSessionNotFound() {
        when(chatSessionRepository.existsById(sessionId)).thenReturn(false);

        assertThatThrownBy(() -> chatPersistenceService.saveMessage(sessionId, MessageRole.USER, "Hello", null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Chat session not found with ID");

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("listMessages checks session ownership before returning messages")
    void testListMessagesOwnershipCheck() {
        ChatSession session = ChatSession.builder().id(sessionId).userId(userId).build();
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        ChatMessage m1 = ChatMessage.builder().id(UUID.randomUUID()).sessionId(sessionId).role(MessageRole.USER).content("Hi").createdAt(Instant.now()).build();
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(m1));

        List<ChatMessageResponse> messages = chatPersistenceService.listMessages(sessionId, userId);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).content()).isEqualTo("Hi");
    }

    @Test
    @DisplayName("listMessages throws NotFoundException when user does not own session")
    void testListMessagesUnauthorized() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatPersistenceService.listMessages(sessionId, userId))
                .isInstanceOf(NotFoundException.class);

        verify(chatMessageRepository, never()).findBySessionIdOrderByCreatedAtAsc(any());
    }

    @Test
    @DisplayName("deleteSession deletes session when owned")
    void testDeleteSession() {
        ChatSession session = ChatSession.builder().id(sessionId).userId(userId).build();
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        chatPersistenceService.deleteSession(sessionId, userId);
        verify(chatSessionRepository).delete(session);
    }
}
