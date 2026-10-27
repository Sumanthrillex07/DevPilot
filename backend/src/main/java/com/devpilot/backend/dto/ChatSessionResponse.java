package com.devpilot.backend.dto;

import com.devpilot.backend.entities.ChatSession;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(
    UUID id,
    UUID userId,
    UUID repositoryId,
    String title,
    Instant createdAt
) {
    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
            session.getId(),
            session.getUserId(),
            session.getRepositoryId(),
            session.getTitle(),
            session.getCreatedAt()
        );
    }
}
