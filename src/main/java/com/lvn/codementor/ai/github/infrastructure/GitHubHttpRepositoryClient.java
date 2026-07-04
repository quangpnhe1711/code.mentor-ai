package com.lvn.codementor.ai.github.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.GitHubRepositorySummary;
import com.lvn.codementor.ai.github.application.port.GitHubRepositoryClient;
import com.lvn.codementor.ai.github.config.GitHubProperties;
import java.net.URI;
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
 * Reads repositories from the GitHub REST API for the connected account.
 *
 * <ul>
 *   <li>{@code listRepositories} → {@code GET /user/repos}.</li>
 *   <li>{@code getRepository}/{@code verifyRepositoryAccess} → {@code GET /repositories/{id}}.</li>
 * </ul>
 *
 * Status mapping: 401 → {@link ErrorCode#TOKEN_EXPIRED}; 403/404 → {@link ErrorCode#REPOSITORY_ACCESS_DENIED};
 * anything else → {@link ErrorCode#GITHUB_INTEGRATION_ERROR}. The token is never logged or echoed.
 */
@Component
public class GitHubHttpRepositoryClient implements GitHubRepositoryClient {

    private static final String FIRST_REPOSITORIES_PAGE = "/user/repos?per_page=100&sort=updated";
    private static final String NEXT_REL = "rel=\"next\"";

    private final RestClient restClient;

    @Autowired
    public GitHubHttpRepositoryClient(GitHubProperties properties) {
        this(RestClient.builder()
                .baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build());
    }

    GitHubHttpRepositoryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<GitHubRepositorySummary> listRepositories(String accessToken) {
        List<GitHubRepositorySummary> summaries = new ArrayList<>();
        String nextUri = FIRST_REPOSITORIES_PAGE;
        try {
            while (nextUri != null) {
                ResponseEntity<RepoResponse[]> response = restClient
                        .get()
                        .uri(nextUri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .toEntity(RepoResponse[].class);
                RepoResponse[] repos = response.getBody();
                if (repos != null) {
                    for (RepoResponse repo : repos) {
                        summaries.add(toSummary(repo));
                    }
                }
                nextUri = nextPageUri(response.getHeaders().getFirst(HttpHeaders.LINK));
            }
        } catch (RestClientResponseException e) {
            throw mapStatus(e.getStatusCode());
        } catch (RestClientException e) {
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub repository listing failed");
        }
        return List.copyOf(summaries);
    }

    @Override
    public GitHubRepositorySummary getRepository(String accessToken, String externalRepoId) {
        try {
            RepoResponse repo = restClient
                    .get()
                    .uri("/repositories/{id}", externalRepoId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(RepoResponse.class);
            if (repo == null || repo.id() == null) {
                throw new AppException(ErrorCode.REPOSITORY_ACCESS_DENIED, "Repository is not accessible");
            }
            return toSummary(repo);
        } catch (RestClientResponseException e) {
            throw mapStatus(e.getStatusCode());
        } catch (RestClientException e) {
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub repository fetch failed");
        }
    }

    @Override
    public void verifyRepositoryAccess(String accessToken, String externalRepoId) {
        getRepository(accessToken, externalRepoId);
    }

    private static AppException mapStatus(HttpStatusCode status) {
        int code = status.value();
        if (code == 401) {
            return new AppException(ErrorCode.TOKEN_EXPIRED, "GitHub token is invalid or expired");
        }
        if (code == 403 || code == 404) {
            return new AppException(ErrorCode.REPOSITORY_ACCESS_DENIED, "Repository is not accessible");
        }
        return new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "GitHub repository call failed");
    }

    private static GitHubRepositorySummary toSummary(RepoResponse r) {
        boolean isPrivate = r.isPrivate() != null && r.isPrivate();
        String visibility = isPrivate ? "PRIVATE" : "PUBLIC";
        String ownerLogin = r.owner() == null ? null : r.owner().login();
        return new GitHubRepositorySummary(
                String.valueOf(r.id()),
                ownerLogin,
                r.name(),
                r.fullName(),
                visibility,
                r.defaultBranch(),
                r.htmlUrl(),
                isPrivate);
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

    private record RepoResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("name") String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("owner") Owner owner,
            @JsonProperty("private") Boolean isPrivate,
            @JsonProperty("visibility") String visibility,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("html_url") String htmlUrl) {
    }

    private record Owner(@JsonProperty("login") String login) {
    }
}
