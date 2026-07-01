package com.lvn.codementor.ai.codeanalysis.persistence;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeAnalysisInputJpaRepository extends JpaRepository<CodeAnalysisInput, UUID> {

    /** An analysis input scoped to its owning organization and repository (detail; enforces ownership). */
    Optional<CodeAnalysisInput> findByIdAndOrganizationIdAndRepositoryId(
            UUID id, UUID organizationId, UUID repositoryId);
}
