package com.lvn.codementor.ai.repository.application;

import com.lvn.codementor.ai.repository.config.RepositorySnapshotProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Scans a cloned workspace into a credential-free file inventory.
 *
 * <p>Skipped directories (e.g. {@code .git}, {@code node_modules}) are not descended into and are
 * deleted; sensitive or oversized files are recorded as {@code included=false} with a reason and
 * deleted. Only included files remain in the workspace afterward. No file <em>content</em> is read —
 * size and extension-based language only. If the included file count or total bytes exceed the
 * configured limits, a failure reason is returned and the snapshot is failed by the caller.
 */
@Service
public class RepositoryFileInventoryService {

    private static final Map<String, String> LANGUAGE_BY_EXTENSION = Map.ofEntries(
            Map.entry("java", "java"), Map.entry("kt", "kotlin"), Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"), Map.entry("tsx", "typescript"), Map.entry("jsx", "javascript"),
            Map.entry("py", "python"), Map.entry("rb", "ruby"), Map.entry("go", "go"),
            Map.entry("rs", "rust"), Map.entry("php", "php"), Map.entry("cs", "csharp"),
            Map.entry("cpp", "cpp"), Map.entry("c", "c"), Map.entry("h", "c"),
            Map.entry("sql", "sql"), Map.entry("sh", "shell"), Map.entry("yml", "yaml"),
            Map.entry("yaml", "yaml"), Map.entry("json", "json"), Map.entry("xml", "xml"),
            Map.entry("md", "markdown"), Map.entry("html", "html"), Map.entry("css", "css"));

    private final SensitiveFilePolicy policy;
    private final RepositorySnapshotProperties properties;

    public RepositoryFileInventoryService(SensitiveFilePolicy policy, RepositorySnapshotProperties properties) {
        this.policy = policy;
        this.properties = properties;
    }

    public InventoryResult scanAndPrune(Path workspaceRoot) {
        List<ScannedFile> files = new ArrayList<>();
        List<Path> toDelete = new ArrayList<>();
        long[] includedBytes = {0L};
        int[] includedCount = {0};

        try {
            Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(workspaceRoot) && policy.isSkippedDirectory(dir.getFileName().toString())) {
                        toDelete.add(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String relativePath = toPosix(workspaceRoot.relativize(file));
                    long size = attrs.size();
                    String skipReason = policy.skipReason(relativePath, size, properties.maxFileSizeBytes());
                    boolean included = skipReason == null;
                    files.add(new ScannedFile(relativePath, language(relativePath), size, included, skipReason));
                    if (included) {
                        includedCount[0]++;
                        includedBytes[0] += size;
                    } else {
                        toDelete.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan snapshot workspace", e);
        }

        // Prune skipped files and skipped directories so only safe, included files remain on disk.
        for (Path path : toDelete) {
            deleteRecursively(path);
        }

        String failureReason = null;
        if (includedCount[0] > properties.maxFileCount()) {
            failureReason = "File count exceeds the configured limit";
        } else if (includedBytes[0] > properties.maxTotalBytes()) {
            failureReason = "Total size exceeds the configured limit";
        }

        return new InventoryResult(files, includedCount[0], includedBytes[0], failureReason);
    }

    private static String toPosix(Path relative) {
        return relative.toString().replace(java.io.File.separatorChar, '/');
    }

    private static String language(String relativePath) {
        int dot = relativePath.lastIndexOf('.');
        if (dot < 0 || dot == relativePath.length() - 1) {
            return null;
        }
        return LANGUAGE_BY_EXTENSION.get(relativePath.substring(dot + 1).toLowerCase());
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best effort
                        }
                    });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // best effort
        }
    }

    /** One scanned file's inventory metadata. */
    public record ScannedFile(String path, String language, long sizeBytes, boolean included, String skipReason) {
    }

    /** Outcome of a scan. {@code failureReason} is non-null when a configured limit was exceeded. */
    public record InventoryResult(
            List<ScannedFile> files, int includedCount, long includedBytes, String failureReason) {
    }
}
