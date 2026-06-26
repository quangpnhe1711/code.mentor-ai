package com.lvn.codementor.ai.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.support.AbstractWebIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** When dev provisioning is disabled by property, the endpoint is not registered (404). */
@TestPropertySource(properties = "codementor.auth.dev-provisioning-enabled=false")
class DevProvisioningDisabledIT extends AbstractWebIT {

    @Test
    void provisionEndpointIsNotRegisteredWhenDisabled() throws Exception {
        String body = """
                {"githubUserId":"123","githubLogin":"x","accessToken":"t"}
                """;
        mockMvc.perform(post("/api/auth/github/provision/dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
