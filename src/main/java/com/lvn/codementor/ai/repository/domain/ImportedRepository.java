package com.lvn.codementor.ai.repository.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A repository imported from a provider, owned by exactly one organization (doc 14 §3.4). Unique per
 * {@code (organizationId, provider, externalRepoId)} (BR-REP-002). Related aggregates are referenced
 * by id only.
 */
@Entity
@Table(name = "repositories")
public class ImportedRepository extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "git_provider_connection_id", nullable = false)
    private UUID gitProviderConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private RepositoryProvider provider;

    @Column(name = "external_repo_id", nullable = false)
    private String externalRepoId;

    @Column(name = "owner_login", nullable = false)
    private String ownerLogin;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private RepositoryVisibility visibility;

    @Column(name = "default_branch")
    private String defaultBranch;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RepositoryStatus status;

    @Column(name = "imported_by_user_id", nullable = false)
    private UUID importedByUserId;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    /** Latest failure reason when {@code status} is FAILED or DISCONNECTED (NFR-005); not an audit log. */
    @Column(name = "error_reason")
    private String errorReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ImportedRepository() {
        // for JPA
    }

    public ImportedRepository(
            UUID organizationId,
            UUID gitProviderConnectionId,
            RepositoryProvider provider,
            String externalRepoId,
            String ownerLogin,
            String name,
            String fullName,
            RepositoryVisibility visibility,
            UUID importedByUserId) {
        this.organizationId = organizationId;
        this.gitProviderConnectionId = gitProviderConnectionId;
        this.provider = provider;
        this.externalRepoId = externalRepoId;
        this.ownerLogin = ownerLogin;
        this.name = name;
        this.fullName = fullName;
        this.visibility = visibility;
        this.importedByUserId = importedByUserId;
        this.status = RepositoryStatus.ACTIVE;
    }

    /** Set the provider's default branch (known at import or after first sync). */
    public void assignDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    /** Move to a failure/disconnected state with a reason (BR-REP-004/005, NFR-005). */
    public void markFailed(RepositoryStatus status, String errorReason) {
        if (status != RepositoryStatus.FAILED && status != RepositoryStatus.DISCONNECTED) {
            throw new IllegalArgumentException("errorReason only applies to FAILED or DISCONNECTED");
        }
        this.status = status;
        this.errorReason = errorReason;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getGitProviderConnectionId() {
        return gitProviderConnectionId;
    }

    public RepositoryProvider getProvider() {
        return provider;
    }

    public String getExternalRepoId() {
        return externalRepoId;
    }

    public String getOwnerLogin() {
        return ownerLogin;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    public RepositoryVisibility getVisibility() {
        return visibility;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public RepositoryStatus getStatus() {
        return status;
    }

    public UUID getImportedByUserId() {
        return importedByUserId;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
