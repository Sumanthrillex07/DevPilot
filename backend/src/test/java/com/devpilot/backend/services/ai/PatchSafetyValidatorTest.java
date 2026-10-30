package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.PatchValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatchSafetyValidatorTest {

    private final UnifiedDiffParser parser = new UnifiedDiffParser();
    private final PatchApplier applier = new PatchApplier(parser);
    private final PatchSafetyValidator validator = new PatchSafetyValidator(parser, applier);

    @Test
    @DisplayName("Valid clean diff passes safety check")
    void validDiffPasses() {
        String diff = """
                diff --git a/src/Service.java b/src/Service.java
                @@ -1,3 +1,3 @@
                 public void run() {
                -    int oldVal = 1;
                +    int newVal = 2;
                 }
                """.trim();

        PatchValidationResult result = validator.validate(diff, "src/Service.java", null);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.filesChanged()).isEqualTo(1);
        assertThat(result.linesAdded()).isEqualTo(1);
        assertThat(result.linesDeleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("Blocks path traversal attacks in diff file target")
    void blocksPathTraversal() {
        String diff = """
                diff --git a/../../etc/passwd b/../../etc/passwd
                @@ -1,1 +1,2 @@
                +root:x:0:0
                """.trim();

        PatchValidationResult result = validator.validate(diff, null, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(err -> err.contains("Path traversal sequence '..'"));
    }

    @Test
    @DisplayName("Blocks modifications to protected sensitive files like .env")
    void blocksProtectedFiles() {
        String diff = """
                diff --git a/.env b/.env
                @@ -1,1 +1,2 @@
                +SECRET_KEY=leaked
                """.trim();

        PatchValidationResult result = validator.validate(diff, null, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(err -> err.contains("protected or sensitive file"));
    }

    @Test
    @DisplayName("Flags high-risk code patterns like Runtime.exec and hardcoded tokens")
    void flagsMaliciousPatterns() {
        String diff = """
                diff --git a/src/Exploit.java b/src/Exploit.java
                @@ -1,2 +1,3 @@
                +Runtime.getRuntime().exec("calc.exe");
                +String token = "ghp_123456789012345678901234567890123456";
                 context line
                """.trim();

        PatchValidationResult result = validator.validate(diff, null, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(err -> err.contains("Runtime.getRuntime().exec()"));
        assertThat(result.errors()).anyMatch(err -> err.contains("GitHub Personal Access Token"));
    }

    @Test
    @DisplayName("Performs dry-run verification when original content is provided")
    void dryRunVerification() {
        String original = "line 1\nline 2";
        String validDiff = """
                diff --git a/file.txt b/file.txt
                @@ -1,2 +1,2 @@
                 line 1
                -line 2
                +line 2 modified
                """.trim();

        PatchValidationResult result = validator.validate(validDiff, "file.txt", original);

        assertThat(result.valid()).isTrue();
        assertThat(result.dryRunSuccess()).isTrue();
    }
}
