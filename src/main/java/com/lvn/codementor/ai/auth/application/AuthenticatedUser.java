package com.lvn.codementor.ai.auth.application;

import java.util.UUID;

/**
 * The authenticated platform principal placed into the Spring Security context (doc 14 §6.1).
 *
 * <p>Intentionally minimal: it carries only the platform {@code userId}. It must never hold the
 * GitHub OAuth token, refresh token, encrypted values, provider secrets, or unnecessary PII.
 */
public record AuthenticatedUser(UUID userId) {
}
