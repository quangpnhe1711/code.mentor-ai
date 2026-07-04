package com.lvn.codementor.ai.review.application.command;

import java.util.UUID;

/**
 * Request to create a review job from a READY analysis input, scoped to the acting user and the
 * organization/repository/analysis-input in the request path. {@code reviewType} is the raw request
 * value (null → default FULL_REPOSITORY); the service validates it and the target fields.
 */
public record CreateReviewJobCommand(
        UUID userId,
        UUID organizationId,
        UUID repositoryId,
        UUID analysisInputId,
        String reviewType,
        Integer targetPullRequestNumber,
        String targetRef) {
}
