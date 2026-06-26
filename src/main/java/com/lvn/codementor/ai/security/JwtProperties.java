package com.lvn.codementor.ai.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform JWT configuration (ADR-009).
 *
 * <p><strong>TBD:</strong> access/refresh lifetimes are configurable here but their final values,
 * and the JWT signing-key management strategy (currently a configured shared secret), remain open
 * (doc 14 §6.2). For Foundation the secret is supplied via config/environment for local/dev.
 *
 * @param issuer          the {@code iss} claim value
 * @param secretBase64    base64-encoded HMAC-SHA256 signing secret (>= 256 bits)
 * @param accessTokenTtl  access-token lifetime (kept short)
 * @param refreshTokenTtl refresh-token lifetime
 */
@ConfigurationProperties(prefix = "codementor.security.jwt")
public record JwtProperties(String issuer, String secretBase64, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
