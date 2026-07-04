package com.lvn.codementor.ai.testgeneration.persistence;

import com.lvn.codementor.ai.testgeneration.domain.GeneratedTest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedTestJpaRepository extends JpaRepository<GeneratedTest, UUID> {

    List<GeneratedTest> findByTestGenerationJobIdOrderByCreatedAtAsc(UUID testGenerationJobId);

    Optional<GeneratedTest> findByIdAndOrganizationIdAndRepositoryId(UUID id, UUID organizationId, UUID repositoryId);
}
