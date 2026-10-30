package com.devpilot.backend.services.graph;

/**
 * Directed relationship edge from source to target.
 *
 * @param source     Source symbol or file path
 * @param target     Target symbol or imported file/module path
 * @param type       {@link RelationType} of the directed relationship
 * @param lineNumber Line where the relationship was established
 */
public record CodeRelation(
        String source,
        String target,
        RelationType type,
        int lineNumber
) {
}
