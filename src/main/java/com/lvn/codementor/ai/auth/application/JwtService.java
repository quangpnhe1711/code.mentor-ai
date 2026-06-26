package com.lvn.codementor.ai.auth.application;
import com.lvn.codementor.ai.auth.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates the stateless platform access JWT (ADR-009, doc 14 §6).
 *
 * <p>Claims included: {@code sub} (platform user UUID), {@code iss}, {@code iat}, {@code exp},
 * {@code jti}. Claims deliberately <strong>excluded</strong>: the GitHub OAuth token, the refresh
 * token, provider secrets, encrypted values, and unnecessary PII (doc 14 §6.1).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final java.time.Duration accessTokenTtl;

    public JwtService(JwtProperties properties) {
        byte[] secret = Base64.getDecoder().decode(properties.secretBase64());
        this.signingKey = Keys.hmacShaKeyFor(secret);
        this.issuer = properties.issuer();
        this.accessTokenTtl = properties.accessTokenTtl();
    }

    /** Issue an access token for the given platform user. */
    public IssuedAccessToken issue(UUID userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(jti)
                .signWith(signingKey)
                .compact();

        return new IssuedAccessToken(token, expiresAt, jti);
    }

    /** Verify signature, issuer and expiry, returning the parsed claims. Throws on invalid tokens. */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
    }
}
