package com.lvn.codementor.ai.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryJpaRepository extends JpaRepository<Repository, UUID> {

    /** Uniqueness lookup for import (BR-REP-002). */
    Optional<Repository> findByOrganizationIdAndProviderAndExternalRepoId(
            UUID organizationId, RepositoryProvider provider, String externalRepoId);
}
