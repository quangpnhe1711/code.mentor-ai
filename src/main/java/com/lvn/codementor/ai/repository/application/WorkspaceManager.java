package com.lvn.codementor.ai.repository.application;

import com.lvn.codementor.ai.repository.config.RepositorySnapshotProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Creates and deletes per-snapshot working directories under the configured workspace root. Each
 * snapshot gets its own empty directory; cleanup deletes the whole tree.
 */
@Component
public class WorkspaceManager {

    private final RepositorySnapshotProperties properties;

    public WorkspaceManager(RepositorySnapshotProperties properties) {
        this.properties = properties;
    }

    /** Create a fresh, empty workspace directory for the given snapshot. */
    public Path createWorkspace(UUID snapshotId) {
        try {
            Path root = Path.of(properties.workspaceRoot());
            Files.createDirectories(root);
            return Files.createDirectories(root.resolve(snapshotId.toString()));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create snapshot workspace", e);
        }
    }

    /** Recursively delete a workspace directory; never throws. */
    public void delete(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}
