package com.lvn.codementor.ai.auth.application;

import java.time.Instant;

/**
 * Represents the result of a <em>successful</em> GitHub OAuth exchange (doc 14 §5).
 *
 * <p>The provisioning service operates on this DTO rather than performing the network exchange
 * itself — the code→token exchange happens upstream (a real OAuth callback handler is out of scope
 * for this Foundation slice). For GitHub, the account id equals the user id and the account login
 * equals the user login.
 *
 * @param githubUserId   stable GitHub id (join key)
 * @param githubLogin    GitHub username (mutable; for display)
 * @param email          may be {@code null} (absent from profile)
 * @param displayName    may be {@code null}
 * @param avatarUrl      may be {@code null}
 * @param accessToken    GitHub OAuth access token (encrypted before storage)
 * @param refreshToken   GitHub OAuth refresh token; may be {@code null}
 * @param tokenExpiresAt token expiry; may be {@code null}
 * @param scopes         granted OAuth scopes; may be {@code null}
 */
public record GitHubOAuthResult(
        String githubUserId,
        String githubLogin,
        String email,
        String displayName,
        String avatarUrl,
        String accessToken,
        String refreshToken,
        Instant tokenExpiresAt,
        String scopes) {
}
