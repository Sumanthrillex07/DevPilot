package com.devpilot.backend.services.indexing;

import com.devpilot.backend.services.ai.RagSettings;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Line-aware source code chunker designed for Code RAG and line-level citations.
 *
 * <p><b>Why not pure TokenTextSplitter?</b>
 * <br>Standard token splitters chop text strictly by token count, frequently slicing in the
 * middle of code lines, statements, or variable names. Crucially, they do not track 1-indexed
 * source line numbers. DevPilot requires line numbers to produce clickable citations and
 * GitHub deep-links (e.g., {@code [UserService.java:42-88]}).
 *
 * <p><b>Chunking Strategy:</b>
 * <ul>
 *   <li>Line-boundary preservation: Splits occur only on line endings.</li>
 *   <li>Contextual Header: Every chunk begins with {@code // File: <path> (lines <start>-<end>)}.</li>
 *   <li>Configurable Window & Overlap: Groups lines up to {@code chunkSizeTokens} with a sliding
 *       overlap of {@code chunkOverlapTokens} so functions spanning chunk boundaries retain context.</li>
 *   <li>Deterministic Content Hash: Calculates SHA-256 for chunk deduplication and delta indexing.</li>
 * </ul>
 */
@Component
public class CodeChunker {

    private final int chunkSizeTokens;
    private final int chunkOverlapTokens;
    private final CodeFileFilter fileFilter;

    public CodeChunker(
            @Value("${devpilot.indexing.chunk-size-tokens:" + RagSettings.DEFAULT_CHUNK_SIZE_TOKENS + "}") int chunkSizeTokens,
            @Value("${devpilot.indexing.chunk-overlap-tokens:" + RagSettings.DEFAULT_CHUNK_OVERLAP_TOKENS + "}") int chunkOverlapTokens,
            CodeFileFilter fileFilter) {
        this.chunkSizeTokens = Math.max(100, chunkSizeTokens);
        this.chunkOverlapTokens = Math.max(0, Math.min(chunkOverlapTokens, this.chunkSizeTokens / 2));
        this.fileFilter = fileFilter;
    }

    /**
     * Splits source code into line-aware {@link CodeChunk}s.
     *
     * @param repoId   Repository UUID string
     * @param filePath Relative path of the file
     * @param content  Raw UTF-8 file content
     * @return List of {@link CodeChunk}s with line numbers and content hashes
     */
    public List<CodeChunk> chunkFile(String repoId, String filePath, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String language = fileFilter.detectLanguage(filePath);
        String fileName = extractFileName(filePath);

        // Normalize Windows/Mac line endings to \n
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int totalLines = lines.length;

        List<CodeChunk> chunks = new ArrayList<>();
        int currentLineIdx = 0; // 0-indexed in array, (currentLineIdx + 1) in 1-indexed lines
        int chunkIndex = 0;

        while (currentLineIdx < totalLines) {
            int startLine = currentLineIdx + 1; // 1-indexed
            StringBuilder bodyBuilder = new StringBuilder();
            int accumulatedChars = 0;
            int lastLineIdx = currentLineIdx;

            while (lastLineIdx < totalLines) {
                String line = lines[lastLineIdx];
                int lineChars = line.length() + 1; // +1 for newline

                // If adding this line would exceed chunk budget and we already have lines, break
                if (bodyBuilder.length() > 0 && estimateTokens(accumulatedChars + lineChars) > chunkSizeTokens) {
                    break;
                }

                bodyBuilder.append(line).append('\n');
                accumulatedChars += lineChars;
                lastLineIdx++;
            }

            int endLine = Math.max(startLine, lastLineIdx); // 1-indexed

            // Prepend contextual file header for vector embedding and LLM prompt context
            String header = "// File: " + filePath + " (lines " + startLine + "-" + endLine + ")\n";
            String fullChunkText = header + bodyBuilder;

            String hash = sha256(fullChunkText);

            chunks.add(new CodeChunk(
                    repoId,
                    filePath,
                    fileName,
                    language,
                    startLine,
                    endLine,
                    chunkIndex++,
                    hash,
                    fullChunkText
            ));

            if (lastLineIdx >= totalLines) {
                break; // Reached end of file
            }

            // Calculate overlap by stepping back lines that fit within chunkOverlapTokens
            int nextStartIdx = lastLineIdx;
            int overlapChars = 0;
            while (nextStartIdx > currentLineIdx + 1) {
                int lineChars = lines[nextStartIdx - 1].length() + 1;
                if (estimateTokens(overlapChars + lineChars) > chunkOverlapTokens) {
                    break;
                }
                overlapChars += lineChars;
                nextStartIdx--;
            }

            // Guarantee strictly forward progress to avoid infinite loops
            currentLineIdx = Math.max(currentLineIdx + 1, nextStartIdx);
        }

        return chunks;
    }

    /**
     * Helper that chunks a file directly into Spring AI {@link Document}s ready for pgvector insertion.
     */
    public List<Document> chunkFileToDocuments(String repoId, String filePath, String content) {
        return chunkFile(repoId, filePath, content).stream()
                .map(CodeChunk::toDocument)
                .toList();
    }

    /**
     * Code token estimation: code typically averages ~3.5 to 4 characters per token.
     */
    private static int estimateTokens(int charCount) {
        return Math.max(1, charCount / 4);
    }

    private static String extractFileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
