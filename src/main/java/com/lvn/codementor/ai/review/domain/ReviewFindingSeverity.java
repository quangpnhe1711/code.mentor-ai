package com.lvn.codementor.ai.review.domain;

/** Severity of a review finding. Stored as text + CHECK. No findings are generated in this phase. */
public enum ReviewFindingSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
