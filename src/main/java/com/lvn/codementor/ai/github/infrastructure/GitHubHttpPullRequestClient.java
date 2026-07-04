package com.lvn.codementor.ai.github.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.GitHubPullRequestSummary;
import com.lvn.codementor.ai.github.application.port.GitHubPullRequestClient;
import com.lvn.codementor.ai.github.config.GitHubProperties;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads open pull requests from the GitHub REST API.
 *
 * <p>Status mapping follows repository reads: 401 → {@link ErrorCode#TOKEN_EXPIRED}; 403/404 →
 * {@link ErrorCode#REPOSITORY_ACCESS_DENIED}; anything else → {@link ErrorCode#GITHUB_INTEGRATION_ERROR}.
 * The token is never logged or echoed.
 */
@Component
public class GitHubHttpPullRequestClient implements GitHubPullRequestClient {

    private static final String NEXT_REL = "rel=\"next\"";

    private final RestClient restClient;

    @Autowired
    public GitHubHttpPullRequestClient(GitHubProperties properties) {
        this(RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build());
    }

    GitHubHttpPullRequestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<GitHubPullRequestSummary> listPullRequests(String accessToken, String owner, String repo) {
        List<GitHubPullRequestSummary> summaries = new ArrayList<>();
        String nextUri = "/repos/%s/%s/pulls?state=open&per_page=100&sort=updated&direction=desc"
                .formatted(owner, repo);
        try {
            while (nextUri != null) {
                ResponseEntity<PullRequestResponse[]> response = restClient
                        .get()
                        .uri(nextUri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .toEntity(PullRequestResponse[].class);
                PullRequestResponse[] pullRequests = response.getBody();
                if (pullRequests != null) {
                    for (PullRequestResponse pullRequest : pullRequests) {
                        summaries.add(toSummary(pullRequest));
                    }
                }
                nextUri = nextPageUri(response.getHeaders().getFirst(HttpHeaders.LINK));
            }
        } catch (RestClientResponseException e) {
            throw mapStatus(e.getStatusCode());
        } catch (RestClientException e) {
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub pull request listing failed");
        }
        return List.copyOf(summaries);
    }

    private static AppException mapStatus(HttpStatusCode status) {
        int code = status.value();
        if (code == 401) {
            return new AppException(ErrorCode.TOKEN_EXPIRED, "GitHub token is invalid or expired");
        }
        if (code == 403 || code == 404) {
            return new AppException(ErrorCode.REPOSITORY_ACCESS_DENIED, "Repository is not accessible");
        }
        return new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub pull request call failed");
    }

    private static GitHubPullRequestSummary toSummary(PullRequestResponse r) {
        return new GitHubPullRequestSummary(
                r.number() == null ? 0 : r.number(),
                r.title(),
                r.state(),
                r.draft() != null && r.draft(),
                r.user() == null ? null : r.user().login(),
                r.head() == null ? null : r.head().ref(),
                r.head() == null ? null : r.head().sha(),
                r.base() == null ? null : r.base().ref(),
                r.base() == null ? null : r.base().sha(),
                r.htmlUrl(),
                r.createdAt(),
                r.updatedAt());
    }

    private static String nextPageUri(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return null;
        }
        for (String part : linkHeader.split(",")) {
            if (!part.contains(NEXT_REL)) {
                continue;
            }
            int start = part.indexOf('<');
            int end = part.indexOf('>', start + 1);
            if (start < 0 || end <= start) {
                return null;
            }
            return normalizeUri(part.substring(start + 1, end));
        }
        return null;
    }

    private static String normalizeUri(String rawUri) {
        URI uri = URI.create(rawUri);
        if (!uri.isAbsolute()) {
            return rawUri;
        }
        String query = uri.getRawQuery();
        return query == null ? uri.getRawPath() : uri.getRawPath() + "?" + query;
    }

    private record PullRequestResponse(
            @JsonProperty("number") Long number,
            @JsonProperty("title") String title,
            @JsonProperty("state") String state,
            @JsonProperty("draft") Boolean draft,
            @JsonProperty("user") User user,
            @JsonProperty("head") Ref head,
            @JsonProperty("base") Ref base,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt) {
    }

    private record User(@JsonProperty("login") String login) {
    }

    private record Ref(@JsonProperty("ref") String ref, @JsonProperty("sha") String sha) {
    }
}
