package com.devpilot.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response payload containing an AI-generated patch and safety validation analysis.
 */
public record GeneratedPatchResponse(
        UUID patchId,
        String instruction,
        String explanation,
        String unifiedDiff,
        List<String> affectedFiles,
        PatchValidationResult validation,
        List<CitationDto> citations,
        Instant generatedAt
) {}
