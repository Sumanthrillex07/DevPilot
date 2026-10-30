package com.devpilot.backend.controllers;

import com.devpilot.backend.dto.*;
import com.devpilot.backend.security.AppUserPrincipal;
import com.devpilot.backend.services.ai.PatchGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for autonomous code patch generation, safety validation, and dry-run execution.
 */
@RestController
@RequestMapping("/api/v1/repos/{repoId}/patches")
public class PatchController {

    private final PatchGenerationService patchService;

    public PatchController(PatchGenerationService patchService) {
        this.patchService = patchService;
    }

    /**
     * Generates a safe Git unified diff patch for an instruction or bug fix.
     */
    @PostMapping("/generate")
    public ResponseEntity<GeneratedPatchResponse> generatePatch(
            @PathVariable UUID repoId,
            @Valid @RequestBody GeneratePatchRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        GeneratedPatchResponse response = patchService.generatePatch(repoId, request, principal.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Validates a Git unified diff patch for safety violations and syntactic correctness.
     */
    @PostMapping("/validate")
    public ResponseEntity<PatchValidationResult> validatePatch(
            @PathVariable UUID repoId,
            @Valid @RequestBody ValidatePatchRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        PatchValidationResult result = patchService.validatePatch(repoId, request, principal.getId());
        return ResponseEntity.ok(result);
    }

    /**
     * Tests applying a Git unified diff patch against original file contents in memory.
     */
    @PostMapping("/dry-run")
    public ResponseEntity<DryRunPatchResponse> dryRunPatch(
            @PathVariable UUID repoId,
            @Valid @RequestBody DryRunPatchRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        DryRunPatchResponse response = patchService.dryRunPatch(repoId, request, principal.getId());
        return ResponseEntity.ok(response);
    }
}
