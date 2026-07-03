package com.lvn.codementor.ai.review.application.result;

import com.lvn.codementor.ai.review.domain.ReviewJob;

/** Outcome of a run attempt: the persisted {@link ReviewJob} in its terminal state (COMPLETED/FAILED). */
public record ReviewExecutionResult(ReviewJob job) {
}
