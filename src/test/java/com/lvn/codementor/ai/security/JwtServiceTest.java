package com.lvn.codementor.ai.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for the platform JWT (doc 14 §6.1): required claims are present and secrets are
 * excluded. No Spring context or database is needed.
 */
class JwtServiceTest {

    // Test-only HMAC-SHA256 secret (48 bytes, base64 -> >= 256 bits).
    private static final String SECRET_BASE64 = "8euJRHXTSgfBeaaIt9oTdM7FsZkt3b6dRzZ2sQBJvvP73gdFE3QpM45EzYPVdVa/";

    private final JwtService jwtService = new JwtService(
            new JwtProperties("codementor-ai-test", SECRET_BASE64, Duration.ofMinutes(15), Duration.ofDays(30)));

    @Test
    void issuedTokenContainsRequiredClaims() {
        UUID userId = UUID.randomUUID();

        IssuedAccessToken issued = jwtService.issue(userId);
        Claims claims = jwtService.parse(issued.token()).getPayload();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getIssuer()).isEqualTo("codementor-ai-test");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getId()).isEqualTo(issued.jti());
    }

    @Test
    void issuedTokenExcludesSecrets() {
        Claims claims = jwtService.parse(jwtService.issue(UUID.randomUUID()).token()).getPayload();

        // Only standard claims; no provider token, refresh token, or other secrets.
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "iss", "iat", "exp", "jti");
        assertThat(claims.get("githubToken")).isNull();
        assertThat(claims.get("refreshToken")).isNull();
        assertThat(claims.get("accessToken")).isNull();
    }
}
