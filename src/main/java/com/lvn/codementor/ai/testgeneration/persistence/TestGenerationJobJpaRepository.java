package com.lvn.codementor.ai.testgeneration.persistence;

import com.lvn.codementor.ai.testgeneration.domain.TestGenerationJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestGenerationJobJpaRepository extends JpaRepository<TestGenerationJob, UUID> {

    List<TestGenerationJob> findByOrganizationIdAndRepositoryIdOrderByCreatedAtDesc(UUID organizationId, UUID repositoryId);

    Optional<TestGenerationJob> findByIdAndOrganizationIdAndRepositoryId(UUID id, UUID organizationId, UUID repositoryId);
}
