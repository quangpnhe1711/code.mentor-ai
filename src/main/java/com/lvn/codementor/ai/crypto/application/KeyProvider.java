package com.lvn.codementor.ai.crypto.application;

import javax.crypto.SecretKey;

/**
 * Resolves encryption keys by version and exposes the current version for new encryptions
 * (doc 14 §7, ADR-011). The concrete key-management backend (env/config vs. external KMS) and the
 * rotation policy remain <strong>TBD</strong>; callers depend only on this port.
 */
public interface KeyProvider {

    /** Key version to use for new encryptions. */
    String currentKeyVersion();

    /**
     * Resolve the key for a given version.
     *
     * @throws CryptoException if the version is unknown/retired
     */
    SecretKey keyFor(String keyVersion);
}
