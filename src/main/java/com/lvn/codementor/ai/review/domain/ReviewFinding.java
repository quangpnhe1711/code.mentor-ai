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
 * <p>ponytail: JPA-only (no public constructor) — nothing creates findings yet; add a constructor when
 * the worker phase lands.
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

    protected ReviewFinding() {
        // for JPA
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
}
