package com.lvn.codementor.ai.github.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.GitHubTokenResult;
import com.lvn.codementor.ai.github.application.port.GitHubOAuthClient;
import com.lvn.codementor.ai.github.config.GitHubProperties;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Exchanges an OAuth code for a token via {@code POST {webBaseUrl}/login/oauth/access_token}.
 *
 * <p>The client secret is sent only in the request body and is never logged; on any upstream failure
 * a {@link ErrorCode#GITHUB_INTEGRATION_ERROR} is raised with no token/secret in the message.
 */
@Component
public class GitHubHttpOAuthClient implements GitHubOAuthClient {

    private final RestClient restClient;
    private final GitHubProperties properties;

    public GitHubHttpOAuthClient(GitHubProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.webBaseUrl()).build();
    }

    @Override
    public GitHubTokenResult exchangeCodeForToken(String code, String redirectUri) {
        TokenResponse response;
        try {
            response = restClient
                    .post()
                    .uri("/login/oauth/access_token")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "client_id", properties.clientId(),
                            "client_secret", properties.clientSecret(),
                            "code", code,
                            "redirect_uri", redirectUri))
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            // Note: e may carry the response body; do not propagate it to clients.
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub token exchange failed");
        }

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub token exchange returned no token");
        }

        Instant expiresAt = response.expiresIn() == null ? null : Instant.now().plusSeconds(response.expiresIn());
        return new GitHubTokenResult(
                response.accessToken(), response.refreshToken(), response.scope(), response.tokenType(), expiresAt);
    }

    /** GitHub token-exchange response (snake_case); {@code error} is set on failures. */
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("scope") String scope,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("error") String error) {
    }
}
