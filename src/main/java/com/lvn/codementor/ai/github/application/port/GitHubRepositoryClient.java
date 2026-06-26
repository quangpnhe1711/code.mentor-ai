package com.lvn.codementor.ai.github.application.port;

import com.lvn.codementor.ai.github.application.GitHubRepositorySummary;
import java.util.List;

/**
 * Reads repositories from the GitHub API on behalf of a connected account.
 *
 * <p>{@link #verifyRepositoryAccess} is the access check used by the import flow: it must complete
 * normally when the account can see the repository and raise the appropriate {@code AppException}
 * ({@code REPOSITORY_ACCESS_DENIED} / {@code TOKEN_EXPIRED}) otherwise. The access token is used only
 * at the HTTP boundary and is never logged or echoed in errors.
 */
public interface GitHubRepositoryClient {

    List<GitHubRepositorySummary> listRepositories(String accessToken);

    GitHubRepositorySummary getRepository(String accessToken, String externalRepoId);

    void verifyRepositoryAccess(String accessToken, String externalRepoId);
}
