package com.lvn.codementor.ai.review.api.request;

/**
 * Request body for creating a review job. {@code reviewType} may be omitted (defaults to
 * FULL_REPOSITORY). PULL_REQUEST requires {@code targetPullRequestNumber}; BRANCH requires
 * {@code targetRef}.
 */
public record CreateReviewJobRequest(
        String reviewType,
        Integer targetPullRequestNumber,
        String targetRef) {
}
