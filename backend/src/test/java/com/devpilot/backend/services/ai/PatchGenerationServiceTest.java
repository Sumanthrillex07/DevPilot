package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.*;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
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
class PatchGenerationServiceTest {

    @Mock
    private ConnectedRepositoryRepository repositoryRepository;

    @Mock
    private CodeContextRetriever contextRetriever;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private final UnifiedDiffParser diffParser = new UnifiedDiffParser();
    private final PatchApplier patchApplier = new PatchApplier(diffParser);
    private final PatchSafetyValidator safetyValidator = new PatchSafetyValidator(diffParser, patchApplier);

    private PatchGenerationService patchService;

    private UUID userId;
    private UUID repoId;
    private ConnectedRepository repo;

    @BeforeEach
    void setUp() {
        patchService = new PatchGenerationService(
                repositoryRepository,
                contextRetriever,
                chatClient,
                diffParser,
                safetyValidator,
                patchApplier
        );

        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();
        repo = ConnectedRepository.builder()
                .id(repoId)
                .userId(userId)
                .owner("octocat")
                .name("hello-world")
                .fullName("octocat/hello-world")
                .defaultBranch("main")
                .build();
    }

    @Test
    @DisplayName("Generates safe patch successfully when grounded by vector store")
    void generatePatch_success() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        RetrievedContext context = new RetrievedContext(
                List.of(new CitationDto("Calculator.java", 1, 5, "java")),
                "public int add(int a, int b) { return a - b; }"
        );
        when(contextRetriever.retrieve(any(UUID.class), anyString()))
                .thenReturn(context);

        String fakeLlmResponse = """
                ```diff
                diff --git a/Calculator.java b/Calculator.java
                --- a/Calculator.java
                +++ b/Calculator.java
                @@ -1,5 +1,5 @@
                 public class Calculator {
                     public int add(int a, int b) {
                -        return a - b;
                +        return a + b;
                     }
                 }
                ```
                EXPLANATION:
                Fixed incorrect subtraction operator in add method.
                """;

        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(fakeLlmResponse);

        GeneratePatchRequest request = new GeneratePatchRequest("Fix addition bug in Calculator", "Calculator.java", null, List.of());
        GeneratedPatchResponse response = patchService.generatePatch(repoId, request, userId);

        assertThat(response).isNotNull();
        assertThat(response.patchId()).isNotNull();
        assertThat(response.instruction()).isEqualTo("Fix addition bug in Calculator");
        assertThat(response.explanation()).contains("Fixed incorrect subtraction operator");
        assertThat(response.affectedFiles()).contains("Calculator.java");
        assertThat(response.unifiedDiff()).contains("return a + b;");
        assertThat(response.validation().valid()).isTrue();
        assertThat(response.citations()).hasSize(1);
    }

    @Test
    @DisplayName("Throws UnauthorizedException when requesting user does not own repo")
    void generatePatch_unauthorizedUser() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));
        UUID otherUserId = UUID.randomUUID();

        GeneratePatchRequest request = new GeneratePatchRequest("Instruction");
        assertThatThrownBy(() -> patchService.generatePatch(repoId, request, otherUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("You do not have access");
    }

    @Test
    @DisplayName("Throws NotFoundException when repository does not exist")
    void generatePatch_repoNotFound() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.empty());

        GeneratePatchRequest request = new GeneratePatchRequest("Instruction");
        assertThatThrownBy(() -> patchService.generatePatch(repoId, request, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Repository not found");
    }

    @Test
    @DisplayName("Validates patch diff for repository owner")
    void validatePatch_success() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        String diff = """
                diff --git a/test.txt b/test.txt
                @@ -1,1 +1,2 @@
                 line 1
                +line 2
                """.trim();

        ValidatePatchRequest request = new ValidatePatchRequest(diff, "test.txt", "line 1");
        PatchValidationResult result = patchService.validatePatch(repoId, request, userId);

        assertThat(result.valid()).isTrue();
        assertThat(result.dryRunSuccess()).isTrue();
    }

    @Test
    @DisplayName("Dry-runs patch against original content")
    void dryRunPatch_success() {
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        String original = "line 1\nline 2";
        String diff = """
                diff --git a/file.txt b/file.txt
                @@ -1,2 +1,2 @@
                 line 1
                -line 2
                +line 2 updated
                """.trim();

        DryRunPatchRequest request = new DryRunPatchRequest(diff, original);
        DryRunPatchResponse result = patchService.dryRunPatch(repoId, request, userId);

        assertThat(result.success()).isTrue();
        assertThat(result.patchedContent()).isEqualTo("line 1\nline 2 updated");
    }
}
