package com.lvn.codementor.ai.codeanalysis.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Metadata about a sanitized review input built from a READY {@code RepositorySnapshot} (pre-AI
 * phase; no provider is called yet). Holds only counts, a stable content hash, and a safe error
 * reason — <strong>never</strong> source code, file content (masked or not), secrets, the workspace
 * path, or credentials. Belongs to exactly one organization and references related aggregates by id.
 */
@Entity
@Table(name = "code_analysis_inputs")
public class CodeAnalysisInput extends BaseEntity {

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CodeAnalysisInputStatus status;

    /** Stable SHA-256 over sanitized (secret-masked) content and file paths. */
    @Column(name = "input_hash")
    private String inputHash;

    @Column(name = "included_file_count", nullable = false)
    private int includedFileCount;

    @Column(name = "skipped_file_count", nullable = false)
    private int skippedFileCount;

    @Column(name = "masked_secret_count", nullable = false)
    private int maskedSecretCount;

    @Column(name = "total_input_bytes", nullable = false)
    private long totalInputBytes;

    @Column(name = "error_reason")
    private String errorReason;

    protected CodeAnalysisInput() {
        // for JPA
    }

    public CodeAnalysisInput(UUID snapshotId, UUID repositoryId, UUID organizationId, UUID createdByUserId) {
        this.snapshotId = snapshotId;
        this.repositoryId = repositoryId;
        this.organizationId = organizationId;
        this.createdByUserId = createdByUserId;
        this.status = CodeAnalysisInputStatus.BUILDING;
    }

    public void markReady(
            String inputHash, int includedFileCount, int skippedFileCount, int maskedSecretCount, long totalInputBytes) {
        this.status = CodeAnalysisInputStatus.READY;
        this.inputHash = inputHash;
        this.includedFileCount = includedFileCount;
        this.skippedFileCount = skippedFileCount;
        this.maskedSecretCount = maskedSecretCount;
        this.totalInputBytes = totalInputBytes;
        this.errorReason = null;
    }

    /** Terminal failure. {@code reason} must be safe (no token, no local path, no content, no stack trace). */
    public void markFailed(String reason) {
        this.status = CodeAnalysisInputStatus.FAILED;
        this.errorReason = reason;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public CodeAnalysisInputStatus getStatus() {
        return status;
    }

    public String getInputHash() {
        return inputHash;
    }

    public int getIncludedFileCount() {
        return includedFileCount;
    }

    public int getSkippedFileCount() {
        return skippedFileCount;
    }

    public int getMaskedSecretCount() {
        return maskedSecretCount;
    }

    public long getTotalInputBytes() {
        return totalInputBytes;
    }

    public String getErrorReason() {
        return errorReason;
    }
}
