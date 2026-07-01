package com.lvn.codementor.ai.codeanalysis.domain;

/** Lifecycle of a sanitized analysis input (build → ready/failed). Stored as text + CHECK. */
public enum CodeAnalysisInputStatus {
    BUILDING,
    READY,
    FAILED
}
