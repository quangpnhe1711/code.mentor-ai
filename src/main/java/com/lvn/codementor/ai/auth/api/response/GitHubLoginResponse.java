package com.lvn.codementor.ai.auth.api.response;

/**
 * Response of the GitHub login endpoint: the authorization URL the client should redirect the user
 * to. The CSRF {@code state} is embedded in the URL and held server-side for callback validation; the
 * client secret is never included.
 */
public record GitHubLoginResponse(String authorizationUrl) {
}
