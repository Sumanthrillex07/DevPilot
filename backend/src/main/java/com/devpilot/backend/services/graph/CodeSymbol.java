package com.devpilot.backend.services.graph;

/**
 * Represents a code symbol (class, interface, function, import, package) identified in a source file.
 *
 * @param name       Identifier name (e.g. "UserService", "com.example.service", "handleRequest")
 * @param type       {@link SymbolType} kind
 * @param filePath   Repository-relative path of the declaring file
 * @param lineNumber 1-indexed line where the symbol is declared
 */
public record CodeSymbol(
        String name,
        SymbolType type,
        String filePath,
        int lineNumber
) {
}
