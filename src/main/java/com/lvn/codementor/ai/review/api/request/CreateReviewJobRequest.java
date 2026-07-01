package com.lvn.codementor.ai.review.api.request;

/**
 * Request body for creating a review job. {@code reviewType} may be omitted (defaults to
 * FULL_REPOSITORY); unknown values are rejected with a validation error by the service.
 */
public record CreateReviewJobRequest(String reviewType) {
}
