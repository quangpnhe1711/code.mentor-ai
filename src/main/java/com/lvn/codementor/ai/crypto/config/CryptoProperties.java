package com.lvn.codementor.ai.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Crypto configuration (doc 14 §7).
 *
 * <p><strong>TBD (ADR-011):</strong> for Foundation the key is supplied via config/environment for
 * local/dev only. The production key-management backend (external KMS/secrets manager) and the key
 * rotation policy are still open. This class isolates that concern behind {@link ConfigKeyProvider}.
 *
 * @param keyVersion   label of the current key (stored on each encrypted row)
 * @param aesKeyBase64 base64-encoded AES key for {@code keyVersion} (256-bit recommended)
 */
@ConfigurationProperties(prefix = "codementor.crypto")
public record CryptoProperties(String keyVersion, String aesKeyBase64) {
}
