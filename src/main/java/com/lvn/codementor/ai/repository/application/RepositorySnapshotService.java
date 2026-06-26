package com.lvn.codementor.ai.repository.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.GitCloneResult;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.application.RepositoryFileInventoryService.InventoryResult;
import com.lvn.codementor.ai.repository.application.RepositoryFileInventoryService.ScannedFile;
import com.lvn.codementor.ai.repository.application.command.RepositorySnapshotCommand;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryFileEntry;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositoryFileEntryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositorySnapshotJpaRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates synchronous snapshot preparation: membership/ownership checks, clone, scan, and
 * persistence of credential-free inventory (clone + snapshot foundation; no AI/clone-sync/worker).
 *
 * <p>Pre-flight failures (auth, membership, not found, missing connection) are raised as the existing
 * error codes before any snapshot row exists. Once cloning starts, any failure (clone/token error or
 * an exceeded limit) marks the snapshot {@code FAILED} with a <em>safe</em> reason — never a token,
 * local path, or stack trace — and the workspace is deleted.
 */
@Service
public class RepositorySnapshotService {

    private static final Logger log = LoggerFactory.getLogger(RepositorySnapshotService.class);

    private final OrganizationAccessService organizationAccess;
    private final ImportedRepositoryJpaRepository repositories;
    private final GitProviderConnectionJpaRepository connections;
    private final RepositorySnapshotJpaRepository snapshots;
    private final RepositoryFileEntryJpaRepository fileEntries;
    private final WorkspaceManager workspaceManager;
    private final RepositoryCloneService cloneService;
    private final RepositoryFileInventoryService inventoryService;

    public RepositorySnapshotService(
            OrganizationAccessService organizationAccess,
            ImportedRepositoryJpaRepository repositories,
            GitProviderConnectionJpaRepository connections,
            RepositorySnapshotJpaRepository snapshots,
            RepositoryFileEntryJpaRepository fileEntries,
            WorkspaceManager workspaceManager,
            RepositoryCloneService cloneService,
            RepositoryFileInventoryService inventoryService) {
        this.organizationAccess = organizationAccess;
        this.repositories = repositories;
        this.connections = connections;
        this.snapshots = snapshots;
        this.fileEntries = fileEntries;
        this.workspaceManager = workspaceManager;
        this.cloneService = cloneService;
        this.inventoryService = inventoryService;
    }

    public RepositorySnapshot createSnapshot(
            UUID userId, UUID organizationId, UUID repositoryId, RepositorySnapshotCommand command) {

        organizationAccess.requireMember(userId, organizationId);

        ImportedRepository repository = repositories
                .findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));

        // Cloning uses the acting user's own provider credentials. (The repository always references a
        // valid connection via FK, but it may belong to a different member; the user operating now must
        // have their own connection to obtain a token.)
        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(userId, repository.getProvider())
                .orElseThrow(() -> new AppException(
                        ErrorCode.PROVIDER_CONNECTION_REQUIRED, "A provider connection is required"));

        RepositorySnapshot snapshot = snapshots.save(
                new RepositorySnapshot(repositoryId, organizationId, userId));
        Path workspace = workspaceManager.createWorkspace(snapshot.getId());

        try {
            snapshot.markCloning();
            snapshots.save(snapshot);

            GitCloneResult clone = cloneService.clone(repository, connection, command.sourceRef(), workspace);

            String sourceRef = command.sourceRef() != null ? command.sourceRef() : clone.resolvedRef();
            snapshot.markScanning(sourceRef, clone.commitSha(), workspace.toString());
            snapshots.save(snapshot);

            InventoryResult inventory = inventoryService.scanAndPrune(workspace);
            if (inventory.failureReason() != null) {
                return fail(snapshot, workspace, inventory.failureReason());
            }

            persistEntries(snapshot.getId(), inventory.files());
            snapshot.markReady(inventory.includedCount(), inventory.includedBytes());
            return snapshots.save(snapshot);
        } catch (RuntimeException e) {
            // Log the cause internally only; never surface token/path/stack trace to the client.
            log.warn("Snapshot {} failed during preparation", snapshot.getId(), e);
            return fail(snapshot, workspace, "Repository snapshot preparation failed");
        }
    }

    /** List a snapshot's file inventory, enforcing membership and snapshot ownership. */
    public List<RepositoryFileEntry> listFiles(
            UUID userId, UUID organizationId, UUID repositoryId, UUID snapshotId) {
        organizationAccess.requireMember(userId, organizationId);
        snapshots.findById(snapshotId)
                .filter(s -> s.getOrganizationId().equals(organizationId)
                        && s.getRepositoryId().equals(repositoryId))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Snapshot not found"));
        return fileEntries.findBySnapshotId(snapshotId);
    }

    private RepositorySnapshot fail(RepositorySnapshot snapshot, Path workspace, String safeReason) {
        snapshot.markFailed(safeReason);
        snapshots.save(snapshot);
        workspaceManager.delete(workspace);
        return snapshot;
    }

    private void persistEntries(UUID snapshotId, List<ScannedFile> files) {
        List<RepositoryFileEntry> entries = files.stream()
                .map(f -> new RepositoryFileEntry(
                        snapshotId, f.path(), f.language(), f.sizeBytes(), f.included(), f.skipReason()))
                .toList();
        fileEntries.saveAll(entries);
    }
}
