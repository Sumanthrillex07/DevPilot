package com.devpilot.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for dry-run applying a Git diff patch against original file contents.
 */
public record DryRunPatchRequest(
        @NotBlank(message = "Diff content cannot be blank")
        String diff,
        @NotNull(message = "Original file content cannot be null")
        String originalContent
) {}
