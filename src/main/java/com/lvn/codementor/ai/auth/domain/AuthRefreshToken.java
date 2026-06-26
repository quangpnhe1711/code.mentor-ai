package com.lvn.codementor.ai.auth.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A platform refresh token, stored as a hash only (doc 14 §3.6, ADR-009). The raw token is never
 * persisted. A token is usable only while {@code revokedAt} is null and {@code expiresAt} is in the
 * future; on rotation the old row is revoked and {@code replacedByTokenId} points to its successor.
 */
@Entity
@Table(name = "auth_refresh_tokens")
public class AuthRefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    protected AuthRefreshToken() {
        // for JPA
    }

    public AuthRefreshToken(UUID userId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /** Revoke this token, optionally recording the successor created during rotation. */
    public void revoke(Instant when, UUID replacedByTokenId) {
        this.revokedAt = when;
        this.replacedByTokenId = replacedByTokenId;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedByTokenId() {
        return replacedByTokenId;
    }
}
