package com.devpilot.backend.dto;

import com.devpilot.backend.entities.ChatMessage;
import com.devpilot.backend.entities.MessageRole;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * API response representing a single chat message with structured citations.
 */
public record ChatMessageResponse(
        UUID id,
        UUID sessionId,
        MessageRole role,
        String content,
        List<CitationDto> citations,
        Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage message, List<CitationDto> parsedCitations) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getContent(),
                parsedCitations != null ? parsedCitations : Collections.emptyList(),
                message.getCreatedAt()
        );
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return from(message, Collections.emptyList());
    }
}
