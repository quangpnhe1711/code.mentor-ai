package com.lvn.codementor.ai.ruleengine.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Active rule set assignment for a repository. The database enforces at most one active assignment.
 */
@Entity
@Table(name = "repository_rule_set_assignments")
public class RepositoryRuleSetAssignment extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "rule_set_id", nullable = false)
    private UUID ruleSetId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "assigned_by_user_id", nullable = false)
    private UUID assignedByUserId;

    protected RepositoryRuleSetAssignment() {
        // for JPA
    }

    public RepositoryRuleSetAssignment(UUID organizationId, UUID repositoryId, UUID ruleSetId, UUID assignedByUserId) {
        this.organizationId = organizationId;
        this.repositoryId = repositoryId;
        this.ruleSetId = ruleSetId;
        this.assignedByUserId = assignedByUserId;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getRuleSetId() {
        return ruleSetId;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getAssignedByUserId() {
        return assignedByUserId;
    }
}
