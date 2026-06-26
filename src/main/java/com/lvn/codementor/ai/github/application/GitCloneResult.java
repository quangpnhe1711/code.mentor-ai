package com.lvn.codementor.ai.github.application;

/**
 * Outcome of a successful clone.
 *
 * @param commitSha   the checked-out HEAD commit sha (may be {@code null} if undetermined)
 * @param resolvedRef the ref that was checked out (may be {@code null} for the default branch)
 */
public record GitCloneResult(String commitSha, String resolvedRef) {
}
