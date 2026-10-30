package com.devpilot.backend.dto;

import java.util.List;

/**
 * Result of validating a Git unified diff patch.
 */
public record PatchValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        int filesChanged,
        int linesAdded,
        int linesDeleted,
        boolean dryRunSuccess
) {
    public static PatchValidationResult failure(List<String> errors) {
        return new PatchValidationResult(false, errors, List.of(), 0, 0, 0, false);
    }

    public static PatchValidationResult success(
            List<String> warnings,
            int filesChanged,
            int linesAdded,
            int linesDeleted,
            boolean dryRunSuccess
    ) {
        return new PatchValidationResult(true, List.of(), warnings, filesChanged, linesAdded, linesDeleted, dryRunSuccess);
    }
}
