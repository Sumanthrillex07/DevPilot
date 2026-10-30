package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.CitationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Retrieves semantically relevant source code chunks for a user question from the vector store.
 *
 * <p>Always scopes the similarity search to the specified {@code repositoryId} using metadata filters
 * to prevent cross-repository data leakage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CodeContextRetriever {

    private static final String NO_MATCHES = "(no matching code chunks found)";

    private final VectorStore vectorStore;
    private final CitationMapper citationMapper;
    private final com.devpilot.backend.services.graph.DependencyGraphService dependencyGraphService;

    /**
     * Executes similarity search scoped to the given repository and compiles context,
     * augmented with 1-hop code relationship context.
     *
     * @param repositoryId UUID of the repository to search within
     * @param question     User's natural language question
     * @return {@link RetrievedContext} containing distinct citations and concatenated chunk text
     */
    public RetrievedContext retrieve(UUID repositoryId, String question) {
        var filter = new FilterExpressionBuilder()
                .eq(RagSettings.METADATA_REPO_ID, repositoryId.toString())
                .build();

        var search = SearchRequest.builder()
                .query(question)
                .topK(RagSettings.TOP_K_CHUNKS)
                .filterExpression(filter)
                .build();

        List<Document> documents = vectorStore.similaritySearch(search);

        List<CitationDto> citations = documents.stream()
                .map(citationMapper::fromDocument)
                .filter(c -> c.filePath() != null)
                .distinct()
                .toList();

        String contextText = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // Enrich with 1-hop code dependency relationships for retrieved citation files
        if (dependencyGraphService != null && !citations.isEmpty()) {
            StringBuilder relationsBuilder = new StringBuilder();
            for (CitationDto citation : citations) {
                var neighbors = dependencyGraphService.getRelatedIdentifiers(repositoryId, citation.filePath());
                if (!neighbors.isEmpty()) {
                    relationsBuilder.append("\n- File `")
                            .append(citation.filePath())
                            .append("` relates to: ")
                            .append(String.join(", ", neighbors));
                }
            }
            if (!relationsBuilder.isEmpty()) {
                contextText += "\n\n### Related Symbols & Dependencies:\n" + relationsBuilder;
            }
        }

        if (contextText.isBlank()) {
            contextText = NO_MATCHES;
        }

        return new RetrievedContext(citations, contextText);
    }
}
