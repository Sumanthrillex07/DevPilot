package com.devpilot.backend.dto;

import java.util.UUID;

/**
 * Read-only view of a User sent to the frontend.
 *
 * <p>We intentionally exclude sensitive fields (access_token, token_scopes) from
 * this response. The frontend only needs identity data: who am I, what's my avatar.
 *
 * <p>Using a Java {@code record} gives us an immutable, null-safe value object
 * with auto-generated constructor, equals/hashCode, and toString — no Lombok needed.
 */
public record UserResponse(
        UUID id,
        Long githubId,
        String githubUsername,
        String displayName,
        String avatarUrl
) {}
