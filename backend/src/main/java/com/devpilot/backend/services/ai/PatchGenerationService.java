package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.*;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autonomous service orchestrating grounded code patch generation, safety analysis,
 * and dry-run application.
 */
@Service
public class PatchGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PatchGenerationService.class);

    private final ConnectedRepositoryRepository repositoryRepository;
    private final CodeContextRetriever contextRetriever;
    private final ChatClient chatClient;
    private final UnifiedDiffParser diffParser;
    private final PatchSafetyValidator safetyValidator;
    private final PatchApplier patchApplier;

    private static final Pattern DIFF_CODE_BLOCK = Pattern.compile("```(?:diff)?\\s*\\n([\\s\\S]*?)```");

    public PatchGenerationService(
            ConnectedRepositoryRepository repositoryRepository,
            CodeContextRetriever contextRetriever,
            ChatClient chatClient,
            UnifiedDiffParser diffParser,
            PatchSafetyValidator safetyValidator,
            PatchApplier patchApplier
    ) {
        this.repositoryRepository = repositoryRepository;
        this.contextRetriever = contextRetriever;
        this.chatClient = chatClient;
        this.diffParser = diffParser;
        this.safetyValidator = safetyValidator;
        this.patchApplier = patchApplier;
    }

    /**
     * Generates a safe Git unified diff patch grounded by vector retrieval from the repository.
     */
    public GeneratedPatchResponse generatePatch(UUID repoId, GeneratePatchRequest request, UUID userId) {
        verifyRepositoryAccess(repoId, userId);

        StringBuilder query = new StringBuilder(request.instruction());
        if (request.targetFile() != null && !request.targetFile().isBlank()) {
            query.append(" ").append(request.targetFile());
        }
        if (request.errorContext() != null && !request.errorContext().isBlank()) {
            query.append(" ").append(request.errorContext());
        }

        RetrievedContext context = contextRetriever.retrieve(repoId, query.toString());

        String systemPrompt = """
                You are DevPilot's Safe Patch Generation Agent, an expert AI software engineer.
                Your job is to generate a minimal, clean, and safe Git unified diff patch addressing the user instruction.

                Rules:
                1. Output ONLY a valid Git unified diff patch inside a ```diff ... ``` code block.
                2. Include standard unified diff headers:
                   diff --git a/<path> b/<path>
                   --- a/<path>
                   +++ b/<path>
                   @@ -<oldStart>,<oldLen> +<newStart>,<newLen> @@
                3. Include sufficient context lines (' ') around additions ('+') and deletions ('-').
                4. Immediately after the diff block, provide an 'EXPLANATION:' section explaining what changes were made, why, and test verification advice.
                5. Do NOT include markdown blocks other than the diff.
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("INSTRUCTION:\n").append(request.instruction()).append("\n\n");
        if (request.targetFile() != null && !request.targetFile().isBlank()) {
            userPrompt.append("TARGET FILE:\n").append(request.targetFile()).append("\n\n");
        }
        if (request.errorContext() != null && !request.errorContext().isBlank()) {
            userPrompt.append("ERROR / LOG CONTEXT:\n").append(request.errorContext()).append("\n\n");
        }
        if (!context.contextText().isBlank() && !"(no matching code chunks found)".equals(context.contextText())) {
            userPrompt.append("CODEBASE REPOSITORY CONTEXT:\n").append(context.contextText()).append("\n\n");
        }
        userPrompt.append("Generate the patch diff now.");

        log.info("Generating patch for repo {} with instruction: {}", repoId, request.instruction());
        String llmResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt.toString())
                .call()
                .content();

        String unifiedDiff = extractDiff(llmResponse);
        String explanation = extractExplanation(llmResponse);

        PatchValidationResult validation = safetyValidator.validate(unifiedDiff, request.targetFile(), null);

        List<UnifiedDiffParser.FileDiff> parsedFiles = diffParser.parse(unifiedDiff);
        List<String> affectedFiles = parsedFiles.stream()
                .map(UnifiedDiffParser.FileDiff::targetPath)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .toList();

        return new GeneratedPatchResponse(
                UUID.randomUUID(),
                request.instruction(),
                explanation,
                unifiedDiff,
                affectedFiles,
                validation,
                context.citations(),
                Instant.now()
        );
    }

    /**
     * Validates a Git diff for security violations and syntactic correctness.
     */
    public PatchValidationResult validatePatch(UUID repoId, ValidatePatchRequest request, UUID userId) {
        verifyRepositoryAccess(repoId, userId);
        return safetyValidator.validate(request.diff(), request.targetFilePath(), request.originalFileContent());
    }

    /**
     * Dry-runs a Git diff against original content in memory.
     */
    public DryRunPatchResponse dryRunPatch(UUID repoId, DryRunPatchRequest request, UUID userId) {
        verifyRepositoryAccess(repoId, userId);
        PatchApplier.ApplyResult result = patchApplier.apply(request.originalContent(), request.diff());
        if (!result.success()) {
            return DryRunPatchResponse.failure(result.errors());
        }
        return DryRunPatchResponse.success(result.patchedContent(), result.linesAdded(), result.linesDeleted());
    }

    private ConnectedRepository verifyRepositoryAccess(UUID repoId, UUID userId) {
        ConnectedRepository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + repoId));
        if (!repo.getUserId().equals(userId)) {
            throw new UnauthorizedException("You do not have access to repository " + repoId);
        }
        return repo;
    }

    private String extractDiff(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = DIFF_CODE_BLOCK.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        if (text.contains("diff --git") || text.contains("--- a/")) {
            int start = text.indexOf("diff --git");
            if (start == -1) start = text.indexOf("--- a/");
            int exp = text.indexOf("EXPLANATION:");
            if (exp > start) {
                return text.substring(start, exp).trim();
            }
            return text.substring(start).trim();
        }
        return text.trim();
    }

    private String extractExplanation(String text) {
        if (text == null || text.isBlank()) {
            return "Patch generated successfully.";
        }
        int expIndex = text.indexOf("EXPLANATION:");
        if (expIndex != -1) {
            return text.substring(expIndex + "EXPLANATION:".length()).trim();
        }
        int lastFence = text.lastIndexOf("```");
        if (lastFence != -1 && lastFence + 3 < text.length()) {
            String trailing = text.substring(lastFence + 3).trim();
            if (!trailing.isBlank()) {
                return trailing;
            }
        }
        return "Patch generated to address the given instruction.";
    }
}
