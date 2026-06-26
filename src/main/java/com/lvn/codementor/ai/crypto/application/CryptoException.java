package com.lvn.codementor.ai.crypto.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;

/**
 * Raised on any encryption/decryption or key-resolution failure. Maps to
 * {@link ErrorCode#CREDENTIAL_CRYPTO_ERROR}. The message is intentionally generic; the cause is
 * retained for server-side logging only (doc 09 §4, ADR-011 — never leak detail).
 */
public class CryptoException extends AppException {

    public CryptoException(String message, Throwable cause) {
        super(ErrorCode.CREDENTIAL_CRYPTO_ERROR, message, cause);
    }

    public CryptoException(String message) {
        super(ErrorCode.CREDENTIAL_CRYPTO_ERROR, message);
    }
}
