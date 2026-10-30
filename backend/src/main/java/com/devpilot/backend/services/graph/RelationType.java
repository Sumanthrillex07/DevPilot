package com.devpilot.backend.services.graph;

/**
 * Kind of directed relationship between code symbols or files.
 */
public enum RelationType {
    IMPORTS,
    EXTENDS,
    IMPLEMENTS,
    CALLS,
    DEFINES
}
