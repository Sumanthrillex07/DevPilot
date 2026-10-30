package com.devpilot.backend.services;

import com.devpilot.backend.dto.ChatMessageResponse;
import com.devpilot.backend.dto.ChatSessionResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * High-level service orchestrating chat sessions, message persistence, and the RAG pipeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RepoService repoService;
    private final CodeContextRetriever codeContextRetriever;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatStreamHandler chatStreamHandler;
    private final CitationMapper citationMapper;

    /**
     * Creates a new chat session for a repository after verifying ownership and ready index status.
     */
    @Transactional
    public ChatSessionResponse createSession(UUID userId, CreateChatSessionRequest request) {
        ConnectedRepository repo = repoService.requireOwned(request.repositoryId(), userId);
        if (repo.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Repository must be indexed before chatting");
        }

        String title = request.title() != null && !request.title().isBlank()
                ? request.title().trim()
                : "Chat with " + repo.getFullName();

        ChatSession session = ChatSession.builder()
                .userId(userId)
                .repositoryId(repo.getId())
                .title(title)
                .build();

        session = chatSessionRepository.save(session);
        return toSessionResponse(session);
    }

    /**
     * Lists all chat sessions for a user and repository ordered by most recent first.
     */
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId, UUID repositoryId) {
        repoService.requireOwned(repositoryId, userId);
        return chatSessionRepository
                .findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repositoryId)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    /**
     * Fetches all messages for a session ordered chronologically.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID userId, UUID sessionId) {
        ChatSession session = requireSession(userId, sessionId);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    /**
     * Verifies that the session exists and belongs to the given user.
     */
    @Transactional(readOnly = true)
    public ChatSession requireSession(UUID userId, UUID sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Chat session not found"));
    }

    /**
     * Full RAG Pipeline:
     * 1. Validates session ownership & repository readiness
     * 2. Saves incoming user question
     * 3. Retrieves semantic code chunks from VectorStore
     * 4. Builds grounded prompts
     * 5. Streams reply tokens to client over SSE
     */
    public SseEmitter streamReply(UUID userId, UUID sessionId, String userContent) {
        ChatSession session = requireSession(userId, sessionId);
        ConnectedRepository repo = repoService.requireOwned(session.getRepositoryId(), userId);
        if (repo.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Repository is not ready for chat");
        }

        // Persist user question
        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(MessageRole.USER)
                .content(userContent)
                .build());

        // Retrieve similar code chunks
        var retrievedContext = codeContextRetriever.retrieve(repo.getId(), userContent);

        // Build system & user prompts
        String systemPrompt = chatPromptBuilder.systemPrompt(repo.getFullName());
        String userPrompt = chatPromptBuilder.userPrompt(retrievedContext.contextText(), userContent);

        // Stream reply via SSE
        return chatStreamHandler.stream(
                session.getId(),
                toMessageResponse(userMessage),
                retrievedContext.citations(),
                systemPrompt,
                userPrompt
        );
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return ChatSessionResponse.from(session);
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return ChatMessageResponse.from(message, citationMapper.fromJson(message.getCitations()));
    }
}
