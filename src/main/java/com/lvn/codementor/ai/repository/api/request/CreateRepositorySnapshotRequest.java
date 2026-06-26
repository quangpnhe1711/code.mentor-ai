package com.lvn.codementor.ai.repository.api.request;

/**
 * Request body for creating a snapshot. All fields optional.
 *
 * @param sourceRef branch/tag/ref to snapshot; {@code null}/absent → default branch
 */
public record CreateRepositorySnapshotRequest(String sourceRef) {
}
