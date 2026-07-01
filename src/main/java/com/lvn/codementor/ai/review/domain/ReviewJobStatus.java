package com.lvn.codementor.ai.review.domain;

/** Lifecycle of a review job. Stored as text + CHECK. Only QUEUED is reachable in this phase. */
public enum ReviewJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED
}
