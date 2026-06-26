package com.lvn.codementor.ai.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Deterministic SHA-256 hashing of refresh tokens so a presented token can be looked up by hash
 * while only the hash is ever stored (doc 14 §3.6/§4.7).
 *
 * <p>A deterministic hash (not a salted password hash like bcrypt) is required because lookup is by
 * {@code token_hash}. The raw tokens carry 256 bits of entropy, so SHA-256 is sufficient against
 * brute force. TODO: a keyed HMAC with a server-side pepper would add defense in depth.
 */
@Component
public class RefreshTokenHasher {

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
