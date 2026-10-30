package com.devpilot.backend.services.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, regex-based AST extractor for extracting symbols and dependency edges
 * across Java, TypeScript, JavaScript, Python, and Go source files.
 */
@Component
@Slf4j
public class CodeRelationshipExtractor {

    // --- Java patterns ---
    private static final Pattern JAVA_PKG = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern JAVA_IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern JAVA_CLASS = Pattern.compile(
            "^\\s*(?:public|protected|private)?\\s*(?:abstract|final|sealed|non-sealed)?\\s*(class|interface|record|enum)\\s+([a-zA-Z0-9_]+)(?:<[^>]+>)?(?:\\s+extends\\s+([a-zA-Z0-9_.,\\s<>]+?))?(?:\\s+implements\\s+([a-zA-Z0-9_.,\\s<>]+?))?(?:\\s*\\{.*)?$"
    );

    // --- TypeScript / JavaScript ES6 patterns ---
    private static final Pattern JS_IMPORT = Pattern.compile(
            "^\\s*import\\s+(?:(?:\\*\\s+as\\s+[a-zA-Z0-9_$]+|[a-zA-Z0-9_$,\\s{}]+)\\s+from\\s+)?['\"]([^'\"]+)['\"]"
    );
    private static final Pattern JS_CLASS = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:abstract\\s+)?(class|interface)\\s+([a-zA-Z0-9_$]+)(?:<[^>]+>)?(?:\\s+extends\\s+([a-zA-Z0-9_$,\\s<>]+?))?(?:\\s+implements\\s+([a-zA-Z0-9_$,\\s<>]+?))?(?:\\s*\\{.*)?$"
    );
    private static final Pattern JS_FUNCTION = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\s+([a-zA-Z0-9_$]+)"
    );

    // --- Python patterns ---
    private static final Pattern PY_IMPORT = Pattern.compile(
            "^\\s*import\\s+([a-zA-Z0-9_.,\\s]+)"
    );
    private static final Pattern PY_FROM_IMPORT = Pattern.compile(
            "^\\s*from\\s+([a-zA-Z0-9_.]+)\\s+import\\s+([a-zA-Z0-9_*,\\s()]+)"
    );
    private static final Pattern PY_CLASS = Pattern.compile(
            "^\\s*class\\s+([a-zA-Z0-9_]+)(?:\\s*\\(([^)]*)\\))?\\s*:"
    );
    private static final Pattern PY_FUNC = Pattern.compile(
            "^\\s*(?:async\\s+)?def\\s+([a-zA-Z0-9_]+)\\s*\\("
    );

    // --- Go patterns ---
    private static final Pattern GO_PKG = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_]+)");
    private static final Pattern GO_IMPORT_SINGLE = Pattern.compile("^\\s*import\\s+(?:[a-zA-Z0-9_]+\\s+)?[\"]([^\"]+)[\"]");
    private static final Pattern GO_TYPE = Pattern.compile("^\\s*type\\s+([a-zA-Z0-9_]+)\\s+(struct|interface)");
    private static final Pattern GO_FUNC = Pattern.compile("^\\s*func\\s+(?:\\([^)]+\\)\\s+)?([a-zA-Z0-9_]+)\\s*\\(");

    /**
     * Extracts symbols and relations from source file content based on extension.
     *
     * @param filePath Repository-relative file path
     * @param content  Source code content
     * @return Extracted {@link ExtractedFileAnalysis} with declared symbols and outgoing relations
     */
    public ExtractedFileAnalysis extract(String filePath, String content) {
        if (content == null || content.isBlank() || filePath == null) {
            return new ExtractedFileAnalysis(List.of(), List.of());
        }

        String lowerPath = filePath.toLowerCase();
        List<CodeSymbol> symbols = new ArrayList<>();
        List<CodeRelation> relations = new ArrayList<>();

        String[] lines = content.split("\r?\n");

        if (lowerPath.endsWith(".java")) {
            extractJava(filePath, lines, symbols, relations);
        } else if (lowerPath.endsWith(".ts") || lowerPath.endsWith(".tsx") || lowerPath.endsWith(".js") || lowerPath.endsWith(".jsx")) {
            extractTypeScript(filePath, lines, symbols, relations);
        } else if (lowerPath.endsWith(".py")) {
            extractPython(filePath, lines, symbols, relations);
        } else if (lowerPath.endsWith(".go")) {
            extractGo(filePath, lines, symbols, relations);
        }

        return new ExtractedFileAnalysis(symbols, relations);
    }

    private void extractJava(String filePath, String[] lines, List<CodeSymbol> symbols, List<CodeRelation> relations) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            Matcher pkgMatcher = JAVA_PKG.matcher(line);
            if (pkgMatcher.find()) {
                symbols.add(new CodeSymbol(pkgMatcher.group(1), SymbolType.PACKAGE, filePath, lineNum));
                continue;
            }

            Matcher importMatcher = JAVA_IMPORT.matcher(line);
            if (importMatcher.find()) {
                String imported = importMatcher.group(1);
                symbols.add(new CodeSymbol(imported, SymbolType.IMPORT, filePath, lineNum));
                relations.add(new CodeRelation(filePath, imported, RelationType.IMPORTS, lineNum));
                continue;
            }

            Matcher classMatcher = JAVA_CLASS.matcher(line);
            if (classMatcher.find()) {
                String kind = classMatcher.group(1);
                String name = classMatcher.group(2);
                String extendsClause = classMatcher.group(3);
                String implementsClause = classMatcher.group(4);

                SymbolType symType = "interface".equals(kind) ? SymbolType.INTERFACE : SymbolType.CLASS;
                symbols.add(new CodeSymbol(name, symType, filePath, lineNum));
                relations.add(new CodeRelation(filePath, name, RelationType.DEFINES, lineNum));

                if (extendsClause != null && !extendsClause.isBlank()) {
                    for (String parent : cleanIdentifiers(extendsClause)) {
                        relations.add(new CodeRelation(name, parent, RelationType.EXTENDS, lineNum));
                    }
                }

                if (implementsClause != null && !implementsClause.isBlank()) {
                    for (String iface : cleanIdentifiers(implementsClause)) {
                        relations.add(new CodeRelation(name, iface, RelationType.IMPLEMENTS, lineNum));
                    }
                }
            }
        }
    }

    private void extractTypeScript(String filePath, String[] lines, List<CodeSymbol> symbols, List<CodeRelation> relations) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            Matcher importMatcher = JS_IMPORT.matcher(line);
            if (importMatcher.find()) {
                String module = importMatcher.group(1);
                symbols.add(new CodeSymbol(module, SymbolType.IMPORT, filePath, lineNum));
                relations.add(new CodeRelation(filePath, module, RelationType.IMPORTS, lineNum));
                continue;
            }

            Matcher classMatcher = JS_CLASS.matcher(line);
            if (classMatcher.find()) {
                String kind = classMatcher.group(1);
                String name = classMatcher.group(2);
                String extendsClause = classMatcher.group(3);
                String implementsClause = classMatcher.group(4);

                SymbolType symType = "interface".equalsIgnoreCase(kind) ? SymbolType.INTERFACE : SymbolType.CLASS;
                symbols.add(new CodeSymbol(name, symType, filePath, lineNum));
                relations.add(new CodeRelation(filePath, name, RelationType.DEFINES, lineNum));

                if (extendsClause != null && !extendsClause.isBlank()) {
                    for (String parent : cleanIdentifiers(extendsClause)) {
                        relations.add(new CodeRelation(name, parent, RelationType.EXTENDS, lineNum));
                    }
                }
                if (implementsClause != null && !implementsClause.isBlank()) {
                    for (String iface : cleanIdentifiers(implementsClause)) {
                        relations.add(new CodeRelation(name, iface, RelationType.IMPLEMENTS, lineNum));
                    }
                }
                continue;
            }

            Matcher funcMatcher = JS_FUNCTION.matcher(line);
            if (funcMatcher.find()) {
                String funcName = funcMatcher.group(1);
                symbols.add(new CodeSymbol(funcName, SymbolType.FUNCTION, filePath, lineNum));
                relations.add(new CodeRelation(filePath, funcName, RelationType.DEFINES, lineNum));
            }
        }
    }

    private void extractPython(String filePath, String[] lines, List<CodeSymbol> symbols, List<CodeRelation> relations) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            Matcher fromImportMatcher = PY_FROM_IMPORT.matcher(line);
            if (fromImportMatcher.find()) {
                String module = fromImportMatcher.group(1);
                symbols.add(new CodeSymbol(module, SymbolType.IMPORT, filePath, lineNum));
                relations.add(new CodeRelation(filePath, module, RelationType.IMPORTS, lineNum));
                continue;
            }

            Matcher importMatcher = PY_IMPORT.matcher(line);
            if (importMatcher.find()) {
                String imported = importMatcher.group(1).trim();
                symbols.add(new CodeSymbol(imported, SymbolType.IMPORT, filePath, lineNum));
                relations.add(new CodeRelation(filePath, imported, RelationType.IMPORTS, lineNum));
                continue;
            }

            Matcher classMatcher = PY_CLASS.matcher(line);
            if (classMatcher.find()) {
                String className = classMatcher.group(1);
                String baseClasses = classMatcher.group(2);
                symbols.add(new CodeSymbol(className, SymbolType.CLASS, filePath, lineNum));
                relations.add(new CodeRelation(filePath, className, RelationType.DEFINES, lineNum));

                if (baseClasses != null && !baseClasses.isBlank()) {
                    for (String base : cleanIdentifiers(baseClasses)) {
                        relations.add(new CodeRelation(className, base, RelationType.EXTENDS, lineNum));
                    }
                }
                continue;
            }

            Matcher funcMatcher = PY_FUNC.matcher(line);
            if (funcMatcher.find()) {
                String funcName = funcMatcher.group(1);
                symbols.add(new CodeSymbol(funcName, SymbolType.FUNCTION, filePath, lineNum));
                relations.add(new CodeRelation(filePath, funcName, RelationType.DEFINES, lineNum));
            }
        }
    }

    private void extractGo(String filePath, String[] lines, List<CodeSymbol> symbols, List<CodeRelation> relations) {
        boolean insideMultiImport = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            Matcher pkgMatcher = GO_PKG.matcher(line);
            if (pkgMatcher.find()) {
                symbols.add(new CodeSymbol(pkgMatcher.group(1), SymbolType.PACKAGE, filePath, lineNum));
                continue;
            }

            if (line.trim().startsWith("import (")) {
                insideMultiImport = true;
                continue;
            }
            if (insideMultiImport) {
                if (line.trim().startsWith(")")) {
                    insideMultiImport = false;
                } else {
                    Matcher m = Pattern.compile("[\"]([^\"]+)[\"]").matcher(line);
                    if (m.find()) {
                        String imported = m.group(1);
                        symbols.add(new CodeSymbol(imported, SymbolType.IMPORT, filePath, lineNum));
                        relations.add(new CodeRelation(filePath, imported, RelationType.IMPORTS, lineNum));
                    }
                }
                continue;
            }

            Matcher singleImport = GO_IMPORT_SINGLE.matcher(line);
            if (singleImport.find()) {
                String imported = singleImport.group(1);
                symbols.add(new CodeSymbol(imported, SymbolType.IMPORT, filePath, lineNum));
                relations.add(new CodeRelation(filePath, imported, RelationType.IMPORTS, lineNum));
                continue;
            }

            Matcher typeMatcher = GO_TYPE.matcher(line);
            if (typeMatcher.find()) {
                String name = typeMatcher.group(1);
                String kind = typeMatcher.group(2);
                SymbolType st = "interface".equalsIgnoreCase(kind) ? SymbolType.INTERFACE : SymbolType.CLASS;
                symbols.add(new CodeSymbol(name, st, filePath, lineNum));
                relations.add(new CodeRelation(filePath, name, RelationType.DEFINES, lineNum));
                continue;
            }

            Matcher funcMatcher = GO_FUNC.matcher(line);
            if (funcMatcher.find()) {
                String name = funcMatcher.group(1);
                symbols.add(new CodeSymbol(name, SymbolType.FUNCTION, filePath, lineNum));
                relations.add(new CodeRelation(filePath, name, RelationType.DEFINES, lineNum));
            }
        }
    }

    private List<String> cleanIdentifiers(String clause) {
        List<String> list = new ArrayList<>();
        // Strip generics, split by commas
        String stripped = clause.replaceAll("<[^>]+>", " ");
        for (String part : stripped.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    public record ExtractedFileAnalysis(
            List<CodeSymbol> symbols,
            List<CodeRelation> relations
    ) {}
}
