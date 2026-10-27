package com.devpilot.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health endpoint for Part 1.
 *
 * <p>Exposed at GET /api/v1/health and intentionally kept public (no authentication required).
 * This allows load balancers, Docker health checks, and the frontend shell to verify
 * that the backend is reachable without needing credentials.
 *
 * <p>The Actuator endpoint (/actuator/health) is also available but serves ops tooling.
 * This endpoint serves the frontend UI specifically.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    /**
     * Returns a simple JSON health payload.
     *
     * <p>Response body:
     * <pre>
     * {
     *   "status": "UP",
     *   "version": "1.0.0",
     *   "timestamp": "2024-01-01T00:00:00Z"
     * }
     * </pre>
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "version", "1.0.0",
                "timestamp", Instant.now().toString()
        ));
    }
}
