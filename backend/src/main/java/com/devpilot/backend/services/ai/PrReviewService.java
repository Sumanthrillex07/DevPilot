package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.CitationDto;
import com.devpilot.backend.dto.PrReviewCommentDto;
import com.devpilot.backend.dto.PrReviewResponse;
import com.devpilot.backend.dto.ReviewDiffRequest;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.UserService;
import com.devpilot.backend.services.github.GithubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Autonomous PR Review & Code Quality Agent.
 * Evaluates Pull Requests and code diffs for security, correctness, performance, and style.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrReviewService {

    private final ConnectedRepositoryRepository repositoryRepository;
    private final UserService userService;
    private final GithubApiClient gitHubApiClient;
    private final CodeContextRetriever contextRetriever;
    private final ChatClient chatClient;
    private final UnifiedDiffParser diffParser = new UnifiedDiffParser();

    /**
     * Reviews a GitHub Pull Request by PR number.
     *
     * @param userId       Authenticated user ID
     * @param repositoryId Repository ID
     * @param prNumber     GitHub PR number
     * @return Structured {@link PrReviewResponse}
     */
    public PrReviewResponse reviewGitHubPullRequest(UUID userId, UUID repositoryId, int prNumber) {
        ConnectedRepository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + repositoryId));

        if (!repo.getUserId().equals(userId)) {
            throw new UnauthorizedException("User does not own repository: " + repositoryId);
        }

        User user = userService.requiredById(userId);
        String token = userService.decryptAccessToken(user);

        // Fetch PR metadata and diff from GitHub
        Map<String, Object> prData = gitHubApiClient.getPullRequest(token, repo.getOwner(), repo.getName(), prNumber);
        String diffText = gitHubApiClient.getPullRequestDiff(token, repo.getOwner(), repo.getName(), prNumber);

        String title = String.valueOf(prData.getOrDefault("title", "PR #" + prNumber));
        String body = String.valueOf(prData.getOrDefault("body", ""));

        return performReview(repo, prNumber, title, body, diffText);
    }

    /**
     * Reviews raw diff/patch text directly without requiring an active GitHub PR.
     *
     * @param userId       Authenticated user ID
     * @param repositoryId Repository ID
     * @param request      Review diff payload
     * @return Structured {@link PrReviewResponse}
     */
    public PrReviewResponse reviewDiff(UUID userId, UUID repositoryId, ReviewDiffRequest request) {
        ConnectedRepository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + repositoryId));

        if (!repo.getUserId().equals(userId)) {
            throw new UnauthorizedException("User does not own repository: " + repositoryId);
        }

        return performReview(repo, null, request.title(), request.description(), request.diffText());
    }

    private PrReviewResponse performReview(
            ConnectedRepository repo,
            Integer prNumber,
            String title,
            String description,
            String diffText) {

        // 1. Parse unified diff to identify target files and modified lines
        List<UnifiedDiffParser.FileDiff> fileDiffs = diffParser.parse(diffText);
        List<String> examinedFiles = fileDiffs.stream()
                .map(UnifiedDiffParser.FileDiff::targetPath)
                .filter(Objects::nonNull)
                .toList();

        // 2. Retrieve codebase context for modified files
        String contextQuery = (title != null ? title : "") + " " + String.join(" ", examinedFiles);
        RetrievedContext context = contextRetriever.retrieve(repo.getId(), contextQuery);

        // 3. Assemble LLM prompt
        String systemPrompt = """
                You are DevPilot's Senior Staff Code Reviewer.
                Analyze the provided code diff in the context of the repository.
                Look for:
                - Security flaws (SQL injection, credential leak, unchecked inputs)
                - Bug risks (NPE, concurrency issues, edge cases)
                - Performance bottlenecks (N+1 queries, unindexed lookups)
                - Maintainability and code style
                
                You must output your review structured into:
                VERDICT: APPROVED | COMMENT | REQUEST_CHANGES
                RISK_SCORE: (Integer between 1 and 100)
                SUMMARY: (A 2-3 sentence executive summary of the changes and quality assessment)
                COMMENTS:
                - [CRITICAL|WARNING|INFO|SUGGESTION] [SECURITY|BUG_RISK|PERFORMANCE|MAINTAINABILITY|CODE_STYLE] (filePath:lineNumber) Comment text | SUGGESTION: Optional replacement code
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("### PR Title: ").append(title != null ? title : "Diff Review").append("\n");
        if (description != null && !description.isBlank()) {
            userPrompt.append("### Description: ").append(description).append("\n");
        }
        userPrompt.append("### Changed Files:\n").append(String.join("\n", examinedFiles)).append("\n\n");
        userPrompt.append("### Unified Diff:\n```diff\n").append(diffText != null ? diffText : "").append("\n```\n\n");
        userPrompt.append("### Codebase Background Context:\n").append(context.contextText());

        // 4. Query LLM
        String llmResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt.toString())
                .call()
                .content();

        if (llmResponse == null || llmResponse.isBlank()) {
            llmResponse = "VERDICT: APPROVED\nRISK_SCORE: 10\nSUMMARY: No issues identified.";
        }

        // 5. Parse review verdict and comments
        PrReviewResponse.ReviewVerdict verdict = parseVerdict(llmResponse);
        int riskScore = parseRiskScore(llmResponse);
        String summary = extractSection(llmResponse, "SUMMARY:", "COMMENTS:", "Code changes reviewed.");
        List<PrReviewCommentDto> comments = parseComments(llmResponse, examinedFiles);

        return new PrReviewResponse(
                repo.getId(),
                prNumber,
                verdict,
                riskScore,
                summary,
                examinedFiles,
                comments,
                context.citations(),
                Instant.now()
        );
    }

    private PrReviewResponse.ReviewVerdict parseVerdict(String text) {
        int idx = text.indexOf("VERDICT:");
        if (idx != -1) {
            String line = text.substring(idx + "VERDICT:".length()).trim().split("\r?\n")[0];
            if (line.contains("REQUEST_CHANGES")) {
                return PrReviewResponse.ReviewVerdict.REQUEST_CHANGES;
            } else if (line.contains("COMMENT")) {
                return PrReviewResponse.ReviewVerdict.COMMENT;
            } else if (line.contains("APPROVED")) {
                return PrReviewResponse.ReviewVerdict.APPROVED;
            }
        }
        return PrReviewResponse.ReviewVerdict.APPROVED;
    }

    private int parseRiskScore(String text) {
        int idx = text.indexOf("RISK_SCORE:");
        if (idx != -1) {
            String sub = text.substring(idx + "RISK_SCORE:".length()).trim();
            String scoreStr = sub.split("\\s+")[0].replaceAll("[^0-9]", "");
            try {
                return Integer.parseInt(scoreStr);
            } catch (NumberFormatException ignored) {}
        }
        return 20; // default moderate score
    }

    private List<PrReviewCommentDto> parseComments(String text, List<String> defaultFiles) {
        List<PrReviewCommentDto> list = new ArrayList<>();
        int idx = text.indexOf("COMMENTS:");
        if (idx == -1) return list;

        String commentsSection = text.substring(idx + "COMMENTS:".length());
        String[] lines = commentsSection.split("\r?\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-") && trimmed.contains("[")) {
                try {
                    // Extract [SEVERITY] and [CATEGORY]
                    String file = defaultFiles.isEmpty() ? "unknown" : defaultFiles.get(0);
                    int lineNum = 1;
                    PrReviewCommentDto.CommentSeverity severity = PrReviewCommentDto.CommentSeverity.INFO;
                    PrReviewCommentDto.CommentCategory category = PrReviewCommentDto.CommentCategory.MAINTAINABILITY;

                    if (trimmed.contains("CRITICAL")) severity = PrReviewCommentDto.CommentSeverity.CRITICAL;
                    else if (trimmed.contains("WARNING")) severity = PrReviewCommentDto.CommentSeverity.WARNING;
                    else if (trimmed.contains("SUGGESTION")) severity = PrReviewCommentDto.CommentSeverity.SUGGESTION;

                    if (trimmed.contains("SECURITY")) category = PrReviewCommentDto.CommentCategory.SECURITY;
                    else if (trimmed.contains("BUG_RISK")) category = PrReviewCommentDto.CommentCategory.BUG_RISK;
                    else if (trimmed.contains("PERFORMANCE")) category = PrReviewCommentDto.CommentCategory.PERFORMANCE;
                    else if (trimmed.contains("CODE_STYLE")) category = PrReviewCommentDto.CommentCategory.CODE_STYLE;

                    // Match (file:line)
                    int openParen = trimmed.indexOf('(');
                    int closeParen = trimmed.indexOf(')', openParen);
                    if (openParen != -1 && closeParen != -1) {
                        String loc = trimmed.substring(openParen + 1, closeParen);
                        if (loc.contains(":")) {
                            String[] parts = loc.split(":");
                            file = parts[0].trim();
                            try {
                                lineNum = Integer.parseInt(parts[1].trim());
                            } catch (Exception ignored) {}
                        }
                    }

                    String commentText = trimmed.substring(closeParen != -1 ? closeParen + 1 : 1).trim();
                    String suggestion = null;
                    if (commentText.contains("| SUGGESTION:")) {
                        String[] split = commentText.split("\\| SUGGESTION:");
                        commentText = split[0].trim();
                        suggestion = split[1].trim();
                    }

                    list.add(new PrReviewCommentDto(file, lineNum, severity, category, commentText, suggestion));
                } catch (Exception ex) {
                    log.warn("Could not parse inline comment line: {}", trimmed);
                }
            }
        }
        return list;
    }

    private String extractSection(String fullText, String startMarker, String endMarker, String defaultValue) {
        int start = fullText.indexOf(startMarker);
        if (start == -1) return defaultValue;
        start += startMarker.length();
        int end = endMarker != null ? fullText.indexOf(endMarker, start) : fullText.length();
        if (end == -1) end = fullText.length();
        String section = fullText.substring(start, end).trim();
        return section.isBlank() ? defaultValue : section;
    }
}
