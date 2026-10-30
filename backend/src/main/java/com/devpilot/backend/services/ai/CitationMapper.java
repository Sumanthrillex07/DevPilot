package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.CitationDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Converts between vector-store {@link Document} metadata, {@link CitationDto}s,
 * and JSON strings for database persistence.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CitationMapper {

    private final ObjectMapper objectMapper;

    /**
     * Extracts a {@link CitationDto} from the metadata of a retrieved Spring AI {@link Document}.
     */
    public CitationDto fromDocument(Document document) {
        if (document == null || document.getMetadata() == null) {
            return new CitationDto(null, null, null, null);
        }

        Map<String, Object> meta = document.getMetadata();
        return new CitationDto(
                stringVal(meta.get(RagSettings.METADATA_FILE_PATH)),
                intVal(meta.get(RagSettings.METADATA_START_LINE)),
                intVal(meta.get(RagSettings.METADATA_END_LINE)),
                stringVal(meta.get(RagSettings.METADATA_LANGUAGE))
        );
    }

    /**
     * Serializes a list of citations into a JSON array string for the database column.
     */
    public String toJson(List<CitationDto> citations) {
        if (citations == null || citations.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize citations to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Deserializes a JSON string into a list of {@link CitationDto}s.
     */
    public List<CitationDto> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CitationDto>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse citations JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
