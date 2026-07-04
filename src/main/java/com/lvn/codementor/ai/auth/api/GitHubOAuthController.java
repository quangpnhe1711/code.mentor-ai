package com.lvn.codementor.ai.auth.api;

import com.lvn.codementor.ai.auth.api.response.GitHubLoginResponse;
import com.lvn.codementor.ai.auth.application.GitHubAuthService;
import com.lvn.codementor.ai.auth.application.IssuedTokens;
import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.github.config.GitHubProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real GitHub OAuth web flow (public endpoints — no platform JWT required).
 *
 * <ul>
 *   <li>{@code GET /api/auth/github/login} — returns the GitHub authorization URL (with CSRF state).</li>
 *   <li>{@code GET /api/auth/github/callback} — completes the exchange and redirects the browser back
 *       to the SPA ({@code codementor.github.frontend-redirect-uri}) with the platform access token in
 *       the URL fragment, or {@code #error=...} on failure.</li>
 * </ul>
 *
 * The GitHub access/refresh tokens are never returned; only the platform access token is, and it is put
 * in the URL fragment (never the query string) so it is not sent to servers or written to access logs.
 */
@RestController
@RequestMapping("/api/auth/github")
public class GitHubOAuthController {

    private final GitHubAuthService gitHubAuthService;
    private final GitHubProperties properties;

    public GitHubOAuthController(GitHubAuthService gitHubAuthService, GitHubProperties properties) {
        this.gitHubAuthService = gitHubAuthService;
        this.properties = properties;
    }

    @GetMapping("/login")
    public ApiResponse<GitHubLoginResponse> login() {
        String url = gitHubAuthService.buildAuthorizationUrl();
        return ApiResponse.ok(new GitHubLoginResponse(url), UUID.randomUUID().toString());
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state) {
        String target;
        try {
            ProvisioningOutcome outcome = gitHubAuthService.handleCallback(code, state);
            IssuedTokens tokens = outcome.tokens();
            target = properties.frontendRedirectUri()
                    + "#token=" + enc(tokens.accessToken())
                    + "&org=" + enc(outcome.personalOrganizationId().toString())
                    + "&userId=" + enc(outcome.userId().toString());
        } catch (AppException e) {
            // Redirect the browser back to the SPA with a readable error instead of leaving the user
            // on a backend JSON error page.
            target = properties.frontendRedirectUri() + "#error=" + enc(e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
