package com.devpilot.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateChatSessionRequest(
    @NotNull(message = "repositoryId is required")
    UUID repositoryId,
    String title
) {}
