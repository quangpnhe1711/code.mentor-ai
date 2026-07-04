package com.lvn.codementor.ai.testgeneration.persistence;

import com.lvn.codementor.ai.testgeneration.domain.TestResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestResultJpaRepository extends JpaRepository<TestResult, UUID> {

    Optional<TestResult> findByTestRunId(UUID testRunId);
}
