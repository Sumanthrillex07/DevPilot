package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.BugReportResponse;
import com.devpilot.backend.dto.CitationDto;
import com.devpilot.backend.dto.InvestigateBugRequest;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.graph.DependencyGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Autonomous agent that investigates bug reports, error stack traces, and failing tests.
 * Combines stack trace parsing, semantic vector retrieval, and dependency graph traversal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BugInvestigationService {

    private final ConnectedRepositoryRepository repositoryRepository;
    private final CodeContextRetriever contextRetriever;
    private final DependencyGraphService dependencyGraphService;
    private final ChatClient chatClient;
    private final StackTraceParser stackTraceParser = new StackTraceParser();

    /**
     * Investigates a reported bug or error log against the codebase.
     *
     * @param userId       Authenticated user ID
     * @param repositoryId Repository ID
     * @param request      Bug investigation inputs
     * @return Structured {@link BugReportResponse}
     */
    public BugReportResponse investigate(UUID userId, UUID repositoryId, InvestigateBugRequest request) {
        ConnectedRepository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + repositoryId));

        if (!repo.getUserId().equals(userId)) {
            throw new UnauthorizedException("User does not own repository: " + repositoryId);
        }

        // 1. Parse error logs and stack traces if present
        var errorInfo = stackTraceParser.parse(request.errorLogs());
        String query = buildRetrievalQuery(request, errorInfo);

        // 2. Retrieve semantic context from vector store
        RetrievedContext context = contextRetriever.retrieve(repositoryId, query);

        // 3. Collect suspected files from stack frames & citations
        Set<String> suspectedFiles = new LinkedHashSet<>();
        if (errorInfo.topApplicationFrame() != null && errorInfo.topApplicationFrame().file() != null) {
            suspectedFiles.add(errorInfo.topApplicationFrame().file());
        }
        for (CitationDto citation : context.citations()) {
            suspectedFiles.add(citation.filePath());
        }

        // 4. Construct grounded diagnosis prompt for LLM
        String systemPrompt = """
                You are DevPilot's Autonomous Bug Investigation Agent.
                Analyze the provided code context, stack trace, and bug report to find the root cause.
                Structure your answer into 4 clearly labeled sections:
                1. SUMMARY: A concise 1-2 sentence description of the defect.
                2. ROOT CAUSE: Thorough explanation of the exact code defect and line numbers responsible.
                3. REPRODUCTION: Numbered steps or test scenario to reproduce the issue.
                4. SUGGESTED FIX: Concrete code changes or refactoring to resolve the bug safely.
                Be precise and cite file names and line numbers where appropriate.
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("### Bug Description:\n").append(request.description()).append("\n\n");
        if (request.errorLogs() != null && !request.errorLogs().isBlank()) {
            userPrompt.append("### Error Stack Trace:\n```\n").append(request.errorLogs()).append("\n```\n\n");
        }
        if (request.failingTest() != null && !request.failingTest().isBlank()) {
            userPrompt.append("### Failing Test:\n```\n").append(request.failingTest()).append("\n```\n\n");
        }
        userPrompt.append("### Relevant Codebase Context:\n").append(context.contextText());

        // 5. Query LLM
        String llmResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt.toString())
                .call()
                .content();

        if (llmResponse == null || llmResponse.isBlank()) {
            llmResponse = "Investigation completed with no definitive defect detected in provided snippets.";
        }

        // 6. Parse sections or package LLM response
        String summary = extractSection(llmResponse, "SUMMARY:", "ROOT CAUSE:", request.description());
        String rootCause = extractSection(llmResponse, "ROOT CAUSE:", "REPRODUCTION:", llmResponse);
        String reproduction = extractSection(llmResponse, "REPRODUCTION:", "SUGGESTED FIX:", "Run failing test or invoke endpoint with error payload.");
        String suggestedFix = extractSection(llmResponse, "SUGGESTED FIX:", null, "Review referenced files for null-checks and edge cases.");

        List<String> reproductionSteps = Arrays.stream(reproduction.split("\r?\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        return new BugReportResponse(
                repositoryId,
                summary,
                rootCause,
                new ArrayList<>(suspectedFiles),
                reproductionSteps,
                suggestedFix,
                context.citations(),
                Instant.now()
        );
    }

    private String buildRetrievalQuery(InvestigateBugRequest request, StackTraceParser.ParsedErrorInfo errorInfo) {
        StringBuilder sb = new StringBuilder(request.description());
        if (errorInfo.errorType() != null) {
            sb.append(" ").append(errorInfo.errorType());
        }
        if (errorInfo.errorMessage() != null) {
            sb.append(" ").append(errorInfo.errorMessage());
        }
        if (errorInfo.topApplicationFrame() != null) {
            sb.append(" ").append(errorInfo.topApplicationFrame().file())
              .append(" ").append(errorInfo.topApplicationFrame().method());
        }
        return sb.toString();
    }

    private String extractSection(String fullText, String startMarker, String endMarker, String defaultValue) {
        int start = fullText.indexOf(startMarker);
        if (start == -1) {
            return defaultValue;
        }
        start += startMarker.length();
        int end = endMarker != null ? fullText.indexOf(endMarker, start) : fullText.length();
        if (end == -1) {
            end = fullText.length();
        }
        String section = fullText.substring(start, end).trim();
        return section.isBlank() ? defaultValue : section;
    }
}
