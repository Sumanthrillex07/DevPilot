package com.devpilot.backend.controllers;

import com.devpilot.backend.dto.BugReportResponse;
import com.devpilot.backend.dto.InvestigateBugRequest;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.services.ai.BugInvestigationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller exposing autonomous bug investigation capabilities.
 */
@RestController
@RequestMapping("/api/v1/repos")
@RequiredArgsConstructor
public class BugInvestigationController {

    private final CurrentUser currentUser;
    private final BugInvestigationService bugInvestigationService;

    @PostMapping("/{id}/investigate")
    public ResponseEntity<BugReportResponse> investigateBug(
            @PathVariable UUID id,
            @Valid @RequestBody InvestigateBugRequest request) {
        UUID userId = currentUser.require().getId();
        return ResponseEntity.ok(bugInvestigationService.investigate(userId, id, request));
    }
}
