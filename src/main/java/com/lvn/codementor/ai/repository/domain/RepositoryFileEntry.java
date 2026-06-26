package com.lvn.codementor.ai.repository.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One file observed while scanning a snapshot. Carries inventory metadata only — never file content.
 * {@code included=false} files (sensitive, oversized, or in skipped directories) are recorded with a
 * {@code skipReason} and are not retained in the snapshot workspace.
 */
@Entity
@Table(name = "repository_file_entries")
public class RepositoryFileEntry extends BaseEntity {

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "language")
    private String language;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "included", nullable = false)
    private boolean included;

    @Column(name = "skip_reason")
    private String skipReason;

    protected RepositoryFileEntry() {
        // for JPA
    }

    public RepositoryFileEntry(
            UUID snapshotId, String path, String language, long sizeBytes, boolean included, String skipReason) {
        this.snapshotId = snapshotId;
        this.path = path;
        this.language = language;
        this.sizeBytes = sizeBytes;
        this.included = included;
        this.skipReason = skipReason;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public String getPath() {
        return path;
    }

    public String getLanguage() {
        return language;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public boolean isIncluded() {
        return included;
    }

    public String getSkipReason() {
        return skipReason;
    }
}
