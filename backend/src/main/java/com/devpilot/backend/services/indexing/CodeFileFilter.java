package com.devpilot.backend.services.indexing;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Filter that determines whether a repository file should be indexed for RAG.
 *
 * <p><b>Design Considerations:</b>
 * <ul>
 *   <li><b>Noise Reduction:</b> Build outputs ({@code target/}, {@code dist/}), dependency
 *       directories ({@code node_modules/}, {@code vendor/}), and lockfiles contain massive
 *       uninformative text that wastes vector tokens and pollutes search results.</li>
 *   <li><b>Security & Secret Sanitisation:</b> {@code .env} files, private keys ({@code .pem},
 *       {@code id_rsa}), and credential vaults are explicitly blocked so secrets are never
 *       sent to external LLM providers or stored in vector embeddings.</li>
 *   <li><b>Binary & Asset Exclusion:</b> Media, compiled binaries, and archives cannot be
 *       embedded as source code.</li>
 *   <li><b>Size Ceiling:</b> Giant generated bundles or minified files over the byte threshold
 *       are skipped to protect memory and token limits.</li>
 * </ul>
 */
@Component
public class CodeFileFilter {

    private static final Set<String> SKIP_DIR_PARTS = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            "target",
            ".next",
            "vendor",
            "__pycache__",
            ".idea",
            ".vscode",
            "coverage",
            "out",
            ".gradle",
            ".mvn",
            "bin",
            ".turbo",
            ".nuxt",
            ".serverless",
            "venv",
            ".venv"
    );

    private static final Set<String> SKIP_FILENAMES = Set.of(
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "composer.lock",
            "cargo.lock",
            "poetry.lock",
            "go.sum",
            "mix.lock"
    );

    private static final Set<String> SENSITIVE_FILENAMES = Set.of(
            "id_rsa",
            "id_dsa",
            "id_ecdsa",
            "id_ed25519",
            "credentials.json",
            "service-account.json",
            "secrets.yaml",
            "secrets.yml",
            "keystore.jks"
    );

    private static final Set<String> SENSITIVE_EXTENSIONS = Set.of(
            "pem",
            "key",
            "pkcs12",
            "p12",
            "pfx",
            "keystore",
            "jks"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // JVM
            "java", "kt", "kts", "scala", "groovy",
            // Web / Frontend
            "ts", "tsx", "js", "jsx", "mjs", "cjs", "html", "css", "scss", "sass", "less", "vue", "svelte",
            // Systems & General Purpose
            "py", "go", "rs", "rb", "php", "c", "h", "cpp", "hpp", "cc", "cs", "swift", "m", "mm",
            // Shell & Scripts
            "sh", "bash", "zsh",
            // Docs & Config
            "md", "mdx", "txt", "rst", "adoc",
            "yml", "yaml", "json", "toml", "xml", "properties", "gradle", "sql"
    );

    private static final Map<String, String> EXTENSION_LANGUAGE_MAP = Map.ofEntries(
            Map.entry("java", "java"),
            Map.entry("kt", "kotlin"),
            Map.entry("kts", "kotlin"),
            Map.entry("scala", "scala"),
            Map.entry("groovy", "groovy"),
            Map.entry("ts", "typescript"),
            Map.entry("tsx", "typescript"),
            Map.entry("js", "javascript"),
            Map.entry("jsx", "javascript"),
            Map.entry("mjs", "javascript"),
            Map.entry("cjs", "javascript"),
            Map.entry("py", "python"),
            Map.entry("go", "go"),
            Map.entry("rs", "rust"),
            Map.entry("rb", "ruby"),
            Map.entry("php", "php"),
            Map.entry("cs", "csharp"),
            Map.entry("cpp", "cpp"),
            Map.entry("hpp", "cpp"),
            Map.entry("cc", "cpp"),
            Map.entry("c", "c"),
            Map.entry("h", "c"),
            Map.entry("swift", "swift"),
            Map.entry("sh", "shell"),
            Map.entry("bash", "shell"),
            Map.entry("zsh", "shell"),
            Map.entry("md", "markdown"),
            Map.entry("mdx", "markdown"),
            Map.entry("yml", "yaml"),
            Map.entry("yaml", "yaml"),
            Map.entry("json", "json"),
            Map.entry("sql", "sql"),
            Map.entry("html", "html"),
            Map.entry("css", "css"),
            Map.entry("scss", "scss"),
            Map.entry("xml", "xml"),
            Map.entry("toml", "toml")
    );

    /**
     * Checks if a file path in a repository is eligible for indexing.
     *
     * @param path         Relative path in repository (e.g. "backend/src/main/App.java")
     * @param sizeBytes    File size in bytes
     * @param maxFileBytes Maximum allowed file size in bytes
     * @return true if the file should be chunked and indexed; false otherwise
     */
    public boolean isEligible(String path, long sizeBytes, long maxFileBytes) {
        if (path == null || path.isBlank()) {
            return false;
        }

        String normalized = path.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);

        // 1. Directory part check (reject build artifacts, node_modules, .git, etc.)
        for (String part : lower.split("/")) {
            if (SKIP_DIR_PARTS.contains(part)) {
                return false;
            }
        }

        String fileName = lower.substring(lower.lastIndexOf('/') + 1);

        // 2. Reject empty file names
        if (fileName.isBlank()) {
            return false;
        }

        // 3. Security check: exclude .env variants (e.g. .env, .env.local, .env.prod)
        if (fileName.equals(".env") || fileName.startsWith(".env.")) {
            return false;
        }

        // 4. Security check: exclude sensitive files by exact name (e.g. id_rsa, credentials.json)
        if (SENSITIVE_FILENAMES.contains(fileName)) {
            return false;
        }

        // 5. Exclude lockfiles
        if (SKIP_FILENAMES.contains(fileName)) {
            return false;
        }

        // 6. Exclude minified and source map files
        if (fileName.endsWith(".min.js") || fileName.endsWith(".min.css") || fileName.endsWith(".map")) {
            return false;
        }

        // 7. Exclude hidden files starting with '.' (unless specifically allowed)
        if (fileName.startsWith(".")) {
            return false;
        }

        // 8. Size ceiling check
        if (sizeBytes > maxFileBytes) {
            return false;
        }

        // 9. Exact build/docker filenames without extensions
        if ("dockerfile".equals(fileName) || "makefile".equals(fileName) || "jenkinsfile".equals(fileName)) {
            return true;
        }

        // 10. Extension extraction and verification
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }

        String ext = fileName.substring(dot + 1);

        // Security check: reject private keys and certificates
        if (SENSITIVE_EXTENSIONS.contains(ext)) {
            return false;
        }

        return ALLOWED_EXTENSIONS.contains(ext);
    }

    /**
     * Detects the canonical language identifier for syntax highlighting and prompt context.
     *
     * @param path File path
     * @return Canonical language string (e.g. "java", "typescript", "python", or "text")
     */
    public String detectLanguage(String path) {
        if (path == null || path.isBlank()) {
            return "text";
        }

        String lower = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);

        if ("dockerfile".equals(fileName)) {
            return "dockerfile";
        }
        if ("makefile".equals(fileName)) {
            return "makefile";
        }
        if ("jenkinsfile".equals(fileName)) {
            return "groovy";
        }

        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "text";
        }

        String ext = fileName.substring(dot + 1);
        return EXTENSION_LANGUAGE_MAP.getOrDefault(ext, ext);
    }
}
