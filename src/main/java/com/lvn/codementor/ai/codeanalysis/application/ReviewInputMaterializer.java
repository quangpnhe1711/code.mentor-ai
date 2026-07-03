package com.lvn.codementor.ai.codeanalysis.application;

import com.lvn.codementor.ai.codeanalysis.application.result.MaterializedReviewInput;
import com.lvn.codementor.ai.codeanalysis.config.CodeAnalysisProperties;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.repository.application.SensitiveFilePolicy;
import com.lvn.codementor.ai.repository.domain.RepositoryFileEntry;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
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
import org.springframework.stereotype.Component;

/**
 * Rebuilds the sanitized, secret-masked files of a review input from a snapshot's pruned workspace,
 * <strong>in memory only</strong>. This is the single source of truth for the sanitize pipeline: the
 * builder (initial input creation) and the review-execution engine (re-materialize for analysis) both
 * use it, so neither stores source content in the database. <strong>No AI provider is called.</strong>
 *
 * <p>Pipeline per file: skip binary/oversized/sensitive/disallowed-extension defensively → mask
 * secrets (skipping files that are essentially all secret) → keep only the masked text and counts.
 * Aggregate ceilings (file count, total bytes) stop adding files and count the remainder as skipped.
 */
@Component
public class ReviewInputMaterializer {

    /** Extensions eligible for text review (normalized: no leading dot, lower-case). */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "kt", "ts", "tsx", "js", "jsx", "py", "cs", "go",
            "md", "yml", "yaml", "json", "xml", "properties", "gradle", "pom");

    private final RepositorySnapshotJpaRepository snapshots;
    private final RepositoryFileEntryJpaRepository fileEntries;
    private final SensitiveFilePolicy sensitiveFilePolicy;
    private final TextFileReader textFileReader;
    private final BinaryFileDetector binaryFileDetector;
    private final SecretMasker secretMasker;
    private final CodeAnalysisProperties properties;

    public ReviewInputMaterializer(
            RepositorySnapshotJpaRepository snapshots,
            RepositoryFileEntryJpaRepository fileEntries,
            SensitiveFilePolicy sensitiveFilePolicy,
            TextFileReader textFileReader,
            BinaryFileDetector binaryFileDetector,
            SecretMasker secretMasker,
            CodeAnalysisProperties properties) {
        this.snapshots = snapshots;
        this.fileEntries = fileEntries;
        this.sensitiveFilePolicy = sensitiveFilePolicy;
        this.textFileReader = textFileReader;
        this.binaryFileDetector = binaryFileDetector;
        this.secretMasker = secretMasker;
        this.properties = properties;
    }

    /**
     * Re-materialize a snapshot's sanitized input by loading the snapshot and its workspace. Used by
     * review execution. Throws a plain runtime exception (safe message, no path) if the workspace is
     * unavailable — the caller marks the job FAILED with a safe reason.
     */
    public MaterializedReviewInput materialize(UUID snapshotId) {
        RepositorySnapshot snapshot = snapshots
                .findById(snapshotId)
                .orElseThrow(() -> new IllegalStateException("Snapshot is unavailable"));
        if (snapshot.getWorkspacePath() == null) {
            throw new IllegalStateException("Snapshot workspace is unavailable");
        }
        Path workspaceRoot = Path.of(snapshot.getWorkspacePath());
        if (!Files.isDirectory(workspaceRoot)) {
            throw new IllegalStateException("Snapshot workspace is no longer available");
        }
        return materialize(workspaceRoot, snapshotId);
    }

    /** Sanitize a snapshot's included files from an already-resolved workspace root. */
    public MaterializedReviewInput materialize(Path workspaceRoot, UUID snapshotId) {
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

        return new MaterializedReviewInput(included, skipped, maskedSecrets, totalBytes);
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
}
