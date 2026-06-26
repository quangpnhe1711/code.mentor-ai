package com.lvn.codementor.ai.repository.api.response;

import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshotStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot view returned by the API. Intentionally omits the local {@code workspacePath} and any
 * credential data.
 */
public record RepositorySnapshotResponse(
        UUID id,
        UUID repositoryId,
        UUID organizationId,
        RepositorySnapshotStatus status,
        String sourceRef,
        String commitSha,
        Integer fileCount,
        Long totalBytes,
        String errorReason,
        Instant createdAt,
        Instant updatedAt) {

    public static RepositorySnapshotResponse from(RepositorySnapshot s) {
        return new RepositorySnapshotResponse(
                s.getId(),
                s.getRepositoryId(),
                s.getOrganizationId(),
                s.getStatus(),
                s.getSourceRef(),
                s.getCommitSha(),
                s.getFileCount(),
                s.getTotalBytes(),
                s.getErrorReason(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
