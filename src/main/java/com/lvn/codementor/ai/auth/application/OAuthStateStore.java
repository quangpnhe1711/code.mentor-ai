package com.lvn.codementor.ai.auth.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Issues and validates single-use OAuth {@code state} values for CSRF protection on the GitHub
 * callback. A state is consumed exactly once and expires after a short TTL.
 *
 * <p><strong>TBD:</strong> this is an in-memory store — correct for a single instance / local dev.
 * A multi-instance deployment needs a shared store (e.g. signed cookie or Redis); intentionally not
 * introduced here (no Redis in this phase).
 */
@Component
public class OAuthStateStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Instant> states = new ConcurrentHashMap<>();

    /** Generate, store, and return a fresh state token. */
    public String issue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(state, Instant.now().plus(TTL));
        return state;
    }

    /**
     * Validate and consume a state. Returns {@code true} only if the state was issued, unexpired, and
     * not already consumed. Expired/used/unknown states return {@code false}.
     */
    public boolean consume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        Instant expiry = states.remove(state);
        return expiry != null && expiry.isAfter(Instant.now());
    }
}
