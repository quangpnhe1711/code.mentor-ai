package com.lvn.codementor.ai.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes returned in the API envelope {@code error.code} field.
 *
 * <p>Source of truth: docs/08-error-handling.md (catalog). The frontend keys behaviour off the
 * {@code code}, not the message text (doc 07 §2).
 *
 * <p><strong>Platform vs. provider auth (ADR-009):</strong> {@link #UNAUTHENTICATED} is for
 * <em>platform JWT</em> authentication failures (missing/invalid/expired CodeMentor token), whereas
 * {@link #TOKEN_EXPIRED} is reserved for the <em>Git provider</em> OAuth token expiring
 * (BR-REP-004). They are distinct credentials.
 */
public enum ErrorCode {

    /** Request failed validation. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    /** Missing/invalid/expired <em>platform</em> JWT. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),

    /** Role/permission denied. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** Requested resource does not exist (or is not visible to the caller). */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** <em>Git provider</em> OAuth token expired; sync halted (BR-REP-004). */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),

    /** Repository already imported in the organization (BR-REP-002). */
    REPOSITORY_ALREADY_IMPORTED(HttpStatus.CONFLICT),

    /** No provider access to the repository (BR-REP-001). */
    REPOSITORY_ACCESS_DENIED(HttpStatus.FORBIDDEN),

    /** No GitHub provider connection exists for the user; required before import/sync (BR-REP-003). */
    PROVIDER_CONNECTION_REQUIRED(HttpStatus.CONFLICT),

    /** OAuth callback {@code state} missing/unknown/expired — likely CSRF or a stale flow. */
    GITHUB_OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST),

    /** A call to the GitHub API failed upstream (token exchange, profile, or repo listing). */
    GITHUB_INTEGRATION_ERROR(HttpStatus.BAD_GATEWAY),

    /** Provider-token encryption/decryption failed; detail is never leaked (ADR-011). */
    CREDENTIAL_CRYPTO_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    /** First-login personal-org provisioning failed; transaction rolled back (ADR-010). */
    PROVISIONING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),

    /** A snapshot must be READY before a sanitized analysis input can be built from it. */
    SNAPSHOT_NOT_READY(HttpStatus.CONFLICT),

    /** Building the sanitized analysis input failed; detail is safe (no path/content/secret). */
    CODE_ANALYSIS_INPUT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),

    /** Unexpected failure. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
