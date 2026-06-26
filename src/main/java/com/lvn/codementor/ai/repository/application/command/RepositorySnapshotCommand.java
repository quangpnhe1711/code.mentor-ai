package com.lvn.codementor.ai.repository.application.command;

/**
 * Input for creating a repository snapshot.
 *
 * @param sourceRef branch/tag/ref to snapshot; {@code null} → the repository's default branch
 */
public record RepositorySnapshotCommand(String sourceRef) {
}
