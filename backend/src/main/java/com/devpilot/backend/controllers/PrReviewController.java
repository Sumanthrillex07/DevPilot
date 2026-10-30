package com.devpilot.backend.controllers;

import com.devpilot.backend.dto.PrReviewResponse;
import com.devpilot.backend.dto.ReviewDiffRequest;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.services.ai.PrReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller exposing autonomous Pull Request and diff review capabilities.
 */
@RestController
@RequestMapping("/api/v1/repos")
@RequiredArgsConstructor
public class PrReviewController {

    private final CurrentUser currentUser;
    private final PrReviewService prReviewService;

    @PostMapping("/{id}/reviews/github/{prNumber}")
    public ResponseEntity<PrReviewResponse> reviewGitHubPullRequest(
            @PathVariable UUID id,
            @PathVariable int prNumber) {
        UUID userId = currentUser.require().getId();
        return ResponseEntity.ok(prReviewService.reviewGitHubPullRequest(userId, id, prNumber));
    }

    @PostMapping("/{id}/reviews/diff")
    public ResponseEntity<PrReviewResponse> reviewDiff(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewDiffRequest request) {
        UUID userId = currentUser.require().getId();
        return ResponseEntity.ok(prReviewService.reviewDiff(userId, id, request));
    }
}
