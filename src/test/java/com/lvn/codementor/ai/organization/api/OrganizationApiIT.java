package com.lvn.codementor.ai.organization.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class OrganizationApiIT extends AbstractWebIT {

    @Test
    void memberCanCreateTeamOrganizationAndListMemberships() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_org_owner");

        MvcResult created = mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Platform Team\",\"slug\":\"platform-team-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("TEAM"))
                .andExpect(jsonPath("$.data.name").value("Platform Team"))
                .andReturn();
        String organizationId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        MvcResult listed = mockMvc.perform(get("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(listed.getResponse().getContentAsString()).contains(organizationId);

        mockMvc.perform(get("/api/organizations/{organizationId}/members", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"));
    }

    @Test
    void ownerCanAddExistingUserAsMember() throws Exception {
        ProvisioningOutcome owner = provision("gh-" + UUID.randomUUID(), "gho_org_owner");
        String memberGithubUserId = "gh-" + UUID.randomUUID();
        ProvisioningOutcome member = provision(memberGithubUserId, "gho_org_member");
        String organizationId = createTeam(owner);

        mockMvc.perform(post("/api/organizations/{organizationId}/members", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUserId\":\"" + memberGithubUserId + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.githubUserId").value(memberGithubUserId))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"));

        MvcResult memberOrgs = mockMvc.perform(get("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member.tokens().accessToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(memberOrgs.getResponse().getContentAsString()).contains(organizationId);
    }

    @Test
    void duplicateMemberAndOwnerRoleAreRejected() throws Exception {
        ProvisioningOutcome owner = provision("gh-" + UUID.randomUUID(), "gho_org_owner");
        String memberGithubUserId = "gh-" + UUID.randomUUID();
        provision(memberGithubUserId, "gho_org_member");
        String ownerRoleGithubUserId = "gh-" + UUID.randomUUID();
        provision(ownerRoleGithubUserId, "gho_owner_role_target");
        String organizationId = createTeam(owner);

        mockMvc.perform(post("/api/organizations/{organizationId}/members", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUserId\":\"" + memberGithubUserId + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/organizations/{organizationId}/members", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUserId\":\"" + memberGithubUserId + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/organizations/{organizationId}/members", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUserId\":\"" + ownerRoleGithubUserId + "\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nonAdminMemberCannotAddMembers() throws Exception {
        ProvisioningOutcome owner = provision("gh-" + UUID.randomUUID(), "gho_org_owner");
        String viewerGithubUserId = "gh-" + UUID.randomUUID();
        ProvisioningOutcome viewer = provision(viewerGithubUserId, "gho_org_viewer");
        String targetGithubUserId = "gh-" + UUID.randomUUID();
        provision(targetGithubUserId, "gho_org_target");
        String organizationId = createTeam(owner);

        mockMvc.perform(post("/api/organizations/{organizationId}/members", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUserId\":\"" + viewerGithubUserId + "\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/organizations/{organizationId}/members", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUserId\":\"" + targetGithubUserId + "\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private String createTeam(ProvisioningOutcome user) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Team " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        return data.path("id").asText();
    }
}
