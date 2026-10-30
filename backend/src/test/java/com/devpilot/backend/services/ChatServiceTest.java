package com.devpilot.backend.services;

import com.devpilot.backend.dto.ChatMessageResponse;
import com.devpilot.backend.dto.ChatSessionResponse;
import com.devpilot.backend.dto.CitationDto;
import com.devpilot.backend.dto.CreateChatSessionRequest;
import com.devpilot.backend.entities.ChatMessage;
import com.devpilot.backend.entities.ChatSession;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.IndexStatus;
import com.devpilot.backend.entities.MessageRole;
import com.devpilot.backend.exceptions.BadRequestException;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.repositories.ChatMessageRepository;
import com.devpilot.backend.repositories.ChatSessionRepository;
import com.devpilot.backend.services.ai.ChatPromptBuilder;
import com.devpilot.backend.services.ai.ChatStreamHandler;
import com.devpilot.backend.services.ai.CitationMapper;
import com.devpilot.backend.services.ai.CodeContextRetriever;
import com.devpilot.backend.services.ai.RetrievedContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RepoService repoService;

    @Mock
    private CodeContextRetriever codeContextRetriever;

    @Mock
    private ChatPromptBuilder chatPromptBuilder;

    @Mock
    private ChatStreamHandler chatStreamHandler;

    @Mock
    private CitationMapper citationMapper;

    @InjectMocks
    private ChatService chatService;

    private UUID userId;
    private UUID repoId;
    private UUID sessionId;
    private ConnectedRepository readyRepo;
    private ChatSession testSession;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        readyRepo = ConnectedRepository.builder()
                .id(repoId)
                .userId(userId)
                .fullName("octocat/hello-world")
                .indexStatus(IndexStatus.READY)
                .build();

        testSession = ChatSession.builder()
                .id(sessionId)
                .userId(userId)
                .repositoryId(repoId)
                .title("My Chat")
                .createdAt(Instant.now())
                .build();
    }

    // --- createSession ---

    @Test
    @DisplayName("createSession: creates and returns new session when repository is READY")
    void createSession_whenRepoReady_success() {
        CreateChatSessionRequest request = new CreateChatSessionRequest(repoId, "Custom Title");
        when(repoService.requireOwned(repoId, userId)).thenReturn(readyRepo);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(i -> {
            ChatSession s = i.getArgument(0);
            s.setId(sessionId);
            s.setCreatedAt(Instant.now());
            return s;
        });

        ChatSessionResponse response = chatService.createSession(userId, request);

        assertThat(response.id()).isEqualTo(sessionId);
        assertThat(response.title()).isEqualTo("Custom Title");
        assertThat(response.repositoryId()).isEqualTo(repoId);
    }

    @Test
    @DisplayName("createSession: throws BadRequestException when repository is not READY")
    void createSession_whenRepoNotReady_throwsBadRequest() {
        readyRepo.setIndexStatus(IndexStatus.INDEXING);
        CreateChatSessionRequest request = new CreateChatSessionRequest(repoId, "Title");
        when(repoService.requireOwned(repoId, userId)).thenReturn(readyRepo);

        assertThatThrownBy(() -> chatService.createSession(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be indexed before chatting");
    }

    // --- listSessions & getMessages ---

    @Test
    @DisplayName("listSessions: returns ordered sessions for user and repository")
    void listSessions_returnsSessions() {
        when(chatSessionRepository.findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repoId))
                .thenReturn(List.of(testSession));

        List<ChatSessionResponse> responses = chatService.listSessions(userId, repoId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(sessionId);
        verify(repoService).requireOwned(repoId, userId);
    }

    @Test
    @DisplayName("getMessages: returns messages for valid owned session")
    void getMessages_returnsMessages() {
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .role(MessageRole.USER)
                .content("Hello")
                .createdAt(Instant.now())
                .build();

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(testSession));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(message));
        when(citationMapper.fromJson(any())).thenReturn(List.of());

        List<ChatMessageResponse> messages = chatService.getMessages(userId, sessionId);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).content()).isEqualTo("Hello");
    }

    // --- streamReply ---

    @Test
    @DisplayName("streamReply: persists message, retrieves context, builds prompts, and delegates to stream handler")
    void streamReply_orchestratesFullPipeline() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(testSession));
        when(repoService.requireOwned(repoId, userId)).thenReturn(readyRepo);

        ChatMessage savedUserMsg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .role(MessageRole.USER)
                .content("What is App.java?")
                .createdAt(Instant.now())
                .build();
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedUserMsg);

        CitationDto citation = new CitationDto("src/App.java", 1, 10, "java");
        RetrievedContext context = new RetrievedContext(List.of(citation), "code snippet");
        when(codeContextRetriever.retrieve(repoId, "What is App.java?")).thenReturn(context);

        when(chatPromptBuilder.systemPrompt("octocat/hello-world")).thenReturn("system-prompt");
        when(chatPromptBuilder.userPrompt("code snippet", "What is App.java?")).thenReturn("user-prompt");

        SseEmitter mockEmitter = new SseEmitter();
        when(chatStreamHandler.stream(eq(sessionId), any(), eq(List.of(citation)), eq("system-prompt"), eq("user-prompt")))
                .thenReturn(mockEmitter);

        SseEmitter result = chatService.streamReply(userId, sessionId, "What is App.java?");

        assertThat(result).isSameAs(mockEmitter);
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(codeContextRetriever).retrieve(repoId, "What is App.java?");
        verify(chatStreamHandler).stream(eq(sessionId), any(), eq(List.of(citation)), eq("system-prompt"), eq("user-prompt"));
    }
}
