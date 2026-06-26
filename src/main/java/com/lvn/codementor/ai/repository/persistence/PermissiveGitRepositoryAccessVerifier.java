package com.lvn.codementor.ai.repository.persistence;

import com.lvn.codementor.ai.repository.application.port.GitRepositoryAccessVerifier;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Permissive (no-op) verifier for local dev only. <strong>Not the default.</strong>
 *
 * <p>Active only when {@code codementor.github.access-verifier=permissive}; otherwise the real
 * {@link GitHubRepositoryAccessVerifier} is wired. Provided so a developer can exercise the import
 * flow without GitHub credentials. It must never be selected in production.
 */
@ConditionalOnProperty(name = "codementor.github.access-verifier", havingValue = "permissive")
@Component
public class PermissiveGitRepositoryAccessVerifier implements GitRepositoryAccessVerifier {

    @Override
    public void verifyAccess(UUID userId, RepositoryProvider provider, String externalRepoId) {
        // Intentionally permissive for Foundation. Real provider verification is a later phase.
    }
}
