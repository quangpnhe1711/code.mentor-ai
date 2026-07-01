package com.lvn.codementor.ai.codeanalysis.application;

import com.lvn.codementor.ai.codeanalysis.application.command.BuildReviewInputCommand;
import com.lvn.codementor.ai.codeanalysis.application.result.BuildReviewInputResult;
import com.lvn.codementor.ai.codeanalysis.config.CodeAnalysisProperties;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.persistence.CodeAnalysisInputJpaRepository;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.application.SensitiveFilePolicy;
import com.lvn.codementor.ai.repository.domain.RepositoryFileEntry;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshotStatus;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositoryFileEntryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositorySnapshotJpaRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds a sanitized, credential-free review input from a READY snapshot's included file entries.
 *
 * <p>Pipeline: read safe text files from the snapshot workspace → skip binary/oversized/sensitive/
 * disallowed-extension files defensively → mask detected secrets (skipping files that are essentially
 * all secret) → assemble a canonical input → compute a stable SHA-256 hash → persist metadata only.
 * <strong>No AI provider is called.</strong>
 *
 * <p>Pre-flight failures (auth, membership, not found, snapshot-not-ready) are raised as existing error
 * codes before any row exists. Once building starts, any failure marks the input {@code FAILED} with a
 * <em>safe</em> reason — never a path, file content, secret, or stack trace. Raw and masked file text
 * live only in memory for the duration of the build; only counts and the hash are stored/returned.
 */
@Service
public class ReviewInputBuilderService {

    private static final Logger log = LoggerFactory.getLogger(ReviewInputBuilderService.class);

    /** Extensions eligible for text review (normalized: no leading dot, lower-case). */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "kt", "ts", "tsx", "js", "jsx", "py", "cs", "go",
            "md", "yml", "yaml", "json", "xml", "properties", "gradle", "pom");

    private final OrganizationAccessService organizationAccess;
    private final ImportedRepositoryJpaRepository repositories;
    private final RepositorySnapshotJpaRepository snapshots;
    private final RepositoryFileEntryJpaRepository fileEntries;
    private final CodeAnalysisInputJpaRepository analysisInputs;
    private final SensitiveFilePolicy sensitiveFilePolicy;
    private final TextFileReader textFileReader;
    private final BinaryFileDetector binaryFileDetector;
    private final SecretMasker secretMasker;
    private final ReviewInputHashService hashService;
    private final CodeAnalysisProperties properties;

    public ReviewInputBuilderService(
            OrganizationAccessService organizationAccess,
            ImportedRepositoryJpaRepository repositories,
            RepositorySnapshotJpaRepository snapshots,
            RepositoryFileEntryJpaRepository fileEntries,
            CodeAnalysisInputJpaRepository analysisInputs,
            SensitiveFilePolicy sensitiveFilePolicy,
            TextFileReader textFileReader,
            BinaryFileDetector binaryFileDetector,
            SecretMasker secretMasker,
            ReviewInputHashService hashService,
            CodeAnalysisProperties properties) {
        this.organizationAccess = organizationAccess;
        this.repositories = repositories;
        this.snapshots = snapshots;
        this.fileEntries = fileEntries;
        this.analysisInputs = analysisInputs;
        this.sensitiveFilePolicy = sensitiveFilePolicy;
        this.textFileReader = textFileReader;
        this.binaryFileDetector = binaryFileDetector;
        this.secretMasker = secretMasker;
        this.hashService = hashService;
        this.properties = properties;
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

            Sanitized sanitized = sanitize(workspaceRoot, command.snapshotId());
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

    private Sanitized sanitize(Path workspaceRoot, UUID snapshotId) {
        List<RepositoryFileEntry> entries = fileEntries.findBySnapshotIdAndIncluded(snapshotId, true).stream()
                .sorted(Comparator.comparing(RepositoryFileEntry::getPath))
                .toList();

        List<CodeAnalysisFile> included = new ArrayList<>();
        int skipped = 0;
        int maskedSecrets = 0;
        long totalBytes = 0;

        for (int i = 0; i < entries.size(); i++) {
            RepositoryFileEntry entry = entries.get(i);

            // Aggregate ceilings: stop adding more files, count the remainder as skipped.
            if (included.size() >= properties.maxFileCount()) {
                skipped += entries.size() - i;
                break;
            }

            String path = entry.getPath();
            // Defensive re-checks (the inventory already applied sensitive/size rules; extension is new here).
            if (sensitiveFilePolicy.skipReason(path, entry.getSizeBytes(), properties.maxFileSizeBytes()) != null
                    || !isAllowedExtension(path)) {
                skipped++;
                continue;
            }

            Path file = workspaceRoot.resolve(path);
            if (!Files.isRegularFile(file)) {
                skipped++;
                continue;
            }

            byte[] bytes = textFileReader.readAllBytes(file);
            if (bytes.length > properties.maxFileSizeBytes() || binaryFileDetector.isBinary(bytes)) {
                skipped++;
                continue;
            }

            SecretMasker.MaskResult mask = secretMasker.mask(textFileReader.decode(bytes));
            if (mask.pureSecretFile()) {
                skipped++;
                continue;
            }

            long fileBytes = mask.sanitized().getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes + fileBytes > properties.maxTotalInputBytes()) {
                skipped += entries.size() - i;
                break;
            }

            included.add(new CodeAnalysisFile(path, mask.sanitized(), mask.maskedCount()));
            totalBytes += fileBytes;
            maskedSecrets += mask.maskedCount();
        }

        return new Sanitized(included, skipped, maskedSecrets, totalBytes);
    }

    private BuildReviewInputResult fail(CodeAnalysisInput input, String safeReason) {
        input.markFailed(safeReason);
        return new BuildReviewInputResult(analysisInputs.save(input));
    }

    private static boolean isAllowedExtension(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = path.substring(lastSlash + 1);
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase());
    }

    private record Sanitized(
            List<CodeAnalysisFile> files, int skippedCount, int maskedSecretCount, long totalInputBytes) {
    }
}
