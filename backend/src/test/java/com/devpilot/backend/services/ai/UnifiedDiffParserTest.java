package com.devpilot.backend.services.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDiffParserTest {

    private final UnifiedDiffParser parser = new UnifiedDiffParser();

    @Test
    @DisplayName("Parses standard Git unified diff with added and deleted lines")
    void parseUnifiedDiff() {
        String diff = """
                diff --git a/src/Auth.java b/src/Auth.java
                index 83a21..91f32 100644
                --- a/src/Auth.java
                +++ b/src/Auth.java
                @@ -10,6 +10,8 @@ package com.devpilot;
                 public class Auth {
                -    private String token;
                +    private final String token;
                +    private final String secret;
                     public void login() {
                     }
                 }
                """;

        List<UnifiedDiffParser.FileDiff> files = parser.parse(diff);

        assertThat(files).hasSize(1);
        UnifiedDiffParser.FileDiff file = files.get(0);
        assertThat(file.targetPath()).isEqualTo("src/Auth.java");
        assertThat(file.hunks()).hasSize(1);

        UnifiedDiffParser.DiffHunk hunk = file.hunks().get(0);
        assertThat(hunk.oldStartLine()).isEqualTo(10);
        assertThat(hunk.newStartLine()).isEqualTo(10);

        List<UnifiedDiffParser.DiffLine> added = file.addedLines();
        assertThat(added).hasSize(2);
        assertThat(added.get(0).content()).isEqualTo("    private final String token;");
        assertThat(added.get(0).newLineNumber()).isEqualTo(11);
        assertThat(added.get(1).content()).isEqualTo("    private final String secret;");
        assertThat(added.get(1).newLineNumber()).isEqualTo(12);
    }

    @Test
    @DisplayName("Parses multi-file diff cleanly")
    void parseMultiFileDiff() {
        String diff = """
                diff --git a/file1.txt b/file1.txt
                @@ -1,2 +1,2 @@
                -old
                +new
                diff --git a/file2.txt b/file2.txt
                @@ -1,2 +1,3 @@
                +added line
                 context
                """;

        List<UnifiedDiffParser.FileDiff> files = parser.parse(diff);

        assertThat(files).hasSize(2);
        assertThat(files.get(0).targetPath()).isEqualTo("file1.txt");
        assertThat(files.get(1).targetPath()).isEqualTo("file2.txt");
    }

    @Test
    @DisplayName("Handles null or empty diff strings")
    void parseEmpty() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
    }
}
