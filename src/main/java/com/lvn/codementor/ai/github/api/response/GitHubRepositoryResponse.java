package com.lvn.codementor.ai.github.api.response;

import com.lvn.codementor.ai.github.application.GitHubRepositorySummary;

/**
 * A GitHub repository as returned to API clients. Credential-free by construction.
 */
public record GitHubRepositoryResponse(
        String externalRepoId,
        String ownerLogin,
        String name,
        String fullName,
        String visibility,
        String defaultBranch,
        String htmlUrl,
        boolean isPrivate) {

    public static GitHubRepositoryResponse from(GitHubRepositorySummary s) {
        return new GitHubRepositoryResponse(
                s.externalRepoId(),
                s.ownerLogin(),
                s.name(),
                s.fullName(),
                s.visibility(),
                s.defaultBranch(),
                s.htmlUrl(),
                s.isPrivate());
    }
}
