package com.devpilot.backend.services.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatchApplierTest {

    private final UnifiedDiffParser parser = new UnifiedDiffParser();
    private final PatchApplier applier = new PatchApplier(parser);

    @Test
    @DisplayName("Successfully applies replacement patch to original content")
    void applyReplacementPatch() {
        String original = """
                public class Calculator {
                    public int add(int a, int b) {
                        return a - b;
                    }
                }
                """.trim();

        String diff = """
                diff --git a/Calculator.java b/Calculator.java
                --- a/Calculator.java
                +++ b/Calculator.java
                @@ -1,5 +1,5 @@
                 public class Calculator {
                     public int add(int a, int b) {
                -        return a - b;
                +        return a + b;
                     }
                 }
                """.trim();

        PatchApplier.ApplyResult result = applier.apply(original, diff);

        assertThat(result.success()).isTrue();
        assertThat(result.linesAdded()).isEqualTo(1);
        assertThat(result.linesDeleted()).isEqualTo(1);
        assertThat(result.patchedContent()).contains("return a + b;");
        assertThat(result.patchedContent()).doesNotContain("return a - b;");
    }

    @Test
    @DisplayName("Successfully applies insertions and deletions across multiple hunks")
    void applyMultiHunkPatch() {
        String original = """
                line 1
                line 2
                line 3
                line 4
                line 5
                line 6
                line 7
                line 8
                """.trim();

        String diff = """
                diff --git a/test.txt b/test.txt
                @@ -2,2 +2,3 @@
                 line 2
                +line 2.5
                 line 3
                @@ -6,2 +7,1 @@
                 line 6
                -line 7
                """.trim();

        PatchApplier.ApplyResult result = applier.apply(original, diff);

        assertThat(result.success()).isTrue();
        assertThat(result.linesAdded()).isEqualTo(1);
        assertThat(result.linesDeleted()).isEqualTo(1);
        assertThat(result.patchedContent()).contains("line 2.5");
        assertThat(result.patchedContent()).doesNotContain("line 7");
        assertThat(result.patchedContent()).contains("line 8");
    }

    @Test
    @DisplayName("Detects context line mismatch and reports descriptive failure")
    void reportsContextMismatch() {
        String original = """
                int x = 10;
                int y = 20;
                """.trim();

        String diff = """
                diff --git a/test.txt b/test.txt
                @@ -1,2 +1,2 @@
                 int differentLine = 100;
                -int y = 20;
                +int y = 30;
                """.trim();

        PatchApplier.ApplyResult result = applier.apply(original, diff);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().get(0)).contains("Context line mismatch");
    }
}
