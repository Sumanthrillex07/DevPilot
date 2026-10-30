package com.devpilot.backend.dto;

import com.devpilot.backend.services.graph.CodeRelation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API response representing the code dependency graph of a repository.
 */
public record DependencyGraphResponse(
        UUID repositoryId,
        int totalNodes,
        int totalEdges,
        Set<String> nodes,
        List<CodeRelation> edges
) {
}
