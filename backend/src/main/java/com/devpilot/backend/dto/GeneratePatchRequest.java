package com.devpilot.backend.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request payload for generating an autonomous code patch.
 */
public record GeneratePatchRequest(
        @NotBlank(message = "Instruction is required")
        String instruction,
        String targetFile,
        String errorContext,
        List<String> contextFilePaths
) {
    public GeneratePatchRequest(String instruction) {
        this(instruction, null, null, List.of());
    }
}
