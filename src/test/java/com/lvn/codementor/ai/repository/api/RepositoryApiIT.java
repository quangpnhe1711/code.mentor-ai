package com.lvn.codementor.ai.repository.api;
import com.lvn.codementor.ai.repository.api.request.ImportRepositoryRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.identity.domain.User;
import com.lvn.codementor.ai.identity.persistence.UserJpaRepository;
import com.lvn.codementor.ai.organization.domain.Organization;
import com.lvn.codementor.ai.organization.domain.OrganizationMember;
import com.lvn.codementor.ai.organization.domain.OrganizationRole;
import com.lvn.codementor.ai.organization.persistence.OrganizationJpaRepository;
import com.lvn.codementor.ai.organization.persistence.OrganizationMemberJpaRepository;
import com.lvn.codementor.ai.auth.application.JwtService;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Repository import/list/detail API behaviour (FR-003/FR-005 area, BR-REP-001/002/003). */
class RepositoryApiIT extends AbstractWebIT {

    @Autowired
    UserJpaRepository users;

    @Autowired
    OrganizationJpaRepository organizations;

    @Autowired
    OrganizationMemberJpaRepository members;

    @Autowired
    JwtService jwtService;

    @Test
    void memberCanImportIntoOwnPersonalOrg() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");

        postImport(user, user.personalOrganizationId(), "ext-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.errorReason").doesNotExist());
    }

    @Test
    void importReturnsAccessDeniedWhenGitHubRejects() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        String deniedRepoId = "ext-" + UUID.randomUUID();
        // The real GitHubRepositoryAccessVerifier delegates to the (mocked) GitHub client; simulate
        // GitHub reporting the repository as inaccessible/not found.
        doThrow(new AppException(ErrorCode.REPOSITORY_ACCESS_DENIED, "Repository is not accessible"))
                .when(gitHubRepositoryClient)
                .verifyRepositoryAccess(anyString(), eq(deniedRepoId));

        postImport(user, user.personalOrganizationId(), deniedRepoId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REPOSITORY_ACCESS_DENIED"));
    }

    @Test
    void duplicateImportInSameOrgReturnsAlreadyImported() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        String externalRepoId = "ext-" + UUID.randomUUID();

        postImport(user, user.personalOrganizationId(), externalRepoId).andExpect(status().isCreated());

        postImport(user, user.personalOrganizationId(), externalRepoId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPOSITORY_ALREADY_IMPORTED"));
    }

    @Test
    void sameUpstreamRepoCanBeImportedIntoDifferentOrganizations() throws Exception {
        ProvisioningOutcome a = provision("gh-" + UUID.randomUUID(), "token-a");
        ProvisioningOutcome b = provision("gh-" + UUID.randomUUID(), "token-b");
        String externalRepoId = "shared-" + UUID.randomUUID();

        postImport(a, a.personalOrganizationId(), externalRepoId).andExpect(status().isCreated());
        postImport(b, b.personalOrganizationId(), externalRepoId).andExpect(status().isCreated());
    }

    @Test
    void nonMemberCannotImportOrList() throws Exception {
        ProvisioningOutcome a = provision("gh-" + UUID.randomUUID(), "token-a");
        ProvisioningOutcome b = provision("gh-" + UUID.randomUUID(), "token-b");

        postImport(b, a.personalOrganizationId(), "ext-" + UUID.randomUUID())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/organizations/{orgId}/repositories", a.personalOrganizationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(b.tokens().accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void importWithoutProviderConnectionReturnsConnectionRequired() throws Exception {
        // Build a member with NO provider connection (provisioning would create one, so do it manually).
        User user = new User("ghC-" + UUID.randomUUID(), "loginC", null, null, null);
        users.save(user);
        UUID userId = user.getId();
        Organization org = Organization.personalFor(userId, "C personal", "c-" + UUID.randomUUID());
        organizations.save(org);
        members.save(new OrganizationMember(org.getId(), userId, OrganizationRole.OWNER));
        String token = jwtService.issue(userId).token();

        mockMvc.perform(post("/api/organizations/{orgId}/repositories", org.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importBody("ext-" + UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_CONNECTION_REQUIRED"));
    }

    @Test
    void listReturnsOnlyTheOrganizationsRepositories() throws Exception {
        ProvisioningOutcome a = provision("gh-" + UUID.randomUUID(), "token-a");
        ProvisioningOutcome b = provision("gh-" + UUID.randomUUID(), "token-b");
        String externalA = "extA-" + UUID.randomUUID();

        postImport(a, a.personalOrganizationId(), externalA).andExpect(status().isCreated());
        postImport(b, b.personalOrganizationId(), "extB-" + UUID.randomUUID()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/organizations/{orgId}/repositories", a.personalOrganizationId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(a.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].externalRepoId").value(externalA));
    }

    @Test
    void detailIsScopedToOrganization_returnsNotFoundForRepoOfAnotherOrg() throws Exception {
        ProvisioningOutcome a = provision("gh-" + UUID.randomUUID(), "token-a");
        ProvisioningOutcome b = provision("gh-" + UUID.randomUUID(), "token-b");

        // A imports a repo into A's personal org.
        MvcResult imported = postImport(a, a.personalOrganizationId(), "ext-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String repoId = objectMapper.readTree(imported.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // B is a member of B's own org, but that repo belongs to A's org. Detail is scoped by
        // (id, organizationId), so it must be 404 — never leaked across organizations.
        mockMvc.perform(get("/api/organizations/{orgId}/repositories/{repoId}",
                        b.personalOrganizationId(), repoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(b.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void responsesDoNotExposeCredentialFields() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "super-secret-github-token");

        MvcResult importResult = postImport(user, user.personalOrganizationId(), "ext-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andReturn();
        String importBody = importResult.getResponse().getContentAsString();
        assertNoCredentialFields(importBody);
        assertThat(importBody).doesNotContain("super-secret-github-token");

        String repositoryId = objectMapper.readTree(importBody).path("data").path("id").asText();
        MvcResult detailResult = mockMvc.perform(
                        get("/api/organizations/{orgId}/repositories/{repoId}",
                                        user.personalOrganizationId(), repositoryId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertNoCredentialFields(detailResult.getResponse().getContentAsString());
    }

    private static void assertNoCredentialFields(String body) {
        assertThat(body)
                .doesNotContain("encrypted")
                .doesNotContain("encryptedAccessToken")
                .doesNotContain("token_hash")
                .doesNotContain("tokenHash");
    }

    private org.springframework.test.web.servlet.ResultActions postImport(
            ProvisioningOutcome user, UUID organizationId, String externalRepoId) throws Exception {
        return mockMvc.perform(post("/api/organizations/{orgId}/repositories", organizationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(importBody(externalRepoId)));
    }

    private String importBody(String externalRepoId) throws Exception {
        return json(new ImportRepositoryRequest(
                RepositoryProvider.GITHUB,
                externalRepoId,
                "owner",
                "repo",
                "owner/repo",
                RepositoryVisibility.PRIVATE,
                "main"));
    }
}
