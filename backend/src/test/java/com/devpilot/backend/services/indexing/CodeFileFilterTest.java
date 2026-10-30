package com.devpilot.backend.services.indexing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CodeFileFilterTest {

    private CodeFileFilter filter;
    private static final long MAX_BYTES = 300_000L; // 300 KB

    @BeforeEach
    void setUp() {
        filter = new CodeFileFilter();
    }

    // --- Eligibility Tests ---

    @ParameterizedTest
    @ValueSource(strings = {
            "src/main/java/com/devpilot/App.java",
            "frontend/src/components/Header.tsx",
            "frontend/src/utils/math.ts",
            "scripts/deploy.py",
            "cmd/server/main.go",
            "src/lib.rs",
            "README.md",
            "Dockerfile",
            "docker/Dockerfile",
            "Makefile",
            "Jenkinsfile",
            "config/application.yml",
            "schema.sql",
            "styles/main.css"
    })
    @DisplayName("isEligible: valid source files return true")
    void isEligible_validFiles_returnsTrue(String path) {
        assertThat(filter.isEligible(path, 1024L, MAX_BYTES)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "node_modules/react/index.js",
            "frontend/node_modules/axios/index.js",
            ".git/config",
            "target/classes/App.class",
            "build/libs/app.jar",
            "dist/bundle.js",
            ".next/server/page.js",
            "vendor/bundle/ruby.rb",
            "__pycache__/model.cpython-310.pyc",
            ".idea/workspace.xml",
            ".vscode/settings.json",
            "coverage/lcov.info"
    })
    @DisplayName("isEligible: files in excluded directories return false")
    void isEligible_excludedDirectories_returnsFalse(String path) {
        assertThat(filter.isEligible(path, 1024L, MAX_BYTES)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "package-lock.json",
            "frontend/package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "composer.lock",
            "cargo.lock",
            "poetry.lock",
            "go.sum"
    })
    @DisplayName("isEligible: lockfiles return false")
    void isEligible_lockfiles_returnsFalse(String path) {
        assertThat(filter.isEligible(path, 5000L, MAX_BYTES)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ".env",
            ".env.local",
            ".env.production",
            "config/.env.staging",
            "secrets/id_rsa",
            "id_ed25519",
            "credentials.json",
            "service-account.json",
            "cert.pem",
            "private.key",
            "keystore.jks",
            "auth.p12"
    })
    @DisplayName("isEligible: sensitive credential and key files return false")
    void isEligible_sensitiveFiles_returnsFalse(String path) {
        assertThat(filter.isEligible(path, 1024L, MAX_BYTES)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dist/bundle.min.js",
            "static/css/app.min.css",
            "static/js/app.js.map"
    })
    @DisplayName("isEligible: minified files and source maps return false")
    void isEligible_minifiedAndMaps_returnsFalse(String path) {
        assertThat(filter.isEligible(path, 1024L, MAX_BYTES)).isFalse();
    }

    @Test
    @DisplayName("isEligible: exceeds maximum file size returns false")
    void isEligible_exceedsMaxBytes_returnsFalse() {
        assertThat(filter.isEligible("App.java", MAX_BYTES + 1, MAX_BYTES)).isFalse();
        assertThat(filter.isEligible("App.java", MAX_BYTES, MAX_BYTES)).isTrue();
    }

    @Test
    @DisplayName("isEligible: blank or null path returns false")
    void isEligible_blankOrNull_returnsFalse() {
        assertThat(filter.isEligible(null, 100L, MAX_BYTES)).isFalse();
        assertThat(filter.isEligible("   ", 100L, MAX_BYTES)).isFalse();
    }

    // --- Language Detection Tests ---

    @Test
    @DisplayName("detectLanguage: maps known extensions and filenames correctly")
    void detectLanguage_mapsCorrectly() {
        assertThat(filter.detectLanguage("App.java")).isEqualTo("java");
        assertThat(filter.detectLanguage("index.ts")).isEqualTo("typescript");
        assertThat(filter.detectLanguage("Component.tsx")).isEqualTo("typescript");
        assertThat(filter.detectLanguage("main.py")).isEqualTo("python");
        assertThat(filter.detectLanguage("server.go")).isEqualTo("go");
        assertThat(filter.detectLanguage("lib.rs")).isEqualTo("rust");
        assertThat(filter.detectLanguage("Dockerfile")).isEqualTo("dockerfile");
        assertThat(filter.detectLanguage("docker/Dockerfile")).isEqualTo("dockerfile");
        assertThat(filter.detectLanguage("Makefile")).isEqualTo("makefile");
        assertThat(filter.detectLanguage("Jenkinsfile")).isEqualTo("groovy");
        assertThat(filter.detectLanguage("schema.sql")).isEqualTo("sql");
        assertThat(filter.detectLanguage("notes.txt")).isEqualTo("txt");
        assertThat(filter.detectLanguage("unknown_file")).isEqualTo("text");
        assertThat(filter.detectLanguage(null)).isEqualTo("text");
    }
}
