package com.lvn.codementor.ai.github.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.github.application.GitHubRepositorySummary;
import com.lvn.codementor.ai.identity.domain.User;
import com.lvn.codementor.ai.identity.persistence.UserJpaRepository;
import com.lvn.codementor.ai.organization.domain.Organization;
import com.lvn.codementor.ai.organization.domain.OrganizationMember;
import com.lvn.codementor.ai.organization.domain.OrganizationRole;
import com.lvn.codementor.ai.organization.persistence.OrganizationJpaRepository;
import com.lvn.codementor.ai.organization.persistence.OrganizationMemberJpaRepository;
import com.lvn.codementor.ai.auth.application.JwtService;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** GET /api/github/repositories — JWT required, provider connection required, safe DTOs only. */
class GitHubRepositoriesApiIT extends AbstractWebIT {

    private static final String URL = "/api/github/repositories";

    @Autowired
    UserJpaRepository users;

    @Autowired
    OrganizationJpaRepository organizations;

    @Autowired
    OrganizationMemberJpaRepository members;

    @Autowired
    JwtService jwtService;

    @Test
    void requiresPlatformJwt() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void missingProviderConnectionReturnsConnectionRequired() throws Exception {
        // A provisioned-by-hand user with NO GitHub connection.
        User user = new User("ghNoConn-" + UUID.randomUUID(), "noconn", null, null, null);
        users.save(user);
        UUID userId = user.getId();
        Organization org = Organization.personalFor(userId, "No conn", "noconn-" + UUID.randomUUID());
        organizations.save(org);
        members.save(new OrganizationMember(org.getId(), userId, OrganizationRole.OWNER));
        String token = jwtService.issue(userId).token();

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_CONNECTION_REQUIRED"));
    }

    @Test
    void validConnectionReturnsSafeRepositoryDtos() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_access");
        when(gitHubRepositoryClient.listRepositories(anyString())).thenReturn(List.of(
                new GitHubRepositorySummary(
                        "12345", "octo", "hello", "octo/hello", "PUBLIC", "main", "https://github.com/octo/hello", false),
                new GitHubRepositorySummary(
                        "67890", "octo", "secret", "octo/secret", "PRIVATE", "main", "https://github.com/octo/secret", true)));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].externalRepoId").value("12345"))
                .andExpect(jsonPath("$.data[0].fullName").value("octo/hello"))
                .andExpect(jsonPath("$.data[1].visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data[1].isPrivate").value(true));
    }

    @Test
    void responseDoesNotExposeCredentialFields() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_super_secret_access");
        when(gitHubRepositoryClient.listRepositories(anyString())).thenReturn(List.of(
                new GitHubRepositorySummary(
                        "1", "octo", "r", "octo/r", "PUBLIC", "main", "https://github.com/octo/r", false)));

        MvcResult result = mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("gho_super_secret_access");
        assertThat(body)
                .doesNotContain("encrypted")
                .doesNotContain("accessToken")
                .doesNotContain("token_hash")
                .doesNotContain("tokenHash");
    }
}
