package com.lvn.codementor.ai.github.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.port.GitHubPullRequestClient;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.application.GitProviderCredentialStore;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists pull requests for an imported GitHub repository, scoped by organization membership.
 *
 * <p>The provider token is decrypted only at the GitHub API boundary and never returned. Pull
 * request data is read live from GitHub for this phase; it is not persisted locally.
 */
@Service
public class GitHubPullRequestQueryService {

    private final OrganizationAccessService organizationAccess;
    private final ImportedRepositoryJpaRepository repositories;
    private final GitProviderConnectionJpaRepository connections;
    private final GitProviderCredentialStore credentialStore;
    private final GitHubPullRequestClient pullRequestClient;

    public GitHubPullRequestQueryService(
            OrganizationAccessService organizationAccess,
            ImportedRepositoryJpaRepository repositories,
            GitProviderConnectionJpaRepository connections,
            GitProviderCredentialStore credentialStore,
            GitHubPullRequestClient pullRequestClient) {
        this.organizationAccess = organizationAccess;
        this.repositories = repositories;
        this.connections = connections;
        this.credentialStore = credentialStore;
        this.pullRequestClient = pullRequestClient;
    }

    @Transactional(readOnly = true)
    public List<GitHubPullRequestSummary> listForRepository(UUID userId, UUID organizationId, UUID repositoryId) {
        organizationAccess.requireMember(userId, organizationId);
        ImportedRepository repository = repositories
                .findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));

        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(userId, repository.getProvider())
                .orElseThrow(() -> new AppException(
                        ErrorCode.PROVIDER_CONNECTION_REQUIRED, "A provider connection is required"));

        String accessToken = credentialStore.decryptAccessToken(connection.getId());
        return pullRequestClient.listPullRequests(accessToken, repository.getOwnerLogin(), repository.getName());
    }
}
