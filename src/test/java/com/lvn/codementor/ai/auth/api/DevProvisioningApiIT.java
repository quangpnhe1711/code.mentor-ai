package com.lvn.codementor.ai.auth.api;
import com.lvn.codementor.ai.auth.api.request.DevProvisionRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.identity.persistence.UserJpaRepository;
import com.lvn.codementor.ai.auth.application.RefreshTokenHasher;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** Dev provisioning endpoint behaviour (Part B): tokens issued, GitHub token not leaked, refresh hashed. */
class DevProvisioningApiIT extends AbstractWebIT {

    private static final String URL = "/api/auth/github/provision/dev";

    @Autowired
    UserJpaRepository users;

    @Autowired
    RefreshTokenHasher refreshTokenHasher;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void provisionReturnsAccessAndRefreshTokens() throws Exception {
        String body = json(request("gh-" + UUID.randomUUID(), "fake-github-token"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.personalOrganizationId").isNotEmpty())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void responseDoesNotExposeGithubToken() throws Exception {
        String githubToken = "ghp_SUPER_SECRET_" + UUID.randomUUID();
        String body = json(request("gh-" + UUID.randomUUID(), githubToken));

        MvcResult result = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(githubToken);
    }

    @Test
    void refreshTokenIsStoredAsHashNotRaw() throws Exception {
        String githubUserId = "gh-" + UUID.randomUUID();
        String body = json(request(githubUserId, "fake-github-token"));

        MvcResult result = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        String rawRefreshToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        UUID userId = users.findByGithubUserId(githubUserId).orElseThrow().getId();
        String storedHash = jdbc.queryForObject(
                "select token_hash from auth_refresh_tokens where user_id = ?", String.class, userId);

        assertThat(storedHash).isNotEqualTo(rawRefreshToken);
        assertThat(storedHash).isEqualTo(refreshTokenHasher.hash(rawRefreshToken));

        Integer rawMatches = jdbc.queryForObject(
                "select count(*) from auth_refresh_tokens where token_hash = ?", Integer.class, rawRefreshToken);
        assertThat(rawMatches).isZero();
    }

    private DevProvisionRequest request(String githubUserId, String accessToken) {
        return new DevProvisionRequest(
                githubUserId,
                githubUserId + "-login",
                "user@example.com",
                "Display Name",
                null,
                githubUserId,
                githubUserId + "-login",
                accessToken,
                null,
                null,
                "repo read:user");
    }
}
