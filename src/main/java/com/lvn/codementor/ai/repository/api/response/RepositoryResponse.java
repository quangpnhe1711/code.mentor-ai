package com.lvn.codementor.ai.repository.api.response;

import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositoryStatus;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import java.time.Instant;
import java.util.UUID;

/**
 * Repository view returned by the API. Deliberately excludes all credential data (encrypted tokens,
 * key material) — only the connection's id is surfaced, never its secrets.
 */
public record RepositoryResponse(
        UUID id,
        UUID organizationId,
        UUID gitProviderConnectionId,
        RepositoryProvider provider,
        String externalRepoId,
        String ownerLogin,
        String name,
        String fullName,
        RepositoryVisibility visibility,
        String defaultBranch,
        RepositoryStatus status,
        UUID importedByUserId,
        Instant lastSyncedAt,
        String errorReason,
        Instant createdAt,
        Instant updatedAt) {

    public static RepositoryResponse from(ImportedRepository repository) {
        return new RepositoryResponse(
                repository.getId(),
                repository.getOrganizationId(),
                repository.getGitProviderConnectionId(),
                repository.getProvider(),
                repository.getExternalRepoId(),
                repository.getOwnerLogin(),
                repository.getName(),
                repository.getFullName(),
                repository.getVisibility(),
                repository.getDefaultBranch(),
                repository.getStatus(),
                repository.getImportedByUserId(),
                repository.getLastSyncedAt(),
                repository.getErrorReason(),
                repository.getCreatedAt(),
                repository.getUpdatedAt());
    }
}
