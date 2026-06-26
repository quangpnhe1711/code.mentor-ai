package com.lvn.codementor.ai.repository.application;

import com.lvn.codementor.ai.github.application.GitCloneResult;
import com.lvn.codementor.ai.github.application.GitCloneSpec;
import com.lvn.codementor.ai.github.application.port.GitRepositoryCloneClient;
import com.lvn.codementor.ai.github.config.GitHubProperties;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/**
 * Clones an imported repository into a prepared workspace.
 *
 * <p>This is the Git provider boundary: the provider access token is decrypted here (via
 * {@link GitProviderCredentialStore}) only to hand it to the clone client, and is never logged,
 * returned, or stored in plaintext. The clone URL is derived from the repository's full name.
 */
@Service
public class RepositoryCloneService {

    private final GitProviderCredentialStore credentialStore;
    private final GitRepositoryCloneClient cloneClient;
    private final GitHubProperties gitHubProperties;

    public RepositoryCloneService(
            GitProviderCredentialStore credentialStore,
            GitRepositoryCloneClient cloneClient,
            GitHubProperties gitHubProperties) {
        this.credentialStore = credentialStore;
        this.cloneClient = cloneClient;
        this.gitHubProperties = gitHubProperties;
    }

    public GitCloneResult clone(
            ImportedRepository repository, GitProviderConnection connection, String ref, Path workspace) {
        // Decrypt the token only at this boundary; it lives only for the duration of the clone call.
        String accessToken = credentialStore.decryptAccessToken(connection.getId());
        String cloneUrl = gitHubProperties.webBaseUrl() + "/" + repository.getFullName() + ".git";
        return cloneClient.cloneRepository(new GitCloneSpec(cloneUrl, accessToken, ref, workspace));
    }
}
