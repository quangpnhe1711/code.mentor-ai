package com.lvn.codementor.ai.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.GitHubTokenResult;
import com.lvn.codementor.ai.github.application.GitHubUserProfile;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/** Real GitHub OAuth login + callback (mocked GitHub clients). */
class GitHubOAuthApiIT extends AbstractWebIT {

    private static final String LOGIN = "/api/auth/github/login";
    private static final String CALLBACK = "/api/auth/github/callback";

    @Test
    void loginReturnsAuthorizationUrlWithRequiredParamsAndNoSecret() throws Exception {
        MvcResult result = mockMvc.perform(get(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizationUrl").isNotEmpty())
                .andReturn();

        String url = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("authorizationUrl").asText();

        assertThat(url).contains("/login/oauth/authorize");
        assertThat(url).contains("client_id=test-client-id");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("scope=");
        assertThat(url).contains("state=");
        // The client secret must never appear in the authorization URL.
        assertThat(url).doesNotContain("test-client-secret");
        assertThat(url).doesNotContain("client_secret");
    }

    @Test
    void callbackRejectsInvalidState() throws Exception {
        mockMvc.perform(get(CALLBACK).param("code", "any-code").param("state", "never-issued"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GITHUB_OAUTH_STATE_INVALID"));
    }

    @Test
    void callbackRejectsMissingState() throws Exception {
        mockMvc.perform(get(CALLBACK).param("code", "any-code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GITHUB_OAUTH_STATE_INVALID"));
    }

    @Test
    void callbackExchangesCodeProvisionsUserAndReturnsPlatformTokens() throws Exception {
        String ghUserId = "gh-" + UUID.randomUUID();
        when(gitHubOAuthClient.exchangeCodeForToken(anyString(), anyString()))
                .thenReturn(new GitHubTokenResult("gho_secret_access", "ghr_secret_refresh", "repo", "bearer", null));
        when(gitHubUserClient.getCurrentUser(anyString()))
                .thenReturn(new GitHubUserProfile(ghUserId, "octo", "octo@example.com", "Octo Cat", null));

        String state = issuedState();

        mockMvc.perform(get(CALLBACK).param("code", "valid-code").param("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.personalOrganizationId").isNotEmpty())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void callbackDoesNotExposeGithubToken() throws Exception {
        when(gitHubOAuthClient.exchangeCodeForToken(anyString(), anyString()))
                .thenReturn(new GitHubTokenResult("gho_SUPER_SECRET", "ghr_SUPER_SECRET", "repo", "bearer", null));
        when(gitHubUserClient.getCurrentUser(anyString()))
                .thenReturn(new GitHubUserProfile("gh-" + UUID.randomUUID(), "octo", null, "Octo", null));

        MvcResult result = mockMvc.perform(get(CALLBACK).param("code", "valid").param("state", issuedState()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("gho_SUPER_SECRET");
        assertThat(body).doesNotContain("ghr_SUPER_SECRET");
    }

    @Test
    void callbackMapsTokenExchangeFailureToStableError() throws Exception {
        when(gitHubOAuthClient.exchangeCodeForToken(anyString(), anyString()))
                .thenThrow(new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub token exchange failed"));

        mockMvc.perform(get(CALLBACK).param("code", "valid").param("state", issuedState()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("GITHUB_INTEGRATION_ERROR"));
    }

    @Test
    void callbackMapsProfileFailureToStableError() throws Exception {
        when(gitHubOAuthClient.exchangeCodeForToken(anyString(), anyString()))
                .thenReturn(new GitHubTokenResult("gho_x", null, "repo", "bearer", null));
        when(gitHubUserClient.getCurrentUser(anyString()))
                .thenThrow(new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub profile fetch failed"));

        mockMvc.perform(get(CALLBACK).param("code", "valid").param("state", issuedState()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("GITHUB_INTEGRATION_ERROR"));
    }

    /** Drive a real login to obtain a server-issued, valid state for callback tests. */
    private String issuedState() throws Exception {
        MvcResult login = mockMvc.perform(get(LOGIN)).andExpect(status().isOk()).andReturn();
        String url = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("authorizationUrl").asText();
        return UriComponentsBuilder.fromUri(URI.create(url)).build()
                .getQueryParams().getFirst("state");
    }
}
