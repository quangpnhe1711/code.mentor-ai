package com.lvn.codementor.ai.auth.api;

import com.lvn.codementor.ai.auth.api.response.GitHubLoginResponse;
import com.lvn.codementor.ai.auth.api.response.ProvisioningResponse;
import com.lvn.codementor.ai.auth.application.GitHubAuthService;
import com.lvn.codementor.ai.auth.application.IssuedTokens;
import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real GitHub OAuth web flow (public endpoints — no platform JWT required).
 *
 * <ul>
 *   <li>{@code GET /api/auth/github/login} — returns the GitHub authorization URL (with CSRF state).</li>
 *   <li>{@code GET /api/auth/github/callback} — completes the exchange and returns platform tokens.</li>
 * </ul>
 *
 * The GitHub access/refresh tokens are never returned; only the platform access + refresh tokens are.
 */
@RestController
@RequestMapping("/api/auth/github")
public class GitHubOAuthController {

    private final GitHubAuthService gitHubAuthService;

    public GitHubOAuthController(GitHubAuthService gitHubAuthService) {
        this.gitHubAuthService = gitHubAuthService;
    }

    @GetMapping("/login")
    public ApiResponse<GitHubLoginResponse> login() {
        String url = gitHubAuthService.buildAuthorizationUrl();
        return ApiResponse.ok(new GitHubLoginResponse(url), UUID.randomUUID().toString());
    }

    @GetMapping("/callback")
    public ApiResponse<ProvisioningResponse> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state) {
        ProvisioningOutcome outcome = gitHubAuthService.handleCallback(code, state);
        IssuedTokens tokens = outcome.tokens();
        ProvisioningResponse data = new ProvisioningResponse(
                outcome.userId(),
                outcome.personalOrganizationId(),
                tokens.accessToken(),
                tokens.accessTokenExpiresAt(),
                tokens.refreshToken(),
                tokens.refreshTokenExpiresAt());
        return ApiResponse.ok(data, UUID.randomUUID().toString());
    }
}
