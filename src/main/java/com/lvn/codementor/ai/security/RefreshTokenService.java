package com.lvn.codementor.ai.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues platform refresh tokens (ADR-009, doc 14 §3.6). The raw token is high-entropy random
 * material returned to the caller once; only its hash is persisted in {@code auth_refresh_tokens}.
 *
 * <p>Validation/rotation/revocation operations are intentionally minimal for the Foundation
 * baseline. A Redis deny-list for immediate access-token revocation is out of scope for Foundation.
 */
@Service
public class RefreshTokenService {

    private static final int RAW_TOKEN_BYTES = 32;

    private final AuthRefreshTokenJpaRepository refreshTokens;
    private final RefreshTokenHasher hasher;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            AuthRefreshTokenJpaRepository refreshTokens, RefreshTokenHasher hasher, JwtProperties jwtProperties) {
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.refreshTokenTtl = jwtProperties.refreshTokenTtl();
    }

    /** Issue and persist (hashed) a new refresh token for the user. */
    public IssuedRefreshToken issue(UUID userId) {
        String rawToken = generateRawToken();
        String tokenHash = hasher.hash(rawToken);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTokenTtl);

        AuthRefreshToken entity = new AuthRefreshToken(userId, tokenHash, now, expiresAt);
        AuthRefreshToken saved = refreshTokens.save(entity);

        return new IssuedRefreshToken(rawToken, expiresAt, saved.getId());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
