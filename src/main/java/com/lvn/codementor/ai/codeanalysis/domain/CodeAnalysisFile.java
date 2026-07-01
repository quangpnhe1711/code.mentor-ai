package com.lvn.codementor.ai.codeanalysis.domain;

/**
 * One sanitized file that survived selection and secret masking, held <strong>in memory only</strong>
 * while a review input is assembled and hashed. It is never persisted and never leaves the service:
 * neither the raw nor the sanitized content is stored in PostgreSQL or returned by the API.
 *
 * @param path             repository-relative POSIX path (safe to hash/count on)
 * @param sanitizedContent file text after secret masking (transient; used only for hashing/byte count)
 * @param maskedSecretCount number of secret occurrences replaced in this file
 */
public record CodeAnalysisFile(String path, String sanitizedContent, int maskedSecretCount) {
}
