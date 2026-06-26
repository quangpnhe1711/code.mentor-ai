package com.lvn.codementor.ai.auth.api;
import com.lvn.codementor.ai.auth.api.response.ProvisioningResponse;
import com.lvn.codementor.ai.auth.api.request.DevProvisionRequest;

import com.lvn.codementor.ai.auth.application.FirstLoginProvisioningService;
import com.lvn.codementor.ai.auth.application.GitHubOAuthResult;
import com.lvn.codementor.ai.auth.application.IssuedTokens;
import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <strong>DEV/LOCAL ONLY.</strong> Exposes {@link FirstLoginProvisioningService} over HTTP so a
 * client can obtain platform tokens without a real GitHub OAuth exchange (out of scope this phase).
 *
 * <p>Guarded by {@code codementor.auth.dev-provisioning-enabled}: when the property is not
 * {@code true}, this controller is not registered and the endpoint returns 404. It is a public
 * endpoint (no JWT required) — see {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth/github")
@ConditionalOnProperty(prefix = "codementor.auth", name = "dev-provisioning-enabled", havingValue = "true")
public class DevProvisioningController {

    private final FirstLoginProvisioningService provisioningService;

    public DevProvisioningController(FirstLoginProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @PostMapping("/provision/dev")
    public ApiResponse<ProvisioningResponse> provision(@Valid @RequestBody DevProvisionRequest request) {
        GitHubOAuthResult oauthResult = new GitHubOAuthResult(
                request.githubUserId(),
                request.githubLogin(),
                request.email(),
                request.displayName(),
                request.avatarUrl(),
                request.accessToken(),
                request.refreshToken(),
                request.tokenExpiresAt(),
                request.scopes());

        ProvisioningOutcome outcome = provisioningService.provisionFromGitHub(oauthResult);
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
