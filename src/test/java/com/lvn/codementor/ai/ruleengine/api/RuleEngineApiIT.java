package com.lvn.codementor.ai.ruleengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.ruleengine.persistence.RepositoryRuleSetAssignmentJpaRepository;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Rule Engine API foundation: rule sets, custom rules, and repository activation. */
class RuleEngineApiIT extends AbstractWebIT {

    @Autowired
    GitProviderConnectionJpaRepository connections;

    @Autowired
    ImportedRepositoryJpaRepository repositories;

    @Autowired
    RepositoryRuleSetAssignmentJpaRepository assignments;

    @Test
    void requiresPlatformJwt() throws Exception {
        mockMvc.perform(get(ruleSetsUrl(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void memberCanCreateAndListRuleSets() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        UUID ruleSetId = createRuleSet(user, "Backend conventions");

        mockMvc.perform(get(ruleSetsUrl(user.personalOrganizationId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(ruleSetId.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Backend conventions"));
    }

    @Test
    void nonMemberCannotCreateRuleSetInAnotherOrganization() throws Exception {
        ProvisioningOutcome owner = provision("gh-" + UUID.randomUUID(), "owner");
        ProvisioningOutcome other = provision("gh-" + UUID.randomUUID(), "other");

        mockMvc.perform(post(ruleSetsUrl(owner.personalOrganizationId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void memberCanCreateAndListNaturalLanguageRule() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        UUID ruleSetId = createRuleSet(user, "Team rules");

        mockMvc.perform(post(ruleSetsUrl(user.personalOrganizationId()) + "/" + ruleSetId + "/rules")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleKey": "no-hardcoded-ui-copy",
                                  "title": "No hardcoded UI copy",
                                  "instruction": "User-facing text must come from the localization layer.",
                                  "category": "ARCHITECTURE",
                                  "defaultSeverity": "MEDIUM",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ruleKey").value("no-hardcoded-ui-copy"))
                .andExpect(jsonPath("$.data.defaultSeverity").value("MEDIUM"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.custom").value(true));

        mockMvc.perform(get(ruleSetsUrl(user.personalOrganizationId()) + "/" + ruleSetId + "/rules")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].instruction").value(
                        "User-facing text must come from the localization layer."));
    }

    @Test
    void invalidRuleSeverityIsRejected() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        UUID ruleSetId = createRuleSet(user, "Team rules");

        mockMvc.perform(post(ruleSetsUrl(user.personalOrganizationId()) + "/" + ruleSetId + "/rules")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleKey": "x",
                                  "title": "x",
                                  "instruction": "x",
                                  "category": "STYLE",
                                  "defaultSeverity": "LOUD"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void repositoryHasOnlyOneActiveRuleSetAtATime() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        ImportedRepository repo = persistRepository(user);
        UUID first = createRuleSet(user, "First");
        UUID second = createRuleSet(user, "Second");

        activate(user, repo.getId(), first)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ruleSetId").value(first.toString()))
                .andExpect(jsonPath("$.data.active").value(true));

        activate(user, repo.getId(), second)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ruleSetId").value(second.toString()))
                .andExpect(jsonPath("$.data.active").value(true));

        assertThat(assignments.findAll().stream()
                        .filter(a -> a.getRepositoryId().equals(repo.getId()))
                        .filter(a -> a.isActive())
                        .toList())
                .hasSize(1);

        mockMvc.perform(get("/api/organizations/{orgId}/repositories/{repoId}/active-rule-set",
                        user.personalOrganizationId(), repo.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruleSetId").value(second.toString()));
    }

    private org.springframework.test.web.servlet.ResultActions activate(
            ProvisioningOutcome user, UUID repositoryId, UUID ruleSetId) throws Exception {
        return mockMvc.perform(post(
                        "/api/organizations/{orgId}/repositories/{repoId}/rule-sets/{ruleSetId}/activate",
                        user.personalOrganizationId(),
                        repositoryId,
                        ruleSetId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())));
    }

    private UUID createRuleSet(ProvisioningOutcome user, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(ruleSetsUrl(user.personalOrganizationId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new com.lvn.codementor.ai.ruleengine.api.request.CreateRuleSetRequest(
                                name, "Rules for " + name))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText());
    }

    private ImportedRepository persistRepository(ProvisioningOutcome user) {
        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(user.userId(), RepositoryProvider.GITHUB)
                .orElseThrow();
        ImportedRepository repo = new ImportedRepository(
                user.personalOrganizationId(),
                connection.getId(),
                RepositoryProvider.GITHUB,
                "ext-" + UUID.randomUUID(),
                "owner",
                "repo",
                "owner/repo",
                RepositoryVisibility.PRIVATE,
                user.userId());
        return repositories.save(repo);
    }

    private static String ruleSetsUrl(UUID organizationId) {
        return "/api/organizations/" + organizationId + "/rule-sets";
    }
}
