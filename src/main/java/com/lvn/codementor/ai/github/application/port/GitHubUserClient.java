package com.lvn.codementor.ai.github.application.port;

import com.lvn.codementor.ai.github.application.GitHubUserProfile;

/**
 * Fetches the GitHub profile of the account that owns an access token. The access token is used only
 * as an {@code Authorization} header at the GitHub boundary; it is never logged or persisted here.
 */
public interface GitHubUserClient {

    GitHubUserProfile getCurrentUser(String accessToken);
}
