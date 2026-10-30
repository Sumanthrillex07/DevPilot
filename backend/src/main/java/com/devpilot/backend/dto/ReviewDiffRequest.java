package com.devpilot.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to review a raw diff/patch text directly.
 */
public record ReviewDiffRequest(
        String title,
        String description,
        @NotBlank(message = "Diff text cannot be blank")
        String diffText
) {
}
