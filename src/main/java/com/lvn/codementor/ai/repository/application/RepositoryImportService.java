package com.lvn.codementor.ai.repository.application;
import com.lvn.codementor.ai.repository.application.command.RepositoryImportCommand;
import com.lvn.codementor.ai.repository.application.port.GitRepositoryAccessVerifier;

import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository import and queries, scoped to an organization (FR-003 / FR-005 area).
 *
 * <p>Every operation enforces organization membership first (doc 09). Import additionally requires a
 * GitHub provider connection (BR-REP-003), verifies provider access (BR-REP-001), and enforces
 * per-organization uniqueness (BR-REP-002). No real GitHub API call, clone, or sync happens here.
 */
@Service
public class RepositoryImportService {

    private final OrganizationAccessService organizationAccess;
    private final GitProviderConnectionJpaRepository connections;
    private final ImportedRepositoryJpaRepository repositories;
    private final GitRepositoryAccessVerifier accessVerifier;

    public RepositoryImportService(
            OrganizationAccessService organizationAccess,
            GitProviderConnectionJpaRepository connections,
            ImportedRepositoryJpaRepository repositories,
            GitRepositoryAccessVerifier accessVerifier) {
        this.organizationAccess = organizationAccess;
        this.connections = connections;
        this.repositories = repositories;
        this.accessVerifier = accessVerifier;
    }

    @Transactional
    public ImportedRepository importRepository(UUID userId, UUID organizationId, RepositoryImportCommand command) {
        organizationAccess.requireMember(userId, organizationId);

        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(userId, command.provider())
                .orElseThrow(() -> new AppException(
                        ErrorCode.PROVIDER_CONNECTION_REQUIRED,
                        "A " + command.provider() + " connection is required before importing"));

        accessVerifier.verifyAccess(userId, command.provider(), command.externalRepoId());

        repositories
                .findByOrganizationIdAndProviderAndExternalRepoId(
                        organizationId, command.provider(), command.externalRepoId())
                .ifPresent(existing -> {
                    throw new AppException(
                            ErrorCode.REPOSITORY_ALREADY_IMPORTED,
                            "Repository already imported in this organization");
                });

        ImportedRepository repository = new ImportedRepository(
                organizationId,
                connection.getId(),
                command.provider(),
                command.externalRepoId(),
                command.ownerLogin(),
                command.name(),
                command.fullName(),
                command.visibility(),
                userId);
        if (command.defaultBranch() != null) {
            repository.assignDefaultBranch(command.defaultBranch());
        }
        return repositories.save(repository);
    }

    @Transactional(readOnly = true)
    public List<ImportedRepository> listByOrganization(UUID userId, UUID organizationId) {
        organizationAccess.requireMember(userId, organizationId);
        return repositories.findByOrganizationId(organizationId);
    }

    @Transactional(readOnly = true)
    public ImportedRepository getByOrganization(UUID userId, UUID organizationId, UUID repositoryId) {
        organizationAccess.requireMember(userId, organizationId);
        return repositories
                .findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));
    }
}
