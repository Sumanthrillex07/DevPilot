package com.devpilot.backend.services.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and extracts stack frames and error details from multi-language error logs.
 */
public class StackTraceParser {

    // Java: "at com.example.MyService.doWork(MyService.java:42)" or "at java.base/java.lang.reflect.Method.invoke(Method.java:580)"
    private static final Pattern JAVA_FRAME = Pattern.compile(
            "^\\s*at\\s+(?:[a-zA-Z0-9_.]+/)?([a-zA-Z0-9_.$]+)\\.([a-zA-Z0-9_$]+)\\((?:([a-zA-Z0-9_]+\\.java):(\\d+)|Native Method|Unknown Source)\\)"
    );

    // Python: "  File "app/service.py", line 42, in my_func"
    private static final Pattern PYTHON_FRAME = Pattern.compile(
            "^\\s*File\\s+[\"']([^\"']+)[\"'],\\s*line\\s*(\\d+)(?:,\\s*in\\s+([a-zA-Z0-9_]+))?"
    );

    // TypeScript/JS: "    at MyService.doWork (src/service.ts:42:15)" or "    at src/service.ts:42:15"
    private static final Pattern JS_FRAME = Pattern.compile(
            "^\\s*at\\s+(?:([a-zA-Z0-9_$.<>]+)\\s+\\()?(?:.+[\\\\/])?([^:()\\s]+):(\\d+)(?::\\d+)?\\)?"
    );

    // Exception header (e.g. "java.lang.NullPointerException: Cannot invoke...")
    private static final Pattern EXCEPTION_HEADER = Pattern.compile(
            "^([a-zA-Z0-9_.$]+Exception|[a-zA-Z0-9_.$]+Error):\\s*(.*)$"
    );

    /**
     * Represents a single parsed stack trace frame.
     */
    public record StackFrame(
            String file,
            int line,
            String method,
            String rawLine
    ) {}

    /**
     * Result of analyzing error logs.
     */
    public record ParsedErrorInfo(
            String errorType,
            String errorMessage,
            List<StackFrame> frames,
            StackFrame topApplicationFrame
    ) {}

    /**
     * Parses error logs and extracts structured error details and stack frames.
     *
     * @param logText Raw error output or stack trace text
     * @return {@link ParsedErrorInfo} with extracted error header and application frames
     */
    public ParsedErrorInfo parse(String logText) {
        if (logText == null || logText.isBlank()) {
            return new ParsedErrorInfo(null, null, List.of(), null);
        }

        String[] lines = logText.split("\r?\n");
        String errorType = null;
        String errorMessage = null;
        List<StackFrame> frames = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();

            if (errorType == null) {
                Matcher headerMatcher = EXCEPTION_HEADER.matcher(trimmed);
                if (headerMatcher.find()) {
                    errorType = headerMatcher.group(1);
                    errorMessage = headerMatcher.group(2);
                    continue;
                }
            }

            // Check Java frame
            Matcher javaMatcher = JAVA_FRAME.matcher(line);
            if (javaMatcher.find()) {
                String fileName = javaMatcher.group(3);
                String lineStr = javaMatcher.group(4);
                String method = javaMatcher.group(1) + "." + javaMatcher.group(2);
                int lineNum = lineStr != null ? Integer.parseInt(lineStr) : 0;
                frames.add(new StackFrame(fileName, lineNum, method, line));
                continue;
            }

            // Check Python frame
            Matcher pyMatcher = PYTHON_FRAME.matcher(line);
            if (pyMatcher.find()) {
                String file = pyMatcher.group(1);
                int lineNum = Integer.parseInt(pyMatcher.group(2));
                String method = pyMatcher.group(3);
                frames.add(new StackFrame(file, lineNum, method, line));
                continue;
            }

            // Check JavaScript / TypeScript frame
            Matcher jsMatcher = JS_FRAME.matcher(line);
            if (jsMatcher.find()) {
                String method = jsMatcher.group(1);
                String file = jsMatcher.group(2);
                int lineNum = Integer.parseInt(jsMatcher.group(3));
                frames.add(new StackFrame(file, lineNum, method, line));
            }
        }

        // Identify the top non-framework frame (filters out standard JDK, Tomcat, Spring, Node internal frames)
        StackFrame topAppFrame = frames.stream()
                .filter(this::isLikelyApplicationCode)
                .findFirst()
                .orElse(frames.isEmpty() ? null : frames.get(0));

        return new ParsedErrorInfo(errorType, errorMessage, frames, topAppFrame);
    }

    private boolean isLikelyApplicationCode(StackFrame frame) {
        if (frame.file() == null) return false;
        String lowerFile = frame.file().toLowerCase();
        String lowerMethod = frame.method() != null ? frame.method().toLowerCase() : "";

        return !lowerMethod.startsWith("java.")
                && !lowerMethod.startsWith("javax.")
                && !lowerMethod.startsWith("org.springframework.")
                && !lowerMethod.startsWith("org.apache.tomcat.")
                && !lowerMethod.startsWith("org.junit.")
                && !lowerMethod.startsWith("jdk.internal.")
                && !lowerFile.startsWith("flask/")
                && !lowerFile.startsWith("django/")
                && !lowerFile.contains("node_modules")
                && !lowerFile.contains("site-packages");
    }
}
