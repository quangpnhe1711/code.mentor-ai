package com.lvn.codementor.ai.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvn.codementor.ai.auth.application.FirstLoginProvisioningService;
import com.lvn.codementor.ai.auth.application.GitHubOAuthResult;
import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Base for web-layer integration tests: full context + MockMvc (with Spring Security filters). */
@AutoConfigureMockMvc
public abstract class AbstractWebIT extends AbstractPostgresIT {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected FirstLoginProvisioningService provisioningService;

    /** Provision a user (creating personal org, membership, and a GitHub connection) and return tokens. */
    protected ProvisioningOutcome provision(String githubUserId, String accessToken) {
        return provisioningService.provisionFromGitHub(new GitHubOAuthResult(
                githubUserId,
                githubUserId + "-login",
                null,
                "Display Name",
                null,
                accessToken,
                "refresh-" + githubUserId,
                null,
                "repo,read:user"));
    }

    protected String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
