package com.lvn.codementor.ai.security;

import java.time.Instant;
import java.util.UUID;

/**
 * A freshly issued refresh token. The {@code rawToken} is returned to the caller exactly once and is
 * never persisted; only its hash is stored.
 */
public record IssuedRefreshToken(String rawToken, Instant expiresAt, UUID id) {
}
