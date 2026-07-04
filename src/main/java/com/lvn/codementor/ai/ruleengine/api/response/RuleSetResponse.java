package com.lvn.codementor.ai.ruleengine.api.response;

import com.lvn.codementor.ai.ruleengine.domain.RuleSet;
import java.time.Instant;
import java.util.UUID;

public record RuleSetResponse(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        UUID createdByUserId,
        boolean archived,
        Instant createdAt,
        Instant updatedAt) {

    public static RuleSetResponse from(RuleSet ruleSet) {
        return new RuleSetResponse(
                ruleSet.getId(),
                ruleSet.getOrganizationId(),
                ruleSet.getName(),
                ruleSet.getDescription(),
                ruleSet.getCreatedByUserId(),
                ruleSet.isArchived(),
                ruleSet.getCreatedAt(),
                ruleSet.getUpdatedAt());
    }
}
