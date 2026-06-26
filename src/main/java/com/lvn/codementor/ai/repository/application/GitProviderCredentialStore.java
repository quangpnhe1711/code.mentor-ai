package com.lvn.codementor.ai.repository.application;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;

import com.lvn.codementor.ai.crypto.application.EncryptedToken;
import com.lvn.codementor.ai.crypto.application.TokenEncryptor;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The only path for persisting and reading Git provider credentials (doc 14 §7). Encrypts on write
 * and decrypts on read so encryption is centralized and can never be bypassed (ADR-011). Plaintext
 * tokens are never persisted, logged, or returned to other layers except via
 * {@link #decryptAccessToken} at the provider-call boundary.
 */
@Service
public class GitProviderCredentialStore {

    private final GitProviderConnectionJpaRepository connections;
    private final TokenEncryptor tokenEncryptor;

    public GitProviderCredentialStore(GitProviderConnectionJpaRepository connections, TokenEncryptor tokenEncryptor) {
        this.connections = connections;
        this.tokenEncryptor = tokenEncryptor;
    }

    /**
     * Create or update the connection for {@code (provider, providerAccountId, userId)}, storing the
     * tokens encrypted. Re-authentication refreshes the ciphertext, key version and expiry.
     *
     * @param refreshToken may be {@code null} (GitHub OAuth apps may not issue one)
     * @param tokenExpiresAt may be {@code null} (non-expiring token)
     */
    public GitProviderConnection saveOrUpdate(
            UUID userId,
            RepositoryProvider provider,
            String providerAccountLogin,
            String providerAccountId,
            String accessToken,
            String refreshToken,
            Instant tokenExpiresAt,
            String scopes) {

        EncryptedToken encryptedAccess = tokenEncryptor.encrypt(accessToken);
        byte[] encryptedRefresh =
                refreshToken == null ? null : tokenEncryptor.encrypt(refreshToken).ciphertext();

        GitProviderConnection connection = connections
                .findByProviderAndProviderAccountIdAndUserId(provider, providerAccountId, userId)
                .orElseGet(() ->
                        new GitProviderConnection(provider, userId, providerAccountLogin, providerAccountId));

        connection.applyCredentials(
                providerAccountLogin,
                encryptedAccess.ciphertext(),
                encryptedRefresh,
                tokenExpiresAt,
                scopes,
                encryptedAccess.keyVersion(),
                encryptedAccess.algorithm());

        return connections.save(connection);
    }

    /** Decrypt the access token. Intended for use only at the moment of a provider call. */
    public String decryptAccessToken(UUID connectionId) {
        GitProviderConnection connection = connections
                .findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        EncryptedToken token = new EncryptedToken(
                connection.encryptedAccessTokenBytes(),
                connection.getKeyVersion(),
                connection.getEncryptionAlgorithm());
        return tokenEncryptor.decrypt(token);
    }
}
