package com.devpilot.backend.services.ai;

import com.devpilot.backend.dto.CitationDto;

import java.util.List;

/**
 * Encapsulates code chunks retrieved from pgvector along with structured citations.
 */
public record RetrievedContext(
        List<CitationDto> citations,
        String contextText
) {}
