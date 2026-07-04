package com.lvn.codementor.ai.repository.persistence;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportedRepositoryJpaRepository extends JpaRepository<ImportedRepository, UUID> {

    /** Uniqueness lookup for import (BR-REP-002). */
    Optional<ImportedRepository> findByOrganizationIdAndProviderAndExternalRepoId(
            UUID organizationId, RepositoryProvider provider, String externalRepoId);

    /** All repositories owned by an organization (listing). */
    List<ImportedRepository> findByOrganizationId(UUID organizationId);

    List<ImportedRepository> findByProviderAndExternalRepoId(RepositoryProvider provider, String externalRepoId);

    /** A repository scoped to its owning organization (detail; enforces org ownership). */
    Optional<ImportedRepository> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
