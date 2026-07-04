package com.lvn.codementor.ai.testgeneration.persistence;

import com.lvn.codementor.ai.testgeneration.domain.TestRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRunJpaRepository extends JpaRepository<TestRun, UUID> {

    List<TestRun> findByGeneratedTestIdOrderByCreatedAtDesc(UUID generatedTestId);

    Optional<TestRun> findByIdAndOrganizationIdAndRepositoryId(UUID id, UUID organizationId, UUID repositoryId);
}
