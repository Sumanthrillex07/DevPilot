package com.devpilot.backend.services.graph;

import com.devpilot.backend.dto.DependencyGraphResponse;
import com.devpilot.backend.entities.ConnectedRepository;
import com.devpilot.backend.exceptions.NotFoundException;
import com.devpilot.backend.exceptions.UnauthorizedException;
import com.devpilot.backend.repositories.ConnectedRepositoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyGraphServiceTest {

    @Mock
    private ConnectedRepositoryRepository repositoryRepository;

    private CodeRelationshipExtractor extractor;
    private DependencyGraphService graphService;

    @BeforeEach
    void setUp() {
        extractor = new CodeRelationshipExtractor();
        graphService = new DependencyGraphService(repositoryRepository, extractor);
    }

    @Test
    @DisplayName("Builds dependency graph and traverses 1-hop related neighbors")
    void buildGraphAndTraverse() {
        UUID repoId = UUID.randomUUID();

        String javaService = """
                package com.example;
                import com.example.repo.UserRepository;
                public class UserService {}
                """;

        String javaRepo = """
                package com.example.repo;
                public interface UserRepository {}
                """;

        Map<String, String> files = Map.of(
                "src/UserService.java", javaService,
                "src/UserRepository.java", javaRepo
        );

        DependencyGraph graph = graphService.buildGraph(repoId, files);

        assertThat(graph.getNodes()).contains("src/UserService.java", "src/UserRepository.java", "UserService", "UserRepository");
        assertThat(graph.getEdges()).isNotEmpty();

        Set<String> neighbors = graphService.getRelatedIdentifiers(repoId, "src/UserService.java");
        assertThat(neighbors).contains("com.example.repo.UserRepository", "UserService");
    }

    @Test
    @DisplayName("getGraphResponse enforces repository ownership")
    void getGraphResponseEnforcesOwnership() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();

        ConnectedRepository repo = ConnectedRepository.builder()
                .id(repoId)
                .userId(ownerId)
                .build();

        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo));

        // Unauthorized when called by a different user
        assertThatThrownBy(() -> graphService.getGraphResponse(otherUserId, repoId))
                .isInstanceOf(UnauthorizedException.class);

        // Success when called by owner
        DependencyGraphResponse response = graphService.getGraphResponse(ownerId, repoId);
        assertThat(response.repositoryId()).isEqualTo(repoId);
    }

    @Test
    @DisplayName("getGraphResponse throws 404 when repository does not exist")
    void getGraphResponseNotFound() {
        UUID userId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();

        when(repositoryRepository.findById(repoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> graphService.getGraphResponse(userId, repoId))
                .isInstanceOf(NotFoundException.class);
    }
}
