package com.lvn.codementor.ai.repository.persistence;

import com.lvn.codementor.ai.repository.application.port.GitRepositoryAccessVerifier;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev implementation that permits all access.
 *
 * <p>Guarded by {@code @Profile("!prod")} so it is wired in dev/test/default runs but NEVER in a
 * production-like profile. A production deployment must provide a real GitHub-API-backed verifier.
 *
 * <p>TODO: replace with a real GitHub-API-backed verifier that confirms the user can access the
 * repository (BR-REP-001) and raises {@code REPOSITORY_ACCESS_DENIED} otherwise. No network call is
 * made in this phase.
 */
@Profile("!prod")
@Component
public class PermissiveGitRepositoryAccessVerifier implements GitRepositoryAccessVerifier {

    @Override
    public void verifyAccess(UUID userId, RepositoryProvider provider, String externalRepoId) {
        // Intentionally permissive for Foundation. Real provider verification is a later phase.
    }
}
