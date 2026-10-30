package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.CitationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationMapperTest {

    private CitationMapper citationMapper;

    @BeforeEach
    void setUp() {
        citationMapper = new CitationMapper(new ObjectMapper());
    }

    @Test
    @DisplayName("fromDocument: extracts CitationDto fields from Document metadata")
    void fromDocument_extractsFields() {
        Document doc = new Document("code snippet", Map.of(
                RagSettings.METADATA_FILE_PATH, "src/App.java",
                RagSettings.METADATA_START_LINE, 10,
                RagSettings.METADATA_END_LINE, 25,
                RagSettings.METADATA_LANGUAGE, "java"
        ));

        CitationDto citation = citationMapper.fromDocument(doc);

        assertThat(citation.filePath()).isEqualTo("src/App.java");
        assertThat(citation.startLine()).isEqualTo(10);
        assertThat(citation.endLine()).isEqualTo(25);
        assertThat(citation.language()).isEqualTo("java");
    }

    @Test
    @DisplayName("toJson & fromJson: round-trips citation list through JSON string")
    void jsonRoundTrip() {
        List<CitationDto> original = List.of(
                new CitationDto("src/App.java", 1, 20, "java"),
                new CitationDto("README.md", 5, 12, "markdown")
        );

        String json = citationMapper.toJson(original);
        List<CitationDto> parsed = citationMapper.fromJson(json);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0)).isEqualTo(original.get(0));
        assertThat(parsed.get(1)).isEqualTo(original.get(1));
    }

    @Test
    @DisplayName("fromJson: handles null, blank, and invalid JSON gracefully")
    void fromJson_handlesInvalidInput() {
        assertThat(citationMapper.fromJson(null)).isEmpty();
        assertThat(citationMapper.fromJson("   ")).isEmpty();
        assertThat(citationMapper.fromJson("invalid json")).isEmpty();
    }
}
