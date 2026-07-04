package com.lvn.codementor.ai.knowledge.api.response;

import com.lvn.codementor.ai.knowledge.domain.Citation;
import java.util.UUID;

public record CitationResponse(
        UUID id,
        String filePath,
        Integer lineStart,
        Integer lineEnd,
        String reason) {

    public static CitationResponse from(Citation citation) {
        return new CitationResponse(
                citation.getId(),
                citation.getFilePath(),
                citation.getLineStart(),
                citation.getLineEnd(),
                citation.getReason());
    }
}
