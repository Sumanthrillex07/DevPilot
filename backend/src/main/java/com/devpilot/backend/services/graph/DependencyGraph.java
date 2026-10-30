package com.devpilot.backend.services.graph;

import java.util.*;

/**
 * Directed graph of repository code relationships.
 */
public class DependencyGraph {

    private final Set<String> nodes = new LinkedHashSet<>();
    private final List<CodeRelation> edges = new ArrayList<>();
    private final Map<String, List<CodeRelation>> outgoing = new HashMap<>();
    private final Map<String, List<CodeRelation>> incoming = new HashMap<>();

    public synchronized void addNode(String node) {
        if (node != null && !node.isBlank()) {
            nodes.add(node);
        }
    }

    public synchronized void addEdge(CodeRelation relation) {
        if (relation == null) return;
        addNode(relation.source());
        addNode(relation.target());
        edges.add(relation);

        outgoing.computeIfAbsent(relation.source(), k -> new ArrayList<>()).add(relation);
        incoming.computeIfAbsent(relation.target(), k -> new ArrayList<>()).add(relation);
    }

    public Set<String> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }

    public List<CodeRelation> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public List<CodeRelation> getOutgoing(String node) {
        return outgoing.getOrDefault(node, Collections.emptyList());
    }

    public List<CodeRelation> getIncoming(String node) {
        return incoming.getOrDefault(node, Collections.emptyList());
    }

    /**
     * Returns 1-hop related nodes (both incoming and outgoing).
     */
    public Set<String> getNeighbors(String node) {
        Set<String> neighbors = new LinkedHashSet<>();
        for (CodeRelation out : getOutgoing(node)) {
            neighbors.add(out.target());
        }
        for (CodeRelation in : getIncoming(node)) {
            neighbors.add(in.source());
        }
        return neighbors;
    }

    /**
     * Clears all nodes and edges.
     */
    public synchronized void clear() {
        nodes.clear();
        edges.clear();
        outgoing.clear();
        incoming.clear();
    }
}
