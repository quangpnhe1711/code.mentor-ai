package com.lvn.codementor.ai.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * Request body for the <strong>dev-only</strong> provisioning endpoint (doc 14 §5). It stands in for
 * a real GitHub OAuth exchange, which is out of scope for this phase.
 *
 * <p>{@code providerAccountId}/{@code providerAccountLogin} are accepted for API fidelity; for GitHub
 * they equal the user id/login, which is what provisioning uses for the connection.
 */
public record DevProvisionRequest(
        @NotBlank String githubUserId,
        @NotBlank String githubLogin,
        String email,
        String displayName,
        String avatarUrl,
        String providerAccountId,
        String providerAccountLogin,
        @NotBlank String accessToken,
        String refreshToken,
        Instant tokenExpiresAt,
        String scopes) {
}
