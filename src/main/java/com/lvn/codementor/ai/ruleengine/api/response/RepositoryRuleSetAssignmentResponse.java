package com.lvn.codementor.ai.ruleengine.api.response;

import com.lvn.codementor.ai.ruleengine.domain.RepositoryRuleSetAssignment;
import java.time.Instant;
import java.util.UUID;

public record RepositoryRuleSetAssignmentResponse(
        UUID id,
        UUID organizationId,
        UUID repositoryId,
        UUID ruleSetId,
        boolean active,
        UUID assignedByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public static RepositoryRuleSetAssignmentResponse from(RepositoryRuleSetAssignment assignment) {
        return new RepositoryRuleSetAssignmentResponse(
                assignment.getId(),
                assignment.getOrganizationId(),
                assignment.getRepositoryId(),
                assignment.getRuleSetId(),
                assignment.isActive(),
                assignment.getAssignedByUserId(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt());
    }
}
