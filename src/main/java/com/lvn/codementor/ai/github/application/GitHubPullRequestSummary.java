package com.lvn.codementor.ai.github.application;

import java.time.Instant;

/**
 * Credential-free summary of a GitHub pull request visible to the connected account.
 */
public record GitHubPullRequestSummary(
        long number,
        String title,
        String state,
        boolean draft,
        String authorLogin,
        String headRef,
        String headSha,
        String baseRef,
        String baseSha,
        String htmlUrl,
        Instant createdAt,
        Instant updatedAt) {
}
