package com.lvn.codementor.ai.repository.domain;

/** Lifecycle of a repository snapshot (clone → scan → ready/failed). Stored as text + CHECK. */
public enum RepositorySnapshotStatus {
    PENDING,
    CLONING,
    SCANNING,
    READY,
    FAILED
}
