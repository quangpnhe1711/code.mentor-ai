package com.lvn.codementor.ai.github.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub integration configuration (real OAuth + API).
 *
 * <p>The {@code clientSecret} is used only server-side for the code→token exchange and is never
 * returned by any endpoint. {@code apiBaseUrl}/{@code webBaseUrl} are overridable so tests can point
 * at a stub instead of github.com. Real secrets must come from the environment/secret manager — the
 * committed config holds placeholders only.
 *
 * @param clientId            GitHub OAuth app client id
 * @param clientSecret        GitHub OAuth app client secret (never exposed)
 * @param redirectUri         OAuth callback URL registered with the GitHub app (points at this backend)
 * @param frontendRedirectUri where the callback sends the browser after login, with the token in the URL
 *                            fragment (the SPA route that consumes it)
 * @param scopes              requested OAuth scopes (comma-separated)
 * @param apiBaseUrl          GitHub REST API base (default {@code https://api.github.com})
 * @param webBaseUrl          GitHub web base for authorize/token (default {@code https://github.com})
 */
@ConfigurationProperties(prefix = "codementor.github")
public record GitHubProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String frontendRedirectUri,
        String scopes,
        String apiBaseUrl,
        String webBaseUrl) {
}
