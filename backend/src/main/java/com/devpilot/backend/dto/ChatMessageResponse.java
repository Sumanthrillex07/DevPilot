package com.devpilot.backend.dto;

import com.devpilot.backend.entities.ChatMessage;
import com.devpilot.backend.entities.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
    UUID id,
    UUID sessionId,
    MessageRole role,
    String content,
    String citations,
    Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
            message.getId(),
            message.getSessionId(),
            message.getRole(),
            message.getContent(),
            message.getCitations(),
            message.getCreatedAt()
        );
    }
}
