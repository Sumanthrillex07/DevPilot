package com.devpilot.backend.services.indexing;

import com.devpilot.backend.services.ai.RagSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChunkerTest {

    private CodeChunker chunker;
    private CodeFileFilter fileFilter;

    @BeforeEach
    void setUp() {
        fileFilter = new CodeFileFilter();
        // Use 100 token chunk size and 20 token overlap for clear, predictable unit testing
        chunker = new CodeChunker(100, 20, fileFilter);
    }

    @Test
    @DisplayName("chunkFile: blank or null content returns empty list")
    void chunkFile_blankOrNull_returnsEmptyList() {
        assertThat(chunker.chunkFile("repo-1", "App.java", null)).isEmpty();
        assertThat(chunker.chunkFile("repo-1", "App.java", "   ")).isEmpty();
    }

    @Test
    @DisplayName("chunkFile: small file produces exactly one chunk with complete line bounds")
    void chunkFile_smallFile_singleChunkWithCorrectBounds() {
        String code = """
                package com.example;

                public class Calculator {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """;

        List<CodeChunk> chunks = chunker.chunkFile("repo-123", "src/Calculator.java", code);

        assertThat(chunks).hasSize(1);
        CodeChunk chunk = chunks.get(0);

        assertThat(chunk.repoId()).isEqualTo("repo-123");
        assertThat(chunk.filePath()).isEqualTo("src/Calculator.java");
        assertThat(chunk.fileName()).isEqualTo("Calculator.java");
        assertThat(chunk.language()).isEqualTo("java");
        assertThat(chunk.startLine()).isEqualTo(1);
        // Verify line count matches
        assertThat(chunk.endLine()).isGreaterThanOrEqualTo(7);
        assertThat(chunk.chunkIndex()).isEqualTo(0);
        assertThat(chunk.contentHash()).isNotBlank();
        assertThat(chunk.text()).startsWith("// File: src/Calculator.java (lines 1-");
        assertThat(chunk.text()).contains("public int add(int a, int b)");
    }

    @Test
    @DisplayName("chunkFile: multi-chunk file creates overlapping chunks with sequential indices")
    void chunkFile_largeFile_createsOverlappingChunks() {
        // Generate a 100-line Java file that forces multi-chunking
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            sb.append("    public void method").append(i).append("() { System.out.println(\"line ").append(i).append("\"); }\n");
        }
        String largeCode = sb.toString();

        List<CodeChunk> chunks = chunker.chunkFile("repo-abc", "LargeService.java", largeCode);

        assertThat(chunks.size()).isGreaterThan(1);

        for (int i = 0; i < chunks.size(); i++) {
            CodeChunk current = chunks.get(i);
            assertThat(current.chunkIndex()).isEqualTo(i);
            assertThat(current.startLine()).isLessThanOrEqualTo(current.endLine());

            if (i > 0) {
                CodeChunk previous = chunks.get(i - 1);
                // Due to overlap, current.startLine should be less than or equal to previous.endLine
                assertThat(current.startLine()).isLessThanOrEqualTo(previous.endLine());
                // But strictly forward progress must be maintained
                assertThat(current.startLine()).isGreaterThan(previous.startLine());
            }
        }
    }

    @Test
    @DisplayName("chunkFile: content hash is deterministic and changes when content changes")
    void chunkFile_contentHash_deterministic() {
        String code1 = "int x = 42;\n";
        String code2 = "int x = 43;\n";

        CodeChunk chunkA1 = chunker.chunkFile("r1", "test.java", code1).get(0);
        CodeChunk chunkA2 = chunker.chunkFile("r1", "test.java", code1).get(0);
        CodeChunk chunkB = chunker.chunkFile("r1", "test.java", code2).get(0);

        assertThat(chunkA1.contentHash()).isEqualTo(chunkA2.contentHash());
        assertThat(chunkA1.contentHash()).isNotEqualTo(chunkB.contentHash());
    }

    @Test
    @DisplayName("chunkFileToDocuments: correctly maps CodeChunk fields to Spring AI Document metadata")
    void chunkFileToDocuments_mapsToSpringAiDocument() {
        String code = "console.log('hello world');\n";

        List<Document> docs = chunker.chunkFileToDocuments("repo-999", "index.ts", code);

        assertThat(docs).hasSize(1);
        Document doc = docs.get(0);

        assertThat(doc.getText()).contains("hello world");
        assertThat(doc.getMetadata()).containsEntry(RagSettings.METADATA_REPO_ID, "repo-999");
        assertThat(doc.getMetadata()).containsEntry(RagSettings.METADATA_FILE_PATH, "index.ts");
        assertThat(doc.getMetadata()).containsEntry(RagSettings.METADATA_FILE_NAME, "index.ts");
        assertThat(doc.getMetadata()).containsEntry(RagSettings.METADATA_LANGUAGE, "typescript");
        assertThat(doc.getMetadata()).containsEntry(RagSettings.METADATA_START_LINE, 1);
        assertThat(doc.getMetadata()).containsEntry(RagSettings.METADATA_CHUNK_INDEX, 0);
        assertThat(doc.getMetadata()).containsKey(RagSettings.METADATA_CONTENT_HASH);
    }
}
