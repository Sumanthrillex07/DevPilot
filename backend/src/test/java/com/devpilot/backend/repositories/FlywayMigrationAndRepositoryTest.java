package com.devpilot.backend.repositories;

import com.devpilot.backend.entities.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationAndRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConnectedRepositoryRepository repositoryRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Flyway migrations apply and schema matches JPA entities (ddl-auto: validate)")
    void testFlywaySchemaMatchesEntities() {
        // If this test boots up, Flyway migrations V1-V4 ran successfully
        // and Hibernate validated that all JPA entities match the generated tables.
        assertThat(userRepository.count()).isZero();
        assertThat(repositoryRepository.count()).isZero();
        assertThat(chatSessionRepository.count()).isZero();
        assertThat(chatMessageRepository.count()).isZero();
    }

    @Test
    @DisplayName("User CRUD and queries by githubId and username")
    void testUserCrudAndQueries() {
        User user = User.builder()
                .githubId(123456L)
                .githubUsername("octocat")
                .displayName("The Octocat")
                .avatarUrl("https://avatars.githubusercontent.com/u/123456")
                .accessToken("encrypted-token-xyz")
                .tokenScopes("read:user,repo")
                .build();

        User saved = userRepository.saveAndFlush(user);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<User> byGithubId = userRepository.findByGithubId(123456L);
        assertThat(byGithubId).isPresent();
        assertThat(byGithubId.get().getGithubUsername()).isEqualTo("octocat");

        Optional<User> byUsername = userRepository.findByGithubUsername("octocat");
        assertThat(byUsername).isPresent();
        assertThat(byUsername.get().getDisplayName()).isEqualTo("The Octocat");
    }

    @Test
    @DisplayName("Unique constraint on user github_id prevents duplicate users")
    void testUserGithubIdUniqueConstraint() {
        User user1 = User.builder()
                .githubId(999L)
                .githubUsername("user1")
                .displayName("User One")
                .accessToken("token1")
                .build();
        userRepository.saveAndFlush(user1);

        User user2 = User.builder()
                .githubId(999L) // duplicate githubId
                .githubUsername("user2")
                .displayName("User Two")
                .accessToken("token2")
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ConnectedRepository CRUD, ordering, and ownership queries")
    void testConnectedRepositoryQueries() {
        User user = userRepository.saveAndFlush(User.builder()
                .githubId(101L)
                .githubUsername("dev")
                .displayName("Dev User")
                .accessToken("token")
                .build());

        ConnectedRepository repoB = repositoryRepository.saveAndFlush(ConnectedRepository.builder()
                .userId(user.getId())
                .githubRepoId(2002L)
                .owner("dev")
                .name("repo-beta")
                .fullName("dev/repo-beta")
                .defaultBranch("main")
                .indexStatus(IndexStatus.PENDING)
                .build());

        ConnectedRepository repoA = repositoryRepository.saveAndFlush(ConnectedRepository.builder()
                .userId(user.getId())
                .githubRepoId(2001L)
                .owner("dev")
                .name("repo-alpha")
                .fullName("dev/repo-alpha")
                .defaultBranch("main")
                .indexStatus(IndexStatus.READY)
                .build());

        // Test ordering by full_name ASC
        List<ConnectedRepository> repos = repositoryRepository.findByUserIdOrderByFullNameAsc(user.getId());
        assertThat(repos).hasSize(2);
        assertThat(repos.get(0).getFullName()).isEqualTo("dev/repo-alpha");
        assertThat(repos.get(1).getFullName()).isEqualTo("dev/repo-beta");

        // Test findByIdAndUserId
        Optional<ConnectedRepository> owned = repositoryRepository.findByIdAndUserId(repoA.getId(), user.getId());
        assertThat(owned).isPresent();

        Optional<ConnectedRepository> wrongUser = repositoryRepository.findByIdAndUserId(repoA.getId(), UUID.randomUUID());
        assertThat(wrongUser).isEmpty();

        // Test findByUserIdAndGithubRepoId
        Optional<ConnectedRepository> byGh = repositoryRepository.findByUserIdAndGithubRepoId(user.getId(), 2001L);
        assertThat(byGh).isPresent();
        assertThat(byGh.get().getName()).isEqualTo("repo-alpha");
    }

    @Test
    @DisplayName("Unique constraint on (user_id, github_repo_id) in repositories")
    void testRepositoryUniqueConstraint() {
        User user = userRepository.saveAndFlush(User.builder()
                .githubId(202L)
                .githubUsername("tester")
                .displayName("Tester")
                .accessToken("token")
                .build());

        repositoryRepository.saveAndFlush(ConnectedRepository.builder()
                .userId(user.getId())
                .githubRepoId(5001L)
                .owner("tester")
                .name("same-repo")
                .fullName("tester/same-repo")
                .defaultBranch("main")
                .build());

        ConnectedRepository duplicate = ConnectedRepository.builder()
                .userId(user.getId())
                .githubRepoId(5001L) // duplicate for same user
                .owner("tester")
                .name("same-repo")
                .fullName("tester/same-repo")
                .defaultBranch("main")
                .build();

        assertThatThrownBy(() -> repositoryRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ChatSession and ChatMessage relationships and ordered queries")
    void testChatSessionAndMessageQueries() {
        User user = userRepository.saveAndFlush(User.builder()
                .githubId(303L)
                .githubUsername("chatter")
                .displayName("Chatter")
                .accessToken("token")
                .build());

        ConnectedRepository repo = repositoryRepository.saveAndFlush(ConnectedRepository.builder()
                .userId(user.getId())
                .githubRepoId(6001L)
                .owner("chatter")
                .name("ai-project")
                .fullName("chatter/ai-project")
                .defaultBranch("main")
                .build());

        ChatSession session1 = chatSessionRepository.saveAndFlush(ChatSession.builder()
                .userId(user.getId())
                .repositoryId(repo.getId())
                .title("First Chat")
                .build());

        ChatSession session2 = chatSessionRepository.saveAndFlush(ChatSession.builder()
                .userId(user.getId())
                .repositoryId(repo.getId())
                .title("Second Chat")
                .build());

        List<ChatSession> sessions = chatSessionRepository.findByUserIdAndRepositoryIdOrderByCreatedAtDesc(user.getId(), repo.getId());
        assertThat(sessions).hasSize(2);

        // Add messages to session1
        ChatMessage userMsg = chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .sessionId(session1.getId())
                .role(MessageRole.USER)
                .content("How does auth work?")
                .build());

        ChatMessage assistantMsg = chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .sessionId(session1.getId())
                .role(MessageRole.ASSISTANT)
                .content("Auth is handled via GitHub OAuth2.")
                .citations("[{\"file\":\"SecurityConfig.java\",\"lines\":\"10-30\"}]")
                .build());

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session1.getId());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(0).getContent()).isEqualTo("How does auth work?");
        assertThat(messages.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(messages.get(1).getCitations()).contains("SecurityConfig.java");
    }
}
