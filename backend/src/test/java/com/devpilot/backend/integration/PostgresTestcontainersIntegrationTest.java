package com.devpilot.backend.integration;

import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class PostgresTestcontainersIntegrationTest {

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("devpilot_tc")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (isDockerAvailable() && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        }
    }

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private ConnectedRepositoryRepository repositoryRepository;

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("Testcontainers runs Flyway and executes CRUD against real PostgreSQL container")
    void testRealPostgreSqlWithFlyway() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(userRepository).isNotNull();

        User user = userRepository.save(User.builder()
                .githubId(99999L)
                .githubUsername("tc-user")
                .displayName("Testcontainers User")
                .accessToken("encrypted-tc-token")
                .build());

        assertThat(user.getId()).isNotNull();

        ConnectedRepository repo = repositoryRepository.save(ConnectedRepository.builder()
                .userId(user.getId())
                .githubRepoId(88888L)
                .owner("tc-user")
                .name("tc-repo")
                .fullName("tc-user/tc-repo")
                .defaultBranch("main")
                .build());

        Optional<ConnectedRepository> found = repositoryRepository.findById(repo.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("tc-user/tc-repo");
    }
}
