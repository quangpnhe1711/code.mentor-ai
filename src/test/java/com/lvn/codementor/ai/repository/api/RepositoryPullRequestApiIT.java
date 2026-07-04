package com.lvn.codementor.ai.repository.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.github.application.GitHubPullRequestSummary;
import com.lvn.codementor.ai.repository.api.request.ImportRepositoryRequest;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** GET /repositories/{repoId}/pull-requests — scoped PR listing for imported repositories. */
class RepositoryPullRequestApiIT extends AbstractWebIT {

    @Test
    void requiresPlatformJwt() throws Exception {
        mockMvc.perform(get("/api/organizations/{orgId}/repositories/{repoId}/pull-requests",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void memberCanListPullRequestsForImportedRepository() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_access");
        UUID repositoryId = importRepository(user, user.personalOrganizationId(), "ext-" + UUID.randomUUID());
        when(gitHubPullRequestClient.listPullRequests(anyString(), eq("owner"), eq("repo")))
                .thenReturn(List.of(new GitHubPullRequestSummary(
                        42,
                        "Improve review pipeline",
                        "open",
                        false,
                        "octocat",
                        "feature/review",
                        "abc123",
                        "main",
                        "def456",
                        "https://github.com/owner/repo/pull/42",
                        Instant.parse("2026-07-01T10:00:00Z"),
                        Instant.parse("2026-07-02T10:00:00Z"))));

        mockMvc.perform(get("/api/organizations/{orgId}/repositories/{repoId}/pull-requests",
                        user.personalOrganizationId(), repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].number").value(42))
                .andExpect(jsonPath("$.data[0].title").value("Improve review pipeline"))
                .andExpect(jsonPath("$.data[0].headRef").value("feature/review"))
                .andExpect(jsonPath("$.data[0].baseRef").value("main"));
    }

    @Test
    void nonMemberCannotListPullRequests() throws Exception {
        ProvisioningOutcome a = provision("gh-" + UUID.randomUUID(), "token-a");
        ProvisioningOutcome b = provision("gh-" + UUID.randomUUID(), "token-b");
        UUID repositoryId = importRepository(a, a.personalOrganizationId(), "ext-" + UUID.randomUUID());

        mockMvc.perform(get("/api/organizations/{orgId}/repositories/{repoId}/pull-requests",
                        a.personalOrganizationId(), repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(b.tokens().accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void responseDoesNotExposeCredentialFields() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_super_secret_access");
        UUID repositoryId = importRepository(user, user.personalOrganizationId(), "ext-" + UUID.randomUUID());
        when(gitHubPullRequestClient.listPullRequests(anyString(), eq("owner"), eq("repo")))
                .thenReturn(List.of(new GitHubPullRequestSummary(
                        1, "Safe DTO", "open", false, "octocat", "h", "hs", "main", "bs", "url", null, null)));

        MvcResult result = mockMvc.perform(get("/api/organizations/{orgId}/repositories/{repoId}/pull-requests",
                        user.personalOrganizationId(), repositoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("gho_super_secret_access")
                .doesNotContain("encrypted")
                .doesNotContain("accessToken")
                .doesNotContain("tokenHash");
    }

    private UUID importRepository(ProvisioningOutcome user, UUID organizationId, String externalRepoId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/organizations/{orgId}/repositories", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ImportRepositoryRequest(
                                RepositoryProvider.GITHUB,
                                externalRepoId,
                                "owner",
                                "repo",
                                "owner/repo",
                                RepositoryVisibility.PRIVATE,
                                "main"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText());
    }
}
