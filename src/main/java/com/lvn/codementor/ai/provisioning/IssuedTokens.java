package com.lvn.codementor.ai.provisioning;

import java.time.Instant;

/**
 * The platform tokens handed back to the client after provisioning. The access token is a JWT; the
 * refresh token is the raw (un-hashed) value returned exactly once.
 */
public record IssuedTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String accessTokenId) {
}
