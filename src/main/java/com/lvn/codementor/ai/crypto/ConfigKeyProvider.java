package com.lvn.codementor.ai.crypto;

import java.util.Base64;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Config/environment-based {@link KeyProvider} for local/dev (doc 14 §7).
 *
 * <p>TODO/TBD (ADR-011): replace with an external KMS/secrets-manager-backed provider and a defined
 * rotation policy before production. The {@link KeyProvider} port keeps call sites unchanged when
 * that swap happens. Multiple key versions can be supported by extending {@link #keys}; for
 * Foundation a single current version is configured.
 */
@Component
public class ConfigKeyProvider implements KeyProvider {

    private final String currentKeyVersion;
    private final Map<String, SecretKey> keys;

    public ConfigKeyProvider(CryptoProperties properties) {
        this.currentKeyVersion = properties.keyVersion();
        byte[] keyBytes = Base64.getDecoder().decode(properties.aesKeyBase64());
        this.keys = Map.of(currentKeyVersion, new SecretKeySpec(keyBytes, "AES"));
    }

    @Override
    public String currentKeyVersion() {
        return currentKeyVersion;
    }

    @Override
    public SecretKey keyFor(String keyVersion) {
        SecretKey key = keys.get(keyVersion);
        if (key == null) {
            throw new CryptoException("Unknown encryption key version");
        }
        return key;
    }
}
