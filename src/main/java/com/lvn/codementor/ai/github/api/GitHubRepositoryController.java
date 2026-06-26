package com.lvn.codementor.ai.github.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.github.api.response.GitHubRepositoryResponse;
import com.lvn.codementor.ai.github.application.GitHubRepositoryQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists the GitHub repositories visible to the current platform user's connected account.
 *
 * <p>Requires a platform JWT (enforced by {@code SecurityConfig}); the current user is resolved from
 * the JWT, never from the GitHub token. Responses contain only safe DTOs — no credential fields.
 */
@RestController
@RequestMapping("/api/github/repositories")
public class GitHubRepositoryController {

    private final CurrentUser currentUser;
    private final GitHubRepositoryQueryService repositoryQueryService;

    public GitHubRepositoryController(
            CurrentUser currentUser, GitHubRepositoryQueryService repositoryQueryService) {
        this.currentUser = currentUser;
        this.repositoryQueryService = repositoryQueryService;
    }

    @GetMapping
    public ApiResponse<List<GitHubRepositoryResponse>> listRepositories() {
        UUID userId = currentUser.requireUserId();
        List<GitHubRepositoryResponse> data = repositoryQueryService.listForUser(userId).stream()
                .map(GitHubRepositoryResponse::from)
                .toList();
        return ApiResponse.ok(data, UUID.randomUUID().toString());
    }
}
