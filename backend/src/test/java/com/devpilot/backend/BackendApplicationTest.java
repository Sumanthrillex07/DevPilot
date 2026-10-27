package com.devpilot.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the Spring application context loads without errors.
 *
 * <p>This is a "smoke test" — if any bean has a misconfigured dependency,
 * missing required property, or circular reference, this test catches it immediately.
 *
 * <p>Uses the "test" profile which activates application-test.yml, providing:
 * - H2 in-memory database (no PostgreSQL needed)
 * - Flyway disabled (schema managed per test when needed)
 * - Mock OAuth2 client credentials
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTest {

    @Test
    void contextLoads() {
        // If this method runs without throwing, the Spring context loaded successfully.
        // Spring Boot's @SpringBootTest starts the entire application context,
        // wiring all beans and validating their dependencies.
    }
}
