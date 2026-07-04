package com.lvn.codementor.ai.ruleengine.persistence;

import com.lvn.codementor.ai.ruleengine.domain.RepositoryRuleSetAssignment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryRuleSetAssignmentJpaRepository
        extends JpaRepository<RepositoryRuleSetAssignment, UUID> {

    Optional<RepositoryRuleSetAssignment> findByOrganizationIdAndRepositoryIdAndActiveTrue(
            UUID organizationId, UUID repositoryId);
}
