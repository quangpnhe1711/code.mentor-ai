package com.lvn.codementor.ai.repository.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits and workspace location for repository snapshots.
 *
 * @param maxFileSizeBytes per-file ceiling; larger files are skipped from the inventory
 * @param maxTotalBytes    total included-bytes ceiling; exceeding it fails the snapshot
 * @param maxFileCount     included-file-count ceiling; exceeding it fails the snapshot
 * @param workspaceRoot    root directory under which per-snapshot workspaces are created
 */
@ConfigurationProperties(prefix = "codementor.repository.snapshot")
public record RepositorySnapshotProperties(
        long maxFileSizeBytes, long maxTotalBytes, int maxFileCount, String workspaceRoot) {
}
