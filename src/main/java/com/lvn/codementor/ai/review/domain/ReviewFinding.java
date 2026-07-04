package com.lvn.codementor.ai.review.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single review finding attached to a {@link ReviewJob}. The table exists now for schema stability,
 * but <strong>no findings are generated in this phase</strong> — the future worker/AI phase populates
 * it. Carries only a file path and structured metadata, never source content.
 *
 * <p>The {@code code_snippet} column holds the offending line (already secret-masked; the secret rule
 * additionally redacts the matched value), so a reviewer can see the exact code. It is nullable —
 * AI-provider findings do not include a snippet.
 */
@Entity
@Table(name = "review_findings")
public class ReviewFinding extends BaseEntity {

    @Column(name = "review_job_id", nullable = false)
    private UUID reviewJobId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "line_start")
    private Integer lineStart;

    @Column(name = "line_end")
    private Integer lineEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private ReviewFindingSeverity severity;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "suggestion")
    private String suggestion;

    @Column(name = "rule_id")
    private String ruleId;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "code_snippet")
    private String codeSnippet;

    protected ReviewFinding() {
        // for JPA
    }

    public ReviewFinding(
            UUID reviewJobId,
            UUID organizationId,
            UUID repositoryId,
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
            String codeSnippet) {
        this.reviewJobId = reviewJobId;
        this.organizationId = organizationId;
        this.repositoryId = repositoryId;
        this.filePath = filePath;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
        this.severity = severity;
        this.category = category;
        this.title = title;
        this.description = description;
        this.suggestion = suggestion;
        this.ruleId = ruleId;
        this.confidence = confidence;
        this.codeSnippet = codeSnippet;
    }

    public UUID getReviewJobId() {
        return reviewJobId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineStart() {
        return lineStart;
    }

    public Integer getLineEnd() {
        return lineEnd;
    }

    public ReviewFindingSeverity getSeverity() {
        return severity;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getRuleId() {
        return ruleId;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getCodeSnippet() {
        return codeSnippet;
    }
}
