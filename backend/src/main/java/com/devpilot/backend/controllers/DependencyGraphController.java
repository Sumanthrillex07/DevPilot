package com.devpilot.backend.controllers;

import com.devpilot.backend.dto.DependencyGraphResponse;
import com.devpilot.backend.security.CurrentUser;
import com.devpilot.backend.services.graph.DependencyGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller exposing repository dependency graph and code relationships.
 */
@RestController
@RequestMapping("/api/v1/repos")
@RequiredArgsConstructor
public class DependencyGraphController {

    private final CurrentUser currentUser;
    private final DependencyGraphService dependencyGraphService;

    @GetMapping("/{id}/graph")
    public ResponseEntity<DependencyGraphResponse> getGraph(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return ResponseEntity.ok(dependencyGraphService.getGraphResponse(userId, id));
    }
}
