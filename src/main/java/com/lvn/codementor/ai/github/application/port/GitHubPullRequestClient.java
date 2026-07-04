package com.lvn.codementor.ai.github.application.port;

import com.lvn.codementor.ai.github.application.GitHubPullRequestSummary;
import java.util.List;

/**
 * Reads pull requests from GitHub on behalf of a connected account.
 */
public interface GitHubPullRequestClient {

    List<GitHubPullRequestSummary> listPullRequests(String accessToken, String owner, String repo);
}
