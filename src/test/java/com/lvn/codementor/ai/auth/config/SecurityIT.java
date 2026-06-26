package com.lvn.codementor.ai.auth.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** Verifies the JWT auth filter + entry point on protected endpoints (doc 07 §10, ADR-009). */
class SecurityIT extends AbstractWebIT {

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/organizations/{orgId}/repositories", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/organizations/{orgId}/repositories", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer("not-a-valid-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedEndpoint_withValidToken_resolvesUser() throws Exception {
        ProvisioningOutcome outcome = provision("gh-" + UUID.randomUUID(), "access");
        // A valid token + membership in the user's own personal org resolves the user and returns 200.
        mockMvc.perform(get("/api/organizations/{orgId}/repositories", outcome.personalOrganizationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outcome.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
