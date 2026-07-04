package com.lvn.codementor.ai.knowledge.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions")
public class ChatSession extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "title", nullable = false)
    private String title;

    protected ChatSession() {
        // for JPA
    }

    public ChatSession(UUID organizationId, UUID repositoryId, UUID snapshotId, UUID createdByUserId, String title) {
        this.organizationId = organizationId;
        this.repositoryId = repositoryId;
        this.snapshotId = snapshotId;
        this.createdByUserId = createdByUserId;
        this.title = title;
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

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public String getTitle() {
        return title;
    }
}
