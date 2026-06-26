package com.lvn.codementor.ai.github.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.port.GitHubRepositoryClient;
import com.lvn.codementor.ai.repository.application.GitProviderCredentialStore;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lists the GitHub repositories visible to a platform user's connected account.
 *
 * <p>Loads the user's {@link GitProviderConnection}, decrypts the access token only at this GitHub
 * API boundary (via {@link GitProviderCredentialStore}), and returns credential-free summaries. If
 * the user has no connection, {@link ErrorCode#PROVIDER_CONNECTION_REQUIRED} is raised.
 */
@Service
public class GitHubRepositoryQueryService {

    private final GitProviderConnectionJpaRepository connections;
    private final GitProviderCredentialStore credentialStore;
    private final GitHubRepositoryClient repositoryClient;

    public GitHubRepositoryQueryService(
            GitProviderConnectionJpaRepository connections,
            GitProviderCredentialStore credentialStore,
            GitHubRepositoryClient repositoryClient) {
        this.connections = connections;
        this.credentialStore = credentialStore;
        this.repositoryClient = repositoryClient;
    }

    public List<GitHubRepositorySummary> listForUser(UUID userId) {
        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(userId, RepositoryProvider.GITHUB)
                .orElseThrow(() -> new AppException(
                        ErrorCode.PROVIDER_CONNECTION_REQUIRED, "A GitHub connection is required"));

        // Decrypt only here, at the GitHub API boundary. The plaintext token never leaves this call.
        String accessToken = credentialStore.decryptAccessToken(connection.getId());
        return repositoryClient.listRepositories(accessToken);
    }
}
