package com.lvn.codementor.ai.ruleengine.api.response;

import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import com.lvn.codementor.ai.ruleengine.domain.Rule;
import java.time.Instant;
import java.util.UUID;

public record RuleResponse(
        UUID id,
        UUID organizationId,
        UUID ruleSetId,
        String ruleKey,
        String title,
        String instruction,
        String category,
        ReviewFindingSeverity defaultSeverity,
        boolean enabled,
        boolean custom,
        Instant createdAt,
        Instant updatedAt) {

    public static RuleResponse from(Rule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getOrganizationId(),
                rule.getRuleSetId(),
                rule.getRuleKey(),
                rule.getTitle(),
                rule.getInstruction(),
                rule.getCategory(),
                rule.getDefaultSeverity(),
                rule.isEnabled(),
                rule.isCustom(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
