package com.devpilot.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload sent by the frontend to send a user question to a chat session.
 */
public record ChatMessageRequest(
        @NotBlank(message = "Message content must not be blank")
        String content
) {}
