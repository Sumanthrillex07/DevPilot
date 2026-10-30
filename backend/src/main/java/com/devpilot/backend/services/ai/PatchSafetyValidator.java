package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.PatchValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safety and syntax validator for AI-generated code patches.
 * Inspects diff structure, enforces path constraints, detects dangerous code constructs,
 * checks syntactic balance, and performs in-memory dry-run verification.
 */
@Component
public class PatchSafetyValidator {

    private final UnifiedDiffParser diffParser;
    private final PatchApplier patchApplier;

    private static final Set<String> FORBIDDEN_FILE_SEGMENTS = Set.of(
            ".git",
            ".env",
            ".aws",
            ".ssh",
            "credentials",
            "id_rsa",
            "id_ed25519"
    );

    private static final List<SecurityRule> SECURITY_RULES = List.of(
            new SecurityRule(
                    Pattern.compile("(?i)Runtime\\.getRuntime\\(\\)\\.exec"),
                    "Dangerous process execution via Runtime.getRuntime().exec() detected",
                    true
            ),
            new SecurityRule(
                    Pattern.compile("(?i)\\bProcessBuilder\\b"),
                    "ProcessBuilder execution detected; verify command inputs are strictly sanitized",
                    false
            ),
            new SecurityRule(
                    Pattern.compile("(?i)\\b(child_process|subprocess\\.Popen|subprocess\\.run|os\\.system)\\b"),
                    "Subprocess execution command detected",
                    false
            ),
            new SecurityRule(
                    Pattern.compile("(?i)\\b(eval|exec)\\s*\\("),
                    "Dynamic code execution via eval/exec detected",
                    false
            ),
            new SecurityRule(
                    Pattern.compile("-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----"),
                    "Hardcoded cryptographic private key detected in patch",
                    true
            ),
            new SecurityRule(
                    Pattern.compile("ghp_[A-Za-z0-9_]{30,}"),
                    "Potential hardcoded GitHub Personal Access Token detected",
                    true
            ),
            new SecurityRule(
                    Pattern.compile("(?i)\\bSystem\\.exit\\s*\\("),
                    "System.exit() detected in patch",
                    false
            )
    );

    private record SecurityRule(Pattern pattern, String message, boolean isError) {}

    public PatchSafetyValidator(UnifiedDiffParser diffParser, PatchApplier patchApplier) {
        this.diffParser = diffParser;
        this.patchApplier = patchApplier;
    }

    /**
     * Validates a Git unified diff text for safety, syntactic plausibility, and optionally executes a dry-run.
     *
     * @param diffText            Raw unified diff
     * @param targetFilePath      Optional target file path to verify
     * @param originalFileContent Optional original file content to simulate patch application
     * @return {@link PatchValidationResult}
     */
    public PatchValidationResult validate(String diffText, String targetFilePath, String originalFileContent) {
        if (diffText == null || diffText.isBlank()) {
            return PatchValidationResult.failure(List.of("Diff content is empty."));
        }

        List<UnifiedDiffParser.FileDiff> fileDiffs = diffParser.parse(diffText);
        if (fileDiffs.isEmpty()) {
            return PatchValidationResult.failure(List.of("Diff contains no recognizable file modifications or hunks."));
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int totalAdded = 0;
        int totalDeleted = 0;

        for (UnifiedDiffParser.FileDiff fileDiff : fileDiffs) {
            String target = fileDiff.targetPath();
            validatePathSafety(target, errors);

            if (targetFilePath != null && !targetFilePath.isBlank() && target != null) {
                if (!normalizePath(target).endsWith(normalizePath(targetFilePath))) {
                    warnings.add("Diff target file '%s' does not match requested target '%s'".formatted(target, targetFilePath));
                }
            }

            if (fileDiff.hunks().isEmpty()) {
                errors.add("File '%s' diff contains no hunks.".formatted(target));
            }

            int hunkAddedBraces = 0;
            int hunkDeletedBraces = 0;

            for (UnifiedDiffParser.DiffHunk hunk : fileDiff.hunks()) {
                for (UnifiedDiffParser.DiffLine line : hunk.lines()) {
                    if (line.type() == UnifiedDiffParser.LineType.ADDED) {
                        totalAdded++;
                        hunkAddedBraces += countBracesDelta(line.content());
                        checkCodeSecurity(line.content(), errors, warnings);
                    } else if (line.type() == UnifiedDiffParser.LineType.DELETED) {
                        totalDeleted++;
                        hunkDeletedBraces += countBracesDelta(line.content());
                    }
                }
            }

            if (hunkAddedBraces != hunkDeletedBraces) {
                warnings.add("File '%s' has potential unbalanced braces across diff (added: %d, deleted: %d)"
                        .formatted(target, hunkAddedBraces, hunkDeletedBraces));
            }
        }

        boolean dryRunSuccess = false;
        if (originalFileContent != null && !fileDiffs.isEmpty()) {
            PatchApplier.ApplyResult applyResult = patchApplier.apply(originalFileContent, fileDiffs.get(0));
            dryRunSuccess = applyResult.success();
            if (!dryRunSuccess) {
                errors.addAll(applyResult.errors());
            }
        }

        boolean valid = errors.isEmpty();
        return new PatchValidationResult(
                valid,
                errors,
                warnings,
                fileDiffs.size(),
                totalAdded,
                totalDeleted,
                dryRunSuccess
        );
    }

    private void validatePathSafety(String path, List<String> errors) {
        if (path == null || path.isBlank()) {
            errors.add("File diff contains an empty or undefined target path.");
            return;
        }

        String normalized = path.replace('\\', '/');

        if (normalized.contains("../") || normalized.contains("/..") || normalized.equals("..")) {
            errors.add("Security violation: Path traversal sequence '..' detected in '%s'".formatted(path));
        }

        if (normalized.startsWith("/") || normalized.matches("^[a-zA-Z]:.*")) {
            errors.add("Security violation: Absolute file paths are not permitted in patches: '%s'".formatted(path));
        }

        String[] segments = normalized.toLowerCase().split("/");
        for (String segment : segments) {
            for (String forbidden : FORBIDDEN_FILE_SEGMENTS) {
                if (segment.equalsIgnoreCase(forbidden) || segment.endsWith(forbidden)) {
                    errors.add("Security violation: Patch modifies protected or sensitive file/directory: '%s'".formatted(path));
                    return;
                }
            }
        }
    }

    private void checkCodeSecurity(String line, List<String> errors, List<String> warnings) {
        for (SecurityRule rule : SECURITY_RULES) {
            if (rule.pattern().matcher(line).find()) {
                if (rule.isError()) {
                    errors.add(rule.message());
                } else {
                    warnings.add(rule.message());
                }
            }
        }
    }

    private int countBracesDelta(String line) {
        int delta = 0;
        for (char c : line.toCharArray()) {
            if (c == '{') delta++;
            if (c == '}') delta--;
        }
        return delta;
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/').trim();
    }
}
