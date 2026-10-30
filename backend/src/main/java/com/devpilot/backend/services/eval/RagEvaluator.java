package com.devpilot.backend.services.eval;

import com.devpilot.backend.dto.CitationDto;
import com.devpilot.backend.dto.EvaluationResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluator for RAG retrieval quality, answer groundedness, citation validity,
 * and hallucination detection.
 */
@Component
public class RagEvaluator {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "is", "at", "which", "on", "a", "an", "and", "or", "in", "for",
            "to", "of", "with", "by", "from", "as", "how", "what", "why", "where",
            "does", "do", "can", "should", "code", "show", "me", "find", "this", "that"
    );

    private static final Set<String> STANDARD_LANG_KEYWORDS = Set.of(
            "string", "int", "integer", "boolean", "void", "public", "private", "protected",
            "return", "class", "interface", "record", "enum", "list", "map", "set", "null",
            "true", "false", "import", "package", "override", "final", "static", "new"
    );

    private static final Pattern CODE_BACKTICKS = Pattern.compile("`([A-Za-z0-9_$.]+)`");
    private static final Pattern IDENTIFIERS = Pattern.compile("\\b[a-zA-Z][a-zA-Z0-9_]{3,}\\b");

    /**
     * Evaluates a complete RAG interaction.
     */
    public EvaluationResult evaluate(
            String query,
            String answer,
            String retrievedContext,
            List<CitationDto> citations
    ) {
        double contextRelevance = computeContextRelevance(query, retrievedContext);
        List<String> hallucinations = new ArrayList<>();
        double groundedness = computeGroundedness(answer, retrievedContext, hallucinations);
        double citationValidity = computeCitationValidity(citations, retrievedContext);

        double overall = (0.35 * contextRelevance) + (0.45 * groundedness) + (0.20 * citationValidity);
        overall = Math.round(overall * 100.0) / 100.0;

        List<String> recommendations = generateRecommendations(contextRelevance, groundedness, citationValidity, overall);

        return new EvaluationResult(
                Math.round(contextRelevance * 100.0) / 100.0,
                Math.round(groundedness * 100.0) / 100.0,
                Math.round(citationValidity * 100.0) / 100.0,
                overall,
                hallucinations,
                recommendations,
                Instant.now()
        );
    }

    public double computeContextRelevance(String query, String retrievedContext) {
        if (retrievedContext == null || retrievedContext.isBlank() ||
                "(no matching code chunks found)".equals(retrievedContext.trim())) {
            return 0.0;
        }
        if (query == null || query.isBlank()) {
            return 1.0;
        }

        String lowerContext = retrievedContext.toLowerCase();
        List<String> queryTokens = Arrays.stream(query.toLowerCase().split("[^a-zA-Z0-9_]+"))
                .filter(t -> t.length() > 2 && !STOP_WORDS.contains(t))
                .distinct()
                .toList();

        if (queryTokens.isEmpty()) {
            return 1.0;
        }

        long matched = queryTokens.stream()
                .filter(t -> lowerContext.contains(t) ||
                        (t.length() >= 5 && lowerContext.contains(t.substring(0, 4))))
                .count();

        return (double) matched / queryTokens.size();
    }

    public double computeGroundedness(String answer, String retrievedContext, List<String> hallucinations) {
        if (answer == null || answer.isBlank()) {
            return 1.0;
        }
        if (retrievedContext == null || retrievedContext.isBlank()) {
            return 0.5;
        }

        String lowerContext = retrievedContext.toLowerCase();
        Set<String> checkedIdentifiers = new HashSet<>();

        // 1. Check symbols highlighted in markdown backticks
        Matcher backtickMatcher = CODE_BACKTICKS.matcher(answer);
        while (backtickMatcher.find()) {
            String symbol = backtickMatcher.group(1);
            checkSymbol(symbol, lowerContext, checkedIdentifiers, hallucinations);
        }

        // 2. Sample general code identifiers from answer
        Matcher idMatcher = IDENTIFIERS.matcher(answer);
        int sampleCount = 0;
        while (idMatcher.find() && sampleCount < 20) {
            String word = idMatcher.group();
            if (isCamelOrPascalCase(word)) {
                sampleCount++;
                checkSymbol(word, lowerContext, checkedIdentifiers, hallucinations);
            }
        }

        if (checkedIdentifiers.isEmpty()) {
            return 1.0;
        }

        double penalty = hallucinations.size() * 0.15;
        return Math.max(0.0, Math.min(1.0, 1.0 - penalty));
    }

    private void checkSymbol(String symbol, String lowerContext, Set<String> checked, List<String> hallucinations) {
        String lowerSymbol = symbol.toLowerCase();
        if (checked.add(lowerSymbol)) {
            if (!STANDARD_LANG_KEYWORDS.contains(lowerSymbol) && !lowerContext.contains(lowerSymbol)) {
                hallucinations.add("Unreferenced identifier '%s'".formatted(symbol));
            }
        }
    }

    public double computeCitationValidity(List<CitationDto> citations, String retrievedContext) {
        if (citations == null || citations.isEmpty()) {
            return (retrievedContext == null || retrievedContext.isBlank()) ? 1.0 : 0.6;
        }

        String lowerContext = (retrievedContext == null) ? "" : retrievedContext.toLowerCase();
        int validCount = 0;

        for (CitationDto citation : citations) {
            if (citation.filePath() == null || citation.filePath().isBlank()) {
                continue;
            }
            if (citation.startLine() != null && citation.endLine() != null) {
                if (citation.startLine() > citation.endLine() || citation.startLine() <= 0) {
                    continue;
                }
            }
            // If context is present, check if file, simple name, or base class name is mentioned
            String fileName = citation.filePath().replace('\\', '/').toLowerCase();
            if (!lowerContext.isEmpty()) {
                String simpleName = fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
                String baseName = simpleName.contains(".") ? simpleName.substring(0, simpleName.lastIndexOf('.')) : simpleName;
                if (!lowerContext.contains(fileName) && !lowerContext.contains(simpleName) && !lowerContext.contains(baseName)) {
                    continue;
                }
            }
            validCount++;
        }

        return (double) validCount / citations.size();
    }

    private boolean isCamelOrPascalCase(String word) {
        return word.matches("^[a-zA-Z][a-zA-Z0-9]*[A-Z][a-zA-Z0-9]*$");
    }

    private List<String> generateRecommendations(
            double contextRelevance,
            double groundedness,
            double citationValidity,
            double overall
    ) {
        List<String> recommendations = new ArrayList<>();
        if (contextRelevance < 0.6) {
            recommendations.add("Context relevance is low; consider expanding chunk overlap or checking vector similarity thresholds.");
        }
        if (groundedness < 0.75) {
            recommendations.add("Potential hallucinations detected; instruct LLM prompts to cite only symbols present in retrieved chunks.");
        }
        if (citationValidity < 0.8) {
            recommendations.add("Citation validity needs improvement; verify citation mapping line offsets.");
        }
        if (overall >= 0.85) {
            recommendations.add("RAG interaction demonstrates high groundedness and reliable codebase citations.");
        }
        return recommendations;
    }
}
