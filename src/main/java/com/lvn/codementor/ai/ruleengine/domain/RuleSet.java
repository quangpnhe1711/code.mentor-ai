package com.lvn.codementor.ai.ruleengine.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Organization-owned collection of review rules.
 */
@Entity
@Table(name = "rule_sets")
public class RuleSet extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    protected RuleSet() {
        // for JPA
    }

    public RuleSet(UUID organizationId, String name, String description, UUID createdByUserId) {
        this.organizationId = organizationId;
        this.name = name;
        this.description = description;
        this.createdByUserId = createdByUserId;
        this.archived = false;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void archive() {
        this.archived = true;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public boolean isArchived() {
        return archived;
    }
}
