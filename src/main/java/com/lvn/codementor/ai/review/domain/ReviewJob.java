package com.lvn.codementor.ai.review.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A future AI-review execution over a READY {@code CodeAnalysisInput} (pre-AI phase; no provider is
 * called yet). Created in {@code QUEUED} status. Holds only ids, lifecycle state, the copied
 * {@code input_hash} (for traceability back to the sanitized input), and safe metadata —
 * <strong>never</strong> source content (masked or not), the workspace path, or credentials.
 */
@Entity
@Table(name = "review_jobs")
public class ReviewJob extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "analysis_input_id", nullable = false)
    private UUID analysisInputId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false)
    private ReviewType reviewType;

    /** Copied verbatim from the source {@code code_analysis_inputs.input_hash} at creation time. */
    @Column(name = "input_hash", nullable = false)
    private String inputHash;

    // Provider/model/prompt are populated by the future worker phase; null while QUEUED.
    @Column(name = "ai_provider")
    private String aiProvider;

    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "total_findings", nullable = false)
    private int totalFindings;

    @Column(name = "error_reason")
    private String errorReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ReviewJob() {
        // for JPA
    }

    public ReviewJob(
            UUID organizationId,
            UUID repositoryId,
            UUID snapshotId,
            UUID analysisInputId,
            UUID createdByUserId,
            ReviewType reviewType,
            String inputHash) {
        this.organizationId = organizationId;
        this.repositoryId = repositoryId;
        this.snapshotId = snapshotId;
        this.analysisInputId = analysisInputId;
        this.createdByUserId = createdByUserId;
        this.reviewType = reviewType;
        this.inputHash = inputHash;
        this.status = ReviewJobStatus.QUEUED;
        this.totalFindings = 0;
    }

    /** QUEUED → RUNNING when execution starts. */
    public void markRunning() {
        this.status = ReviewJobStatus.RUNNING;
        this.startedAt = Instant.now();
        this.errorReason = null;
    }

    /** RUNNING → COMPLETED once findings are persisted. {@code totalFindings} must equal the row count. */
    public void markCompleted(int totalFindings) {
        this.status = ReviewJobStatus.COMPLETED;
        this.totalFindings = totalFindings;
        this.completedAt = Instant.now();
        this.errorReason = null;
    }

    /** Terminal failure. {@code reason} must be safe (no path, content, secret, or stack trace). */
    public void markFailed(String reason) {
        this.status = ReviewJobStatus.FAILED;
        this.errorReason = reason;
        this.completedAt = Instant.now();
    }

    /** Whether this job may transition to RUNNING (only a QUEUED job is runnable). */
    public boolean isRunnable() {
        return this.status == ReviewJobStatus.QUEUED;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public UUID getAnalysisInputId() {
        return analysisInputId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public ReviewJobStatus getStatus() {
        return status;
    }

    public ReviewType getReviewType() {
        return reviewType;
    }

    public String getInputHash() {
        return inputHash;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public int getTotalFindings() {
        return totalFindings;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
