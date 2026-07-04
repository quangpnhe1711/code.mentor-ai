package com.lvn.codementor.ai.repository.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.github.application.GitHubPullRequestQueryService;
import com.lvn.codementor.ai.repository.api.response.PullRequestResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pull request listing for an imported repository (FR-005).
 */
@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/pull-requests")
public class RepositoryPullRequestController {

    private final CurrentUser currentUser;
    private final GitHubPullRequestQueryService pullRequestQueryService;

    public RepositoryPullRequestController(
            CurrentUser currentUser, GitHubPullRequestQueryService pullRequestQueryService) {
        this.currentUser = currentUser;
        this.pullRequestQueryService = pullRequestQueryService;
    }

    @GetMapping
    public ApiResponse<List<PullRequestResponse>> listPullRequests(
            @PathVariable UUID organizationId, @PathVariable UUID repositoryId) {
        UUID userId = currentUser.requireUserId();
        List<PullRequestResponse> data = pullRequestQueryService
                .listForRepository(userId, organizationId, repositoryId)
                .stream()
                .map(PullRequestResponse::from)
                .toList();
        return ApiResponse.ok(data, UUID.randomUUID().toString());
    }
}
