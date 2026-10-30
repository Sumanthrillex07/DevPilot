package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.CitationDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeContextRetrieverTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private CitationMapper citationMapper;

    @Mock
    private com.devpilot.backend.services.graph.DependencyGraphService dependencyGraphService;

    @InjectMocks
    private CodeContextRetriever retriever;

    @Test
    @DisplayName("retrieve: searches vector store with repoId filter and returns distinct citations and text")
    void retrieve_returnsContextAndCitations() {
        UUID repoId = UUID.randomUUID();
        Document doc1 = new Document("int a = 1;");
        Document doc2 = new Document("int b = 2;");

        CitationDto citation = new CitationDto("src/Math.java", 1, 10, "java");

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2));
        when(citationMapper.fromDocument(doc1)).thenReturn(citation);
        when(citationMapper.fromDocument(doc2)).thenReturn(citation); // duplicate citation to test distinct

        RetrievedContext result = retriever.retrieve(repoId, "How are numbers defined?");

        assertThat(result.contextText()).contains("int a = 1;");
        assertThat(result.contextText()).contains("int b = 2;");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).filePath()).isEqualTo("src/Math.java");
    }

    @Test
    @DisplayName("retrieve: returns placeholder message when no matching chunks found")
    void retrieve_emptyResults_returnsPlaceholder() {
        UUID repoId = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        RetrievedContext result = retriever.retrieve(repoId, "unknown");

        assertThat(result.contextText()).isEqualTo("(no matching code chunks found)");
        assertThat(result.citations()).isEmpty();
    }
}
