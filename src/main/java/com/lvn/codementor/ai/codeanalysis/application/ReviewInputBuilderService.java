package com.lvn.codementor.ai.codeanalysis.application;

import com.lvn.codementor.ai.codeanalysis.application.command.BuildReviewInputCommand;
import com.lvn.codementor.ai.codeanalysis.application.result.BuildReviewInputResult;
import com.lvn.codementor.ai.codeanalysis.application.result.MaterializedReviewInput;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.persistence.CodeAnalysisInputJpaRepository;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshotStatus;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositorySnapshotJpaRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds a sanitized, credential-free review input from a READY snapshot's included file entries.
 *
 * <p>The sanitize pipeline itself lives in {@link ReviewInputMaterializer} (shared with review
 * execution); this service owns the pre-flight checks, the input row lifecycle, and the stable hash.
 * <strong>No AI provider is called.</strong>
 *
 * <p>Pre-flight failures (auth, membership, not found, snapshot-not-ready) are raised as existing error
 * codes before any row exists. Once building starts, any failure marks the input {@code FAILED} with a
 * <em>safe</em> reason — never a path, file content, secret, or stack trace. Sanitized text lives only
 * in memory for the duration of the build; only counts and the hash are stored/returned.
 */
@Service
public class ReviewInputBuilderService {

    private static final Logger log = LoggerFactory.getLogger(ReviewInputBuilderService.class);

    private final OrganizationAccessService organizationAccess;
    private final ImportedRepositoryJpaRepository repositories;
    private final RepositorySnapshotJpaRepository snapshots;
    private final CodeAnalysisInputJpaRepository analysisInputs;
    private final ReviewInputMaterializer materializer;
    private final ReviewInputHashService hashService;

    public ReviewInputBuilderService(
            OrganizationAccessService organizationAccess,
            ImportedRepositoryJpaRepository repositories,
            RepositorySnapshotJpaRepository snapshots,
            CodeAnalysisInputJpaRepository analysisInputs,
            ReviewInputMaterializer materializer,
            ReviewInputHashService hashService) {
        this.organizationAccess = organizationAccess;
        this.repositories = repositories;
        this.snapshots = snapshots;
        this.analysisInputs = analysisInputs;
        this.materializer = materializer;
        this.hashService = hashService;
    }

    public BuildReviewInputResult build(BuildReviewInputCommand command) {
        organizationAccess.requireMember(command.userId(), command.organizationId());

        repositories
                .findByIdAndOrganizationId(command.repositoryId(), command.organizationId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));

        RepositorySnapshot snapshot = snapshots
                .findById(command.snapshotId())
                .filter(s -> s.getOrganizationId().equals(command.organizationId())
                        && s.getRepositoryId().equals(command.repositoryId()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Snapshot not found"));

        if (snapshot.getStatus() != RepositorySnapshotStatus.READY) {
            throw new AppException(ErrorCode.SNAPSHOT_NOT_READY, "Snapshot is not ready for analysis");
        }

        CodeAnalysisInput input = analysisInputs.save(new CodeAnalysisInput(
                snapshot.getId(), command.repositoryId(), command.organizationId(), command.userId()));

        try {
            if (snapshot.getWorkspacePath() == null) {
                return fail(input, "Snapshot workspace is unavailable");
            }
            Path workspaceRoot = Path.of(snapshot.getWorkspacePath());
            if (!Files.isDirectory(workspaceRoot)) {
                return fail(input, "Snapshot workspace is no longer available");
            }

            MaterializedReviewInput sanitized = materializer.materialize(workspaceRoot, command.snapshotId());
            String hash = hashService.hash(sanitized.files());
            input.markReady(
                    hash,
                    sanitized.files().size(),
                    sanitized.skippedCount(),
                    sanitized.maskedSecretCount(),
                    sanitized.totalInputBytes());
            return new BuildReviewInputResult(analysisInputs.save(input));
        } catch (RuntimeException e) {
            // Log internally only; never surface a path, file content, secret, or stack trace to the client.
            log.warn("Analysis input {} failed during build", input.getId(), e);
            return fail(input, "Analysis input preparation failed");
        }
    }

    /** Fetch an analysis input scoped to the caller's organization and repository. */
    public CodeAnalysisInput get(UUID userId, UUID organizationId, UUID repositoryId, UUID analysisInputId) {
        organizationAccess.requireMember(userId, organizationId);
        return analysisInputs
                .findByIdAndOrganizationIdAndRepositoryId(analysisInputId, organizationId, repositoryId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis input not found"));
    }

    private BuildReviewInputResult fail(CodeAnalysisInput input, String safeReason) {
        input.markFailed(safeReason);
        return new BuildReviewInputResult(analysisInputs.save(input));
    }
}
