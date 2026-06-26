package com.lvn.codementor.ai.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM implementation of {@link TokenEncryptor} (authenticated encryption, ADR-011).
 *
 * <p>Wire format of {@link EncryptedToken#ciphertext()} is {@code IV (12 bytes) || GCM ciphertext+tag}.
 * The GCM authentication tag means tampering or a wrong key is detected on decrypt.
 */
@Component
public class AesGcmTokenEncryptor implements TokenEncryptor {

    static final String ALGORITHM_LABEL = "AES_GCM";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final KeyProvider keyProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmTokenEncryptor(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public EncryptedToken encrypt(String plaintext) {
        try {
            String keyVersion = keyProvider.currentKeyVersion();
            SecretKey key = keyProvider.keyFor(keyVersion);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return new EncryptedToken(out, keyVersion, ALGORITHM_LABEL);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Token encryption failed", e);
        }
    }

    @Override
    public String decrypt(EncryptedToken token) {
        try {
            SecretKey key = keyProvider.keyFor(token.keyVersion());
            byte[] data = token.ciphertext();
            byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(data, IV_LENGTH_BYTES, data.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Token decryption failed", e);
        }
    }
}
