package com.lvn.codementor.ai.organization;

import com.lvn.codementor.ai.sharedkernel.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A tenant grouping users and repositories (doc 14 §3.2). A {@code PERSONAL} org is auto-provisioned
 * per user (ADR-010). The owning user is referenced by id only (no cross-module entity association).
 */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OrganizationType type;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Organization() {
        // for JPA
    }

    public Organization(String name, String slug, OrganizationType type, UUID ownerUserId) {
        this.name = name;
        this.slug = slug;
        this.type = type;
        this.ownerUserId = ownerUserId;
    }

    /** Build the default personal organization for a user (ADR-010). */
    public static Organization personalFor(UUID ownerUserId, String name, String slug) {
        return new Organization(name, slug, OrganizationType.PERSONAL, ownerUserId);
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public OrganizationType getType() {
        return type;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
