package com.devpilot.backend.services.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory patch application engine.
 * Applies a parsed {@link UnifiedDiffParser.FileDiff} against original file content,
 * verifying line alignment, deletions, and context lines without touching the local disk.
 */
@Component
public class PatchApplier {

    private final UnifiedDiffParser diffParser;

    public PatchApplier(UnifiedDiffParser diffParser) {
        this.diffParser = diffParser;
    }

    public record ApplyResult(
            boolean success,
            String patchedContent,
            List<String> errors,
            int linesAdded,
            int linesDeleted
    ) {
        public static ApplyResult failure(List<String> errors) {
            return new ApplyResult(false, null, errors, 0, 0);
        }

        public static ApplyResult success(String patchedContent, int linesAdded, int linesDeleted) {
            return new ApplyResult(true, patchedContent, List.of(), linesAdded, linesDeleted);
        }
    }

    /**
     * Applies a raw unified diff against the provided original content.
     */
    public ApplyResult apply(String originalContent, String diffText) {
        List<UnifiedDiffParser.FileDiff> fileDiffs = diffParser.parse(diffText);
        if (fileDiffs.isEmpty()) {
            return ApplyResult.failure(List.of("Diff does not contain any valid file changes."));
        }
        return apply(originalContent, fileDiffs.get(0));
    }

    /**
     * Applies a parsed {@link UnifiedDiffParser.FileDiff} against original content.
     */
    public ApplyResult apply(String originalContent, UnifiedDiffParser.FileDiff fileDiff) {
        if (fileDiff == null || fileDiff.hunks().isEmpty()) {
            return ApplyResult.failure(List.of("File diff contains no hunks to apply."));
        }

        String[] origLines = (originalContent != null && !originalContent.isEmpty())
                ? originalContent.split("\r?\n", -1)
                : new String[0];

        List<String> outLines = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int origIndex = 0;
        int totalAdded = 0;
        int totalDeleted = 0;

        for (UnifiedDiffParser.DiffHunk hunk : fileDiff.hunks()) {
            int targetStart = Math.max(0, hunk.oldStartLine() - 1);

            // Copy untouched lines preceding the current hunk
            while (origIndex < targetStart && origIndex < origLines.length) {
                outLines.add(origLines[origIndex]);
                origIndex++;
            }

            for (UnifiedDiffParser.DiffLine diffLine : hunk.lines()) {
                switch (diffLine.type()) {
                    case CONTEXT -> {
                        if (origIndex >= origLines.length) {
                            errors.add("Context line past EOF at line %d: '%s'".formatted(origIndex + 1, diffLine.content()));
                        } else if (!linesMatch(origLines[origIndex], diffLine.content())) {
                            errors.add("Context line mismatch at line %d: expected '%s', found '%s'".formatted(
                                    origIndex + 1, diffLine.content(), origLines[origIndex]));
                            outLines.add(origLines[origIndex]);
                            origIndex++;
                        } else {
                            outLines.add(origLines[origIndex]);
                            origIndex++;
                        }
                    }
                    case DELETED -> {
                        totalDeleted++;
                        if (origIndex >= origLines.length) {
                            errors.add("Deleted line past EOF at line %d: '%s'".formatted(origIndex + 1, diffLine.content()));
                        } else if (!linesMatch(origLines[origIndex], diffLine.content())) {
                            errors.add("Deleted line mismatch at line %d: expected '%s', found '%s'".formatted(
                                    origIndex + 1, diffLine.content(), origLines[origIndex]));
                            origIndex++;
                        } else {
                            // Line matched and deleted: advance without copying
                            origIndex++;
                        }
                    }
                    case ADDED -> {
                        totalAdded++;
                        outLines.add(diffLine.content());
                    }
                }
            }
        }

        // Copy remaining untouched lines after the last hunk
        while (origIndex < origLines.length) {
            outLines.add(origLines[origIndex]);
            origIndex++;
        }

        if (!errors.isEmpty()) {
            return ApplyResult.failure(errors);
        }

        String patchedText = String.join("\n", outLines);
        return ApplyResult.success(patchedText, totalAdded, totalDeleted);
    }

    private boolean linesMatch(String original, String expected) {
        if (original.equals(expected)) {
            return true;
        }
        // Allow whitespace trimming comparison for minor indentation differences
        return original.trim().equals(expected.trim());
    }
}
