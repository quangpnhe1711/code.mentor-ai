package com.lvn.codementor.ai.repository;

/** Repository lifecycle status (doc 03 status enumerations; doc 14 §3.4). */
public enum RepositoryStatus {
    ACTIVE,
    SYNCING,
    DISCONNECTED,
    FAILED,
    ARCHIVED
}
