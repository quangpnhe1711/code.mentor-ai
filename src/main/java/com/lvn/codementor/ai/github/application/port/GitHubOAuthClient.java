package com.lvn.codementor.ai.github.application.port;

import com.lvn.codementor.ai.github.application.GitHubTokenResult;

/**
 * Exchanges a GitHub OAuth authorization {@code code} for an access token (the server-side half of
 * the OAuth web flow). Implementations call GitHub over HTTPS and must never log the token or include
 * it (or the client secret) in exception messages.
 */
public interface GitHubOAuthClient {

    GitHubTokenResult exchangeCodeForToken(String code, String redirectUri);
}
