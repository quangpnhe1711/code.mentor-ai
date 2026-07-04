package com.lvn.codementor.ai.ruleengine.persistence;

import com.lvn.codementor.ai.ruleengine.domain.RuleSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleSetJpaRepository extends JpaRepository<RuleSet, UUID> {

    List<RuleSet> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<RuleSet> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
