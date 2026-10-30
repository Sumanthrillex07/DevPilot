package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.BugReportResponse;
import com.devpilot.backend.dto.CitationDto;
import com.devpilot.backend.dto.InvestigateBugRequest;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.graph.DependencyGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugInvestigationServiceTest {

    @Mock
    private ConnectedRepositoryRepository repositoryRepository;

    @Mock
    private CodeContextRetriever contextRetriever;

    @Mock
    private DependencyGraphService dependencyGraphService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private BugInvestigationService bugInvestigationService;

    private UUID userId;
    private UUID repoId;
    private ConnectedRepository repo;

    @BeforeEach
    void setUp() {
        bugInvestigationService = new BugInvestigationService(
                repositoryRepository, contextRetriever, dependencyGraphService, chatClient
        );
        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();
        repo = ConnectedRepository.builder()
                .id(repoId)
                .userId(userId)
                .name("test-repo")
                .build();
    }

    @Test
    @DisplayName("investigate: throws NotFoundException when repository does not exist")
    void investigate_repoNotFound() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.empty());

        InvestigateBugRequest request = new InvestigateBugRequest("Null pointer in login", null, null, null);

        assertThatThrownBy(() -> bugInvestigationService.investigate(userId, repoId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("investigate: throws UnauthorizedException when user does not own repo")
    void investigate_unauthorized() {
        UUID otherUser = UUID.randomUUID();
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        InvestigateBugRequest request = new InvestigateBugRequest("Null pointer in login", null, null, null);

        assertThatThrownBy(() -> bugInvestigationService.investigate(otherUser, repoId, request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("investigate: executes full diagnosis pipeline and returns structured report")
    void investigate_success() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        CitationDto citation = new CitationDto("src/AuthService.java", 20, 45, "Java");
        RetrievedContext context = new RetrievedContext(
                List.of(citation),
                "public User login(String token) { return verify(token); }"
        );
        when(contextRetriever.retrieve(any(UUID.class), anyString())).thenReturn(context);

        String mockLlmAnswer = """
                SUMMARY:
                NullPointerException occurs when token is null.
                
                ROOT CAUSE:
                Line 25 does not validate token before passing to verify method.
                
                REPRODUCTION:
                1. Call login with null token
                2. Observe NPE thrown
                
                SUGGESTED FIX:
                Add Objects.requireNonNull(token, "token required") check.
                """;

        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(mockLlmAnswer);

        InvestigateBugRequest request = new InvestigateBugRequest(
                "NPE when logging in",
                "java.lang.NullPointerException\n\tat com.devpilot.AuthService.login(AuthService.java:25)",
                null,
                null
        );

        BugReportResponse report = bugInvestigationService.investigate(userId, repoId, request);

        assertThat(report.repositoryId()).isEqualTo(repoId);
        assertThat(report.summary()).contains("NullPointerException occurs when token is null");
        assertThat(report.rootCause()).contains("Line 25 does not validate token");
        assertThat(report.reproductionSteps()).isNotEmpty();
        assertThat(report.suggestedFix()).contains("Add Objects.requireNonNull");
        assertThat(report.suspectedFiles()).contains("AuthService.java");
        assertThat(report.citations()).hasSize(1);
        assertThat(report.citations().get(0).filePath()).isEqualTo("src/AuthService.java");
    }
}
