package com.lvn.codementor.ai.crypto;

/**
 * Symmetric authenticated encryption of secret material (doc 14 §7). Has no domain knowledge.
 * Failures surface as {@link CryptoException} and must never leak key material or plaintext.
 */
public interface TokenEncryptor {

    /** Encrypt plaintext with the current key, returning ciphertext + key version + algorithm. */
    EncryptedToken encrypt(String plaintext);

    /** Decrypt a previously produced token using its recorded key version/algorithm. */
    String decrypt(EncryptedToken token);
}
