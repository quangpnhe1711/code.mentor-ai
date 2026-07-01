package com.lvn.codementor.ai.review.api.response;

import com.lvn.codementor.ai.review.domain.ReviewFinding;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Review-finding view returned by the API. Structured metadata only (file path, location, severity,
 * text) — never raw source content. No findings are produced in this phase, so lists are empty.
 */
public record ReviewFindingResponse(
        UUID id,
        UUID reviewJobId,
        String filePath,
        Integer lineStart,
        Integer lineEnd,
        ReviewFindingSeverity severity,
        String category,
        String title,
        String description,
        String suggestion,
        String ruleId,
        BigDecimal confidence,
        Instant createdAt,
        Instant updatedAt) {

    public static ReviewFindingResponse from(ReviewFinding finding) {
        return new ReviewFindingResponse(
                finding.getId(),
                finding.getReviewJobId(),
                finding.getFilePath(),
                finding.getLineStart(),
                finding.getLineEnd(),
                finding.getSeverity(),
                finding.getCategory(),
                finding.getTitle(),
                finding.getDescription(),
                finding.getSuggestion(),
                finding.getRuleId(),
                finding.getConfidence(),
                finding.getCreatedAt(),
                finding.getUpdatedAt());
    }
}
