package com.lvn.codementor.ai.security;

import java.time.Instant;

/** A freshly issued platform access JWT and its key metadata. */
public record IssuedAccessToken(String token, Instant expiresAt, String jti) {
}
