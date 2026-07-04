package com.lvn.codementor.ai.github.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.port.GitHubPullRequestCommentClient;
import com.lvn.codementor.ai.github.config.GitHubProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Creates GitHub issue comments for pull-request reviews.
 *
 * <p>Status mapping follows other GitHub adapters: 401 → {@link ErrorCode#TOKEN_EXPIRED}; 403/404 →
 * {@link ErrorCode#REPOSITORY_ACCESS_DENIED}; anything else → {@link ErrorCode#GITHUB_INTEGRATION_ERROR}.
 */
@Component
public class GitHubHttpPullRequestCommentClient implements GitHubPullRequestCommentClient {

    private final RestClient restClient;

    public GitHubHttpPullRequestCommentClient(GitHubProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();
    }

    @Override
    public void createIssueComment(String accessToken, String owner, String repo, int issueNumber, String body) {
        try {
            restClient
                    .post()
                    .uri("/repos/{owner}/{repo}/issues/{issueNumber}/comments", owner, repo, issueNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .body(new CreateCommentRequest(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw mapStatus(e.getStatusCode());
        } catch (RestClientException e) {
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub pull request comment failed");
        }
    }

    private static AppException mapStatus(HttpStatusCode status) {
        int code = status.value();
        if (code == 401) {
            return new AppException(ErrorCode.TOKEN_EXPIRED, "GitHub token is invalid or expired");
        }
        if (code == 403 || code == 404) {
            return new AppException(ErrorCode.REPOSITORY_ACCESS_DENIED, "Repository is not accessible");
        }
        return new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub pull request comment failed");
    }

    private record CreateCommentRequest(@JsonProperty("body") String body) {
    }
}
