package com.lvn.codementor.ai.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvn.codementor.ai.auth.application.FirstLoginProvisioningService;
import com.lvn.codementor.ai.auth.application.GitHubOAuthResult;
import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.github.application.port.GitHubOAuthClient;
import com.lvn.codementor.ai.github.application.port.GitHubRepositoryClient;
import com.lvn.codementor.ai.github.application.port.GitHubUserClient;
import com.lvn.codementor.ai.github.application.port.GitRepositoryCloneClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for web-layer integration tests: full context + MockMvc (with Spring Security filters).
 *
 * <p>The GitHub HTTP clients are replaced by Mockito mocks so tests never call real GitHub. With the
 * default mock behaviour, {@code verifyRepositoryAccess} is a no-op (import succeeds) and
 * {@code listRepositories} returns an empty list; individual tests stub as needed.
 */
@AutoConfigureMockMvc
public abstract class AbstractWebIT extends AbstractPostgresIT {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected FirstLoginProvisioningService provisioningService;

    @MockitoBean
    protected GitHubOAuthClient gitHubOAuthClient;

    @MockitoBean
    protected GitHubUserClient gitHubUserClient;

    @MockitoBean
    protected GitHubRepositoryClient gitHubRepositoryClient;

    @MockitoBean
    protected GitRepositoryCloneClient gitRepositoryCloneClient;

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
