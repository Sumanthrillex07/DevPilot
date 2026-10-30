package com.devpilot.backend.services.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Git unified diff text format.
 * Extracts modified files, line additions/deletions, and line number mappings.
 */
@Component
public class UnifiedDiffParser {

    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/(.*) b/(.*)$");
    private static final Pattern OLD_FILE_HEADER = Pattern.compile("^--- (?:a/)?(.*)$");
    private static final Pattern NEW_FILE_HEADER = Pattern.compile("^\\+\\+\\+ (?:b/)?(.*)$");
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    /**
     * Represents a single parsed hunk within a file diff.
     */
    public record DiffHunk(
            int oldStartLine,
            int newStartLine,
            List<DiffLine> lines
    ) {}

    /**
     * Line in a diff hunk.
     */
    public record DiffLine(
            LineType type,
            int newLineNumber,
            int oldLineNumber,
            String content
    ) {}

    public enum LineType {
        ADDED,
        DELETED,
        CONTEXT
    }

    /**
     * Represents all changes to a specific file.
     */
    public record FileDiff(
            String oldPath,
            String newPath,
            List<DiffHunk> hunks
    ) {
        public String targetPath() {
            return newPath != null && !"/dev/null".equals(newPath) ? newPath : oldPath;
        }

        public List<DiffLine> addedLines() {
            List<DiffLine> added = new ArrayList<>();
            for (DiffHunk hunk : hunks) {
                for (DiffLine line : hunk.lines()) {
                    if (line.type() == LineType.ADDED) {
                        added.add(line);
                    }
                }
            }
            return added;
        }
    }

    /**
     * Parses full unified diff text into a list of {@link FileDiff} objects.
     *
     * @param diffText Raw git diff output
     * @return List of {@link FileDiff}
     */
    public List<FileDiff> parse(String diffText) {
        if (diffText == null || diffText.isBlank()) {
            return List.of();
        }

        List<FileDiff> files = new ArrayList<>();
        String[] lines = diffText.split("\r?\n");

        String currentOldPath = null;
        String currentNewPath = null;
        List<DiffHunk> currentHunks = new ArrayList<>();
        List<DiffLine> currentHunkLines = null;
        int oldLine = 0;
        int newLine = 0;
        int hunkOldStart = 0;
        int hunkNewStart = 0;

        for (String line : lines) {
            Matcher diffMatcher = DIFF_HEADER.matcher(line);
            if (diffMatcher.find()) {
                if (currentOldPath != null || currentNewPath != null) {
                    if (currentHunkLines != null) {
                        currentHunks.add(new DiffHunk(hunkOldStart, hunkNewStart, currentHunkLines));
                        currentHunkLines = null;
                    }
                    files.add(new FileDiff(currentOldPath, currentNewPath, new ArrayList<>(currentHunks)));
                    currentHunks.clear();
                }
                currentOldPath = diffMatcher.group(1);
                currentNewPath = diffMatcher.group(2);
                continue;
            }

            if (currentHunkLines == null) {
                Matcher oldMatcher = OLD_FILE_HEADER.matcher(line);
                if (oldMatcher.find() && currentOldPath == null) {
                    currentOldPath = oldMatcher.group(1).trim();
                    continue;
                }
                Matcher newMatcher = NEW_FILE_HEADER.matcher(line);
                if (newMatcher.find()) {
                    currentNewPath = newMatcher.group(1).trim();
                    if (currentOldPath == null) {
                        currentOldPath = currentNewPath;
                    }
                    continue;
                }
            }

            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.find()) {
                if (currentHunkLines != null) {
                    currentHunks.add(new DiffHunk(hunkOldStart, hunkNewStart, currentHunkLines));
                }
                hunkOldStart = Integer.parseInt(hunkMatcher.group(1));
                hunkNewStart = Integer.parseInt(hunkMatcher.group(2));
                oldLine = hunkOldStart;
                newLine = hunkNewStart;
                currentHunkLines = new ArrayList<>();
                continue;
            }

            if (currentHunkLines != null) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    currentHunkLines.add(new DiffLine(LineType.ADDED, newLine, -1, line.substring(1)));
                    newLine++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    currentHunkLines.add(new DiffLine(LineType.DELETED, -1, oldLine, line.substring(1)));
                    oldLine++;
                } else if (line.startsWith(" ") || line.isEmpty()) {
                    String content = line.isEmpty() ? "" : line.substring(1);
                    currentHunkLines.add(new DiffLine(LineType.CONTEXT, newLine, oldLine, content));
                    oldLine++;
                    newLine++;
                }
            }
        }

        if (currentOldPath != null || currentNewPath != null) {
            if (currentHunkLines != null) {
                currentHunks.add(new DiffHunk(hunkOldStart, hunkNewStart, currentHunkLines));
            }
            files.add(new FileDiff(currentOldPath, currentNewPath, currentHunks));
        }

        return files;
    }
}
