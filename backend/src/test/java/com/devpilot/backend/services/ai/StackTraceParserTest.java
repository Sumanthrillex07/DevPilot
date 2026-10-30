package com.devpilot.backend.services.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StackTraceParserTest {

    private final StackTraceParser parser = new StackTraceParser();

    @Test
    @DisplayName("Parses Java exception and finds top application stack frame")
    void parseJavaStackTrace() {
        String javaLog = """
                java.lang.NullPointerException: Cannot invoke "String.length()" because "name" is null
                \tat org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
                \tat com.devpilot.backend.services.UserService.updateProfile(UserService.java:85)
                \tat com.devpilot.backend.controllers.UserController.update(UserController.java:34)
                \tat java.base/java.lang.reflect.Method.invoke(Method.java:580)
                """;

        var result = parser.parse(javaLog);

        assertThat(result.errorType()).isEqualTo("java.lang.NullPointerException");
        assertThat(result.errorMessage()).contains("Cannot invoke \"String.length()\"");
        assertThat(result.frames()).hasSize(4);

        // Filters out org.springframework and java.base framework frames
        assertThat(result.topApplicationFrame()).isNotNull();
        assertThat(result.topApplicationFrame().file()).isEqualTo("UserService.java");
        assertThat(result.topApplicationFrame().line()).isEqualTo(85);
        assertThat(result.topApplicationFrame().method()).contains("UserService.updateProfile");
    }

    @Test
    @DisplayName("Parses Python traceback and extracts line numbers")
    void parsePythonTraceback() {
        String pyLog = """
                Traceback (most recent call last):
                  File "flask/app.py", line 1234, in wsgi_app
                  File "app/routes.py", line 42, in get_user
                    user = db.query(User).filter_by(id=uid).one()
                ZeroDivisionError: division by zero
                """;

        var result = parser.parse(pyLog);

        assertThat(result.frames()).hasSize(2);
        assertThat(result.topApplicationFrame()).isNotNull();
        assertThat(result.topApplicationFrame().file()).isEqualTo("app/routes.py");
        assertThat(result.topApplicationFrame().line()).isEqualTo(42);
        assertThat(result.topApplicationFrame().method()).isEqualTo("get_user");
    }

    @Test
    @DisplayName("Parses JavaScript / TypeScript stack trace")
    void parseJsStackTrace() {
        String jsLog = """
                TypeError: Cannot read properties of undefined (reading 'token')
                    at AuthService.verifyToken (src/auth/service.ts:55:12)
                    at processTicksAndRejections (node:internal/process/task_queues:95:5)
                """;

        var result = parser.parse(jsLog);

        assertThat(result.frames()).isNotEmpty();
        assertThat(result.topApplicationFrame()).isNotNull();
        assertThat(result.topApplicationFrame().file()).isEqualTo("service.ts");
        assertThat(result.topApplicationFrame().line()).isEqualTo(55);
        assertThat(result.topApplicationFrame().method()).isEqualTo("AuthService.verifyToken");
    }

    @Test
    @DisplayName("Handles null or empty logs gracefully")
    void parseEmpty() {
        var result = parser.parse("");
        assertThat(result.frames()).isEmpty();
        assertThat(result.topApplicationFrame()).isNull();
    }
}
