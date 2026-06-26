package com.lvn.codementor.ai.github.application;

import java.time.Instant;

/**
 * Result of a successful GitHub code→token exchange. Held only transiently in memory while
 * provisioning; the access/refresh tokens are encrypted before storage and never returned by any API.
 *
 * @param accessToken  GitHub OAuth access token
 * @param refreshToken GitHub OAuth refresh token; {@code null} for classic OAuth apps
 * @param scope        granted scopes (space- or comma-separated, as returned by GitHub); may be {@code null}
 * @param tokenType    e.g. {@code bearer}; may be {@code null}
 * @param expiresAt    access-token expiry; {@code null} when non-expiring
 */
public record GitHubTokenResult(
        String accessToken, String refreshToken, String scope, String tokenType, Instant expiresAt) {
}
