package com.lvn.codementor.ai.repository.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * GitHub OAuth credentials authorizing CodeMentor to act on the user's behalf (doc 14 §3.5,
 * ADR-009/ADR-011). Tokens are stored as ciphertext only — this entity never exposes plaintext, and
 * the encrypted columns are never surfaced in API DTOs.
 *
 * <p>Writes go exclusively through {@link GitProviderCredentialStore} so encryption is never
 * bypassed.
 */
@Entity
@Table(name = "git_provider_connections")
public class GitProviderConnection extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private RepositoryProvider provider;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_account_login", nullable = false)
    private String providerAccountLogin;

    @Column(name = "provider_account_id", nullable = false)
    private String providerAccountId;

    @Column(name = "encrypted_access_token", nullable = false)
    private byte[] encryptedAccessToken;

    @Column(name = "encrypted_refresh_token")
    private byte[] encryptedRefreshToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "scopes")
    private String scopes;

    @Column(name = "key_version", nullable = false)
    private String keyVersion;

    @Column(name = "encryption_algorithm", nullable = false)
    private String encryptionAlgorithm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GitProviderConnectionStatus status;

    protected GitProviderConnection() {
        // for JPA
    }

    public GitProviderConnection(RepositoryProvider provider, UUID userId, String providerAccountLogin, String providerAccountId) {
        this.provider = provider;
        this.userId = userId;
        this.providerAccountLogin = providerAccountLogin;
        this.providerAccountId = providerAccountId;
        this.status = GitProviderConnectionStatus.CONNECTED;
    }

    /** Replace the stored ciphertext and connection metadata. Intended for the repository module's credential store only. */
    public void applyCredentials(
            String providerAccountLogin,
            byte[] encryptedAccessToken,
            byte[] encryptedRefreshToken,
            Instant tokenExpiresAt,
            String scopes,
            String keyVersion,
            String encryptionAlgorithm) {
        this.providerAccountLogin = providerAccountLogin;
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
        this.scopes = scopes;
        this.keyVersion = keyVersion;
        this.encryptionAlgorithm = encryptionAlgorithm;
        this.status = GitProviderConnectionStatus.CONNECTED;
    }

    public RepositoryProvider getProvider() {
        return provider;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProviderAccountLogin() {
        return providerAccountLogin;
    }

    public String getProviderAccountId() {
        return providerAccountId;
    }

    public Instant getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public String getScopes() {
        return scopes;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public GitProviderConnectionStatus getStatus() {
        return status;
    }

    // Accessors for the repository module's credential store decrypt path. Other modules must not import this.
    public byte[] encryptedAccessTokenBytes() {
        return encryptedAccessToken;
    }

    public byte[] encryptedRefreshTokenBytes() {
        return encryptedRefreshToken;
    }
}
