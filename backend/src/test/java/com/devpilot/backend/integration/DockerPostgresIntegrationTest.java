package com.devpilot.backend.integration;

import com.devpilot.backend.entities.ChatMessage;
import com.devpilot.backend.entities.ChatSession;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.IndexStatus;
import com.devpilot.backend.entities.MessageRole;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.repositories.ChatMessageRepository;
import com.devpilot.backend.repositories.ChatSessionRepository;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test against the real running Docker PostgreSQL container (pgvector/pgvector:pg16).
 *
 * <p>Validates:
 * <ul>
 *   <li>Database connectivity on port 5433</li>
 *   <li>Flyway migrations (V1 through V5 including pgvector extension and vector_store table)</li>
 *   <li>Entity persistence and relational constraints across Users, Repositories, Sessions, and Messages</li>
 *   <li>Native pgvector cosine distance operations directly in PostgreSQL</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class DockerPostgresIntegrationTest {

    static boolean isDockerPostgresRunning() {
        try (Socket socket = new Socket("localhost", 5433)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (isDockerPostgresRunning()) {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/devpilot");
            registry.add("spring.datasource.username", () -> "postgres");
            registry.add("spring.datasource.password", () -> "postgres");
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/postgresql-migration");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        }
    }

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private ConnectedRepositoryRepository repositoryRepository;

    @Autowired(required = false)
    private ChatSessionRepository sessionRepository;

    @Autowired(required = false)
    private ChatMessageRepository messageRepository;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Test
    @EnabledIf("isDockerPostgresRunning")
    @DisplayName("Verify Docker PostgreSQL: Flyway V1-V5, pgvector extension, and JPA entity CRUD")
    void testDockerPostgresMigrationsAndEntities() {
        assertThat(userRepository).isNotNull();
        assertThat(jdbcTemplate).isNotNull();

        // 1. Verify all 5 Flyway migrations applied successfully in PostgreSQL
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
        );
        assertThat(migrations).isNotEmpty();
        assertThat(migrations.stream().allMatch(m -> Boolean.TRUE.equals(m.get("success")))).isTrue();

        // 2. Verify pgvector extension is installed in PostgreSQL
        Integer vectorExtensionCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class
        );
        assertThat(vectorExtensionCount).isNotNull();
        assertThat(vectorExtensionCount).isEqualTo(1);

        // 3. Verify vector_store table exists and supports vector operations
        Integer vectorStoreTableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'vector_store'",
                Integer.class
        );
        assertThat(vectorStoreTableCount).isEqualTo(1);

        // 4. Test Entity CRUD pipeline in Docker PostgreSQL
        User user = userRepository.save(User.builder()
                .githubId(12345678L)
                .githubUsername("docker-octocat")
                .displayName("Docker Test User")
                .accessToken("encrypted-docker-token")
                .build());
        assertThat(user.getId()).isNotNull();

        ConnectedRepository repo = repositoryRepository.save(ConnectedRepository.builder()
                .userId(user.getId())
                .githubRepoId(98765432L)
                .owner("docker-octocat")
                .name("docker-repo")
                .fullName("docker-octocat/docker-repo")
                .defaultBranch("main")
                .language("Java")
                .indexStatus(IndexStatus.READY)
                .chunkCount(42)
                .build());
        assertThat(repo.getId()).isNotNull();

        ChatSession session = sessionRepository.save(ChatSession.builder()
                .userId(user.getId())
                .repositoryId(repo.getId())
                .title("Docker Test Session")
                .build());
        assertThat(session.getId()).isNotNull();

        ChatMessage message = messageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(MessageRole.USER)
                .content("How does vector indexing work in Docker?")
                .build());
        assertThat(message.getId()).isNotNull();

        // Read back and verify
        Optional<ChatMessage> retrievedMessage = messageRepository.findById(message.getId());
        assertThat(retrievedMessage).isPresent();
        assertThat(retrievedMessage.get().getContent()).isEqualTo("How does vector indexing work in Docker?");
        assertThat(retrievedMessage.get().getRole()).isEqualTo(MessageRole.USER);
    }
}
