package com.lvn.codementor.ai.repository.application.port;

import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import java.util.UUID;

/**
 * Verifies that a user actually has access to a repository on the Git provider before it is imported
 * (BR-REP-001). Implementations may call the provider API; failure should raise
 * {@code REPOSITORY_ACCESS_DENIED}.
 */
public interface GitRepositoryAccessVerifier {

    void verifyAccess(UUID userId, RepositoryProvider provider, String externalRepoId);
}
