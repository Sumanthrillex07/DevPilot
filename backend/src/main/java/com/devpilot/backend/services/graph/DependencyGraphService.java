package com.devpilot.backend.services.graph;

import com.devpilot.backend.dto.DependencyGraphResponse;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages repository dependency graphs and provides neighbor expansion for RAG context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DependencyGraphService {

    private final ConnectedRepositoryRepository repositoryRepository;
    private final CodeRelationshipExtractor relationshipExtractor;

    // In-memory graph cache keyed by repositoryId
    private final Map<UUID, DependencyGraph> graphCache = new ConcurrentHashMap<>();

    /**
     * Builds or replaces the dependency graph for a repository using a map of filePath -> fileContent.
     *
     * @param repositoryId UUID of repository
     * @param files        Map of relative file path to source text
     * @return Generated {@link DependencyGraph}
     */
    public DependencyGraph buildGraph(UUID repositoryId, Map<String, String> files) {
        DependencyGraph graph = new DependencyGraph();

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey();
            String content = entry.getValue();

            graph.addNode(path);
            var analysis = relationshipExtractor.extract(path, content);

            for (CodeSymbol sym : analysis.symbols()) {
                graph.addNode(sym.name());
            }

            for (CodeRelation rel : analysis.relations()) {
                graph.addEdge(rel);
            }
        }

        graphCache.put(repositoryId, graph);
        log.info("Built dependency graph for repo {} with {} nodes and {} edges",
                repositoryId, graph.getNodes().size(), graph.getEdges().size());
        return graph;
    }

    /**
     * Gets the dependency graph for a repository, enforcing ownership.
     *
     * @param userId       ID of the authenticated user
     * @param repositoryId ID of the repository
     * @return {@link DependencyGraphResponse}
     */
    public DependencyGraphResponse getGraphResponse(UUID userId, UUID repositoryId) {
        ConnectedRepository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + repositoryId));

        if (!repo.getUserId().equals(userId)) {
            throw new UnauthorizedException("User does not own repository: " + repositoryId);
        }

        DependencyGraph graph = graphCache.getOrDefault(repositoryId, new DependencyGraph());
        return new DependencyGraphResponse(
                repositoryId,
                graph.getNodes().size(),
                graph.getEdges().size(),
                graph.getNodes(),
                graph.getEdges()
        );
    }

    /**
     * Returns 1-hop related code references for a given file or symbol path.
     *
     * @param repositoryId ID of repository
     * @param identifier   File path or symbol name
     * @return Set of related identifiers
     */
    public Set<String> getRelatedIdentifiers(UUID repositoryId, String identifier) {
        DependencyGraph graph = graphCache.get(repositoryId);
        if (graph == null) {
            return Collections.emptySet();
        }
        return graph.getNeighbors(identifier);
    }

    /**
     * Adds an edge directly to the cached graph if present.
     */
    public void addRelation(UUID repositoryId, CodeRelation relation) {
        DependencyGraph graph = graphCache.computeIfAbsent(repositoryId, k -> new DependencyGraph());
        graph.addEdge(relation);
    }

    public DependencyGraph getGraph(UUID repositoryId) {
        return graphCache.getOrDefault(repositoryId, new DependencyGraph());
    }
}
