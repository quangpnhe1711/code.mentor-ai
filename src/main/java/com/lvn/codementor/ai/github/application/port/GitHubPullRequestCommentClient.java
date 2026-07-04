package com.lvn.codementor.ai.github.application.port;

/** Writes pull-request feedback to GitHub. */
public interface GitHubPullRequestCommentClient {

    /**
     * GitHub pull requests are issues for comments, so {@code issueNumber} is the pull-request number.
     * The access token must never be logged or returned.
     */
    void createIssueComment(String accessToken, String owner, String repo, int issueNumber, String body);
}
