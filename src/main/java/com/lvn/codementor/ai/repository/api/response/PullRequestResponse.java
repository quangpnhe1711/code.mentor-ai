package com.lvn.codementor.ai.repository.api.response;

import com.lvn.codementor.ai.github.application.GitHubPullRequestSummary;
import java.time.Instant;

/**
 * Public pull request DTO. Contains provider metadata only; never credential fields.
 */
public record PullRequestResponse(
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

    public static PullRequestResponse from(GitHubPullRequestSummary pr) {
        return new PullRequestResponse(
                pr.number(),
                pr.title(),
                pr.state(),
                pr.draft(),
                pr.authorLogin(),
                pr.headRef(),
                pr.headSha(),
                pr.baseRef(),
                pr.baseSha(),
                pr.htmlUrl(),
                pr.createdAt(),
                pr.updatedAt());
    }
}
