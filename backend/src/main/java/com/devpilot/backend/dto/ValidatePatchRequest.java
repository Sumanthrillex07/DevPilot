package com.devpilot.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for validating a Git unified diff patch for safety and syntax.
 */
public record ValidatePatchRequest(
        @NotBlank(message = "Diff content cannot be blank")
        String diff,
        String targetFilePath,
        String originalFileContent
) {
    public ValidatePatchRequest(String diff) {
        this(diff, null, null);
    }
}
