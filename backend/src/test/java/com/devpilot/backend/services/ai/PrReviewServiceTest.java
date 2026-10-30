package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.PrReviewCommentDto;
import com.devpilot.backend.dto.PrReviewResponse;
import com.devpilot.backend.dto.ReviewDiffRequest;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.entities.User;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import com.devpilot.backend.services.UserService;
import com.devpilot.backend.services.github.GithubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrReviewServiceTest {

    @Mock
    private ConnectedRepositoryRepository repositoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private GithubApiClient gitHubApiClient;

    @Mock
    private CodeContextRetriever contextRetriever;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private PrReviewService prReviewService;

    private UUID userId;
    private UUID repoId;
    private ConnectedRepository repo;

    @BeforeEach
    void setUp() {
        prReviewService = new PrReviewService(
                repositoryRepository, userService, gitHubApiClient, contextRetriever, chatClient
        );
        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();
        repo = ConnectedRepository.builder()
                .id(repoId)
                .userId(userId)
                .owner("octocat")
                .name("hello-world")
                .build();
    }

    @Test
    @DisplayName("reviewDiff: throws NotFoundException when repository does not exist")
    void reviewDiff_repoNotFound() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.empty());

        ReviewDiffRequest request = new ReviewDiffRequest("Title", "Desc", "diff --git a/a b/a");
        assertThatThrownBy(() -> prReviewService.reviewDiff(userId, repoId, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("reviewDiff: throws UnauthorizedException when user does not own repository")
    void reviewDiff_unauthorized() {
        UUID otherUser = UUID.randomUUID();
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        ReviewDiffRequest request = new ReviewDiffRequest("Title", "Desc", "diff --git a/a b/a");
        assertThatThrownBy(() -> prReviewService.reviewDiff(otherUser, repoId, request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("reviewDiff: successfully reviews unified diff and parses verdict and inline comments")
    void reviewDiff_success() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(contextRetriever.retrieve(any(UUID.class), anyString()))
                .thenReturn(new RetrievedContext(List.of(), "public class Auth {}"));

        String mockLlmAnswer = """
                VERDICT: REQUEST_CHANGES
                RISK_SCORE: 75
                SUMMARY:
                Potential security vulnerability identified in token storage.
                COMMENTS:
                - [CRITICAL] [SECURITY] (src/Auth.java:12) Plaintext secret detected | SUGGESTION: Encrypt token with AES
                """;

        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(mockLlmAnswer);

        String sampleDiff = """
                diff --git a/src/Auth.java b/src/Auth.java
                @@ -10,2 +10,4 @@
                 public class Auth {
                +    private String secret;
                """;

        ReviewDiffRequest request = new ReviewDiffRequest("Security patch", "Adds secret", sampleDiff);
        PrReviewResponse response = prReviewService.reviewDiff(userId, repoId, request);

        assertThat(response.repositoryId()).isEqualTo(repoId);
        assertThat(response.verdict()).isEqualTo(PrReviewResponse.ReviewVerdict.REQUEST_CHANGES);
        assertThat(response.riskScore()).isEqualTo(75);
        assertThat(response.summary()).contains("Potential security vulnerability");
        assertThat(response.examinedFiles()).contains("src/Auth.java");
        assertThat(response.comments()).hasSize(1);

        PrReviewCommentDto comment = response.comments().get(0);
        assertThat(comment.filePath()).isEqualTo("src/Auth.java");
        assertThat(comment.lineNumber()).isEqualTo(12);
        assertThat(comment.severity()).isEqualTo(PrReviewCommentDto.CommentSeverity.CRITICAL);
        assertThat(comment.category()).isEqualTo(PrReviewCommentDto.CommentCategory.SECURITY);
        assertThat(comment.suggestedChange()).isEqualTo("Encrypt token with AES");
    }

    @Test
    @DisplayName("reviewGitHubPullRequest: fetches diff from GitHub and reviews successfully")
    void reviewGitHubPullRequest_success() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        User user = User.builder().id(userId).accessToken("encrypted-token").build();
        when(userService.requiredById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw-token");

        when(gitHubApiClient.getPullRequest("raw-token", "octocat", "hello-world", 42))
                .thenReturn(Map.of("title", "Add payment processing", "body", "Integration with Stripe"));
        when(gitHubApiClient.getPullRequestDiff("raw-token", "octocat", "hello-world", 42))
                .thenReturn("diff --git a/Payment.java b/Payment.java\n@@ -1,1 +1,2 @@\n+charge();");

        when(contextRetriever.retrieve(any(UUID.class), anyString()))
                .thenReturn(new RetrievedContext(List.of(), "context text"));

        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("VERDICT: APPROVED\nRISK_SCORE: 15\nSUMMARY: Clean changes.\nCOMMENTS:");

        PrReviewResponse response = prReviewService.reviewGitHubPullRequest(userId, repoId, 42);

        assertThat(response.pullRequestNumber()).isEqualTo(42);
        assertThat(response.verdict()).isEqualTo(PrReviewResponse.ReviewVerdict.APPROVED);
        assertThat(response.riskScore()).isEqualTo(15);
    }
}
