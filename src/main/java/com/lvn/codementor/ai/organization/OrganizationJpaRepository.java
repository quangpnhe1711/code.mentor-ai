package com.lvn.codementor.ai.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationJpaRepository extends JpaRepository<Organization, UUID> {

    /** Find a user's personal organization (ADR-010: at most one per user). */
    Optional<Organization> findByOwnerUserIdAndType(UUID ownerUserId, OrganizationType type);

    boolean existsBySlug(String slug);
}
