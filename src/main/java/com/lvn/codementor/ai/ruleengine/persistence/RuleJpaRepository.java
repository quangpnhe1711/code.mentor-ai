package com.lvn.codementor.ai.ruleengine.persistence;

import com.lvn.codementor.ai.ruleengine.domain.Rule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleJpaRepository extends JpaRepository<Rule, UUID> {

    List<Rule> findByRuleSetIdOrderByCreatedAtAsc(UUID ruleSetId);

    Optional<Rule> findByIdAndOrganizationIdAndRuleSetId(UUID id, UUID organizationId, UUID ruleSetId);
}
