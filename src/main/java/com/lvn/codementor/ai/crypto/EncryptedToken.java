package com.lvn.codementor.ai.crypto;

/**
 * Ciphertext plus the metadata needed to decrypt it later (doc 14 §7). Holds no plaintext.
 *
 * @param ciphertext authenticated ciphertext (IV prefixed)
 * @param keyVersion the key version used, enabling decryption after key rotation (ADR-011)
 * @param algorithm  the algorithm label, e.g. {@code AES_GCM}
 */
public record EncryptedToken(byte[] ciphertext, String keyVersion, String algorithm) {
}
