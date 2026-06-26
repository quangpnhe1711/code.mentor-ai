package com.lvn.codementor.ai.repository.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A point-in-time clone of an imported repository, prepared for later analysis (doc 14 area; clone +
 * snapshot foundation). Holds only metadata — source code is never stored in the database. Belongs to
 * exactly one organization and references related aggregates by id.
 */
@Entity
@Table(name = "repository_snapshots")
public class RepositorySnapshot extends BaseEntity {

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RepositorySnapshotStatus status;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "commit_sha")
    private String commitSha;

    /** Local filesystem location of the pruned snapshot. Never exposed via the API. */
    @Column(name = "workspace_path")
    private String workspacePath;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Column(name = "error_reason")
    private String errorReason;

    protected RepositorySnapshot() {
        // for JPA
    }

    public RepositorySnapshot(UUID repositoryId, UUID organizationId, UUID createdByUserId) {
        this.repositoryId = repositoryId;
        this.organizationId = organizationId;
        this.createdByUserId = createdByUserId;
        this.status = RepositorySnapshotStatus.PENDING;
    }

    public void markCloning() {
        this.status = RepositorySnapshotStatus.CLONING;
    }

    public void markScanning(String sourceRef, String commitSha, String workspacePath) {
        this.status = RepositorySnapshotStatus.SCANNING;
        this.sourceRef = sourceRef;
        this.commitSha = commitSha;
        this.workspacePath = workspacePath;
    }

    public void markReady(int fileCount, long totalBytes) {
        this.status = RepositorySnapshotStatus.READY;
        this.fileCount = fileCount;
        this.totalBytes = totalBytes;
        this.errorReason = null;
    }

    /** Terminal failure. {@code reason} must be a safe message (no token, no local path, no stack trace). */
    public void markFailed(String reason) {
        this.status = RepositorySnapshotStatus.FAILED;
        this.errorReason = reason;
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

    public RepositorySnapshotStatus getStatus() {
        return status;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public String getErrorReason() {
        return errorReason;
    }
}
