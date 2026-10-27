package com.devpilot.backend.services;

import com.devpilot.backend.dto.ChatMessageResponse;
import com.devpilot.backend.dto.ChatSessionResponse;
import com.devpilot.backend.dto.CreateChatSessionRequest;
import com.devpilot.backend.entities.ChatMessage;
import com.devpilot.backend.entities.ChatSession;
import com.devpilot.backend.entities.MessageRole;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.repositories.ChatMessageRepository;
import com.devpilot.backend.repositories.ChatSessionRepository;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatPersistenceService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ConnectedRepositoryRepository connectedRepositoryRepository;

    @Transactional
    public ChatSessionResponse createSession(UUID userId, CreateChatSessionRequest request) {
        // Verify repository exists and belongs to user
        connectedRepositoryRepository.findByIdAndUserId(request.repositoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Repository not found with ID: " + request.repositoryId()));

        String title = (request.title() != null && !request.title().isBlank())
                ? request.title().trim()
                : "New Chat Session";

        ChatSession session = ChatSession.builder()
                .userId(userId)
                .repositoryId(request.repositoryId())
                .title(title)
                .build();

        ChatSession saved = chatSessionRepository.save(session);
        return ChatSessionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId, UUID repositoryId) {
        return chatSessionRepository.findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repositoryId)
                .stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatSession requireSession(UUID sessionId, UUID userId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Chat session not found with ID: " + sessionId));
    }

    @Transactional(readOnly = true)
    public ChatSessionResponse getSession(UUID sessionId, UUID userId) {
        return ChatSessionResponse.from(requireSession(sessionId, userId));
    }

    @Transactional
    public void deleteSession(UUID sessionId, UUID userId) {
        ChatSession session = requireSession(sessionId, userId);
        chatSessionRepository.delete(session);
    }

    @Transactional
    public ChatMessageResponse saveMessage(UUID sessionId, MessageRole role, String content, String citations) {
        // Ensure session exists
        if (!chatSessionRepository.existsById(sessionId)) {
            throw new NotFoundException("Chat session not found with ID: " + sessionId);
        }

        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .citations(citations)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        return ChatMessageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(UUID sessionId, UUID userId) {
        // Security check: verify session ownership
        requireSession(sessionId, userId);

        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }
}
