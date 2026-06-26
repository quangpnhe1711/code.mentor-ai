package com.lvn.codementor.ai.github.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.GitHubUserProfile;
import com.lvn.codementor.ai.github.application.port.GitHubUserClient;
import com.lvn.codementor.ai.github.config.GitHubProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Fetches {@code GET {apiBaseUrl}/user} using the access token as a bearer credential. A 401 maps to
 * {@link ErrorCode#TOKEN_EXPIRED}; other upstream failures map to {@link ErrorCode#GITHUB_INTEGRATION_ERROR}.
 * The token is never logged or placed in an exception message.
 */
@Component
public class GitHubHttpUserClient implements GitHubUserClient {

    private final RestClient restClient;

    public GitHubHttpUserClient(GitHubProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();
    }

    @Override
    public GitHubUserProfile getCurrentUser(String accessToken) {
        UserResponse user;
        try {
            user = restClient
                    .get()
                    .uri("/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(UserResponse.class);
        } catch (RestClientResponseException e) {
            throw mapStatus(e.getStatusCode(), "GitHub profile fetch failed");
        } catch (RestClientException e) {
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub profile fetch failed");
        }

        if (user == null || user.id() == null) {
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub profile fetch returned no user");
        }
        return new GitHubUserProfile(
                String.valueOf(user.id()), user.login(), user.email(), user.name(), user.avatarUrl());
    }

    private static AppException mapStatus(HttpStatusCode status, String message) {
        if (status.value() == 401) {
            return new AppException(ErrorCode.TOKEN_EXPIRED, "GitHub token is invalid or expired");
        }
        return new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, message);
    }

    private record UserResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("login") String login,
            @JsonProperty("email") String email,
            @JsonProperty("name") String name,
            @JsonProperty("avatar_url") String avatarUrl) {
    }
}
