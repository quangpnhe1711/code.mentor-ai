package com.lvn.codementor.ai.repository.persistence;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.port.GitHubRepositoryClient;
import com.lvn.codementor.ai.repository.application.GitProviderCredentialStore;
import com.lvn.codementor.ai.repository.application.port.GitRepositoryAccessVerifier;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real verifier (BR-REP-001): confirms the user's GitHub account can access the repository before
 * import. This is the <strong>default</strong> verifier.
 *
 * <p>Flow: load the user's {@link GitProviderConnection} (else {@link ErrorCode#PROVIDER_CONNECTION_REQUIRED});
 * decrypt the access token only at the GitHub boundary (via {@link GitProviderCredentialStore}); ask
 * GitHub. The client raises {@link ErrorCode#REPOSITORY_ACCESS_DENIED} for inaccessible/not-found and
 * {@link ErrorCode#TOKEN_EXPIRED} for an invalid/expired token. The token is never logged.
 */
@ConditionalOnProperty(name = "codementor.github.access-verifier", havingValue = "github", matchIfMissing = true)
@Component
public class GitHubRepositoryAccessVerifier implements GitRepositoryAccessVerifier {

    private final GitProviderConnectionJpaRepository connections;
    private final GitProviderCredentialStore credentialStore;
    private final GitHubRepositoryClient repositoryClient;

    public GitHubRepositoryAccessVerifier(
            GitProviderConnectionJpaRepository connections,
            GitProviderCredentialStore credentialStore,
            GitHubRepositoryClient repositoryClient) {
        this.connections = connections;
        this.credentialStore = credentialStore;
        this.repositoryClient = repositoryClient;
    }

    @Override
    public void verifyAccess(UUID userId, RepositoryProvider provider, String externalRepoId) {
        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new AppException(
                        ErrorCode.PROVIDER_CONNECTION_REQUIRED, "A " + provider + " connection is required"));

        String accessToken = credentialStore.decryptAccessToken(connection.getId());
        repositoryClient.verifyRepositoryAccess(accessToken, externalRepoId);
    }
}
