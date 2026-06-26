package com.lvn.codementor.ai.organization.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Links a user to an organization with a role (doc 14 §3.3). Both sides are referenced by id only,
 * keeping the Organization module decoupled from the Identity module's entity. Unique per
 * {@code (organizationId, userId)}.
 */
@Entity
@Table(name = "organization_members")
public class OrganizationMember extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private OrganizationRole role;

    protected OrganizationMember() {
        // for JPA
    }

    public OrganizationMember(UUID organizationId, UUID userId, OrganizationRole role) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrganizationRole getRole() {
        return role;
    }
}
