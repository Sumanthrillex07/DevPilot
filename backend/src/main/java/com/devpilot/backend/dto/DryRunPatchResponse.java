package com.devpilot.backend.dto;

import java.util.List;

/**
 * Result of dry-run applying a patch against original content.
 */
public record DryRunPatchResponse(
        boolean success,
        String patchedContent,
        List<String> errors,
        int linesAdded,
        int linesDeleted
) {
    public static DryRunPatchResponse failure(List<String> errors) {
        return new DryRunPatchResponse(false, null, errors, 0, 0);
    }

    public static DryRunPatchResponse success(String patchedContent, int linesAdded, int linesDeleted) {
        return new DryRunPatchResponse(true, patchedContent, List.of(), linesAdded, linesDeleted);
    }
}
