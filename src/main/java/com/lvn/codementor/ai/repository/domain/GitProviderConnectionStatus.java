package com.lvn.codementor.ai.repository.domain;

/**
 * Provider-connection status (doc 14 §3.5) — distinct from {@link RepositoryStatus}.
 *
 * <ul>
 *   <li>{@code CONNECTED} — usable credentials.</li>
 *   <li>{@code EXPIRED} — token expiry is known (BR-REP-004).</li>
 *   <li>{@code DISCONNECTED} — provider access invalid/revoked/otherwise unusable (BR-REP-005).</li>
 * </ul>
 *
 * Dependent repositories must not sync unless the connection is {@code CONNECTED}.
 */
public enum GitProviderConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    EXPIRED
}
