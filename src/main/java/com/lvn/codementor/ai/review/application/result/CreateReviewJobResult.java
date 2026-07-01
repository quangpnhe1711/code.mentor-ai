package com.lvn.codementor.ai.review.application.result;

import com.lvn.codementor.ai.review.domain.ReviewJob;

/** Outcome of a create attempt: the persisted {@link ReviewJob} aggregate (QUEUED, metadata only). */
public record CreateReviewJobResult(ReviewJob job) {
}
