package com.lvn.codementor.ai.auth.api.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of the dev provisioning endpoint: the resolved platform user, their personal org, and the
 * issued platform tokens. The GitHub OAuth token is never included.
 */
public record ProvisioningResponse(
        UUID userId,
        UUID personalOrganizationId,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}
