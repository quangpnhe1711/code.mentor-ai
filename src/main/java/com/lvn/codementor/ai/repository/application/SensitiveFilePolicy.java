package com.lvn.codementor.ai.repository.application;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decides which cloned files/directories are excluded from a snapshot for safety.
 *
 * <p>Skips well-known secret-bearing files (e.g. {@code .env}, {@code *.pem}, {@code id_rsa},
 * {@code **}{@code /secrets/**}) and build/tooling directories (e.g. {@code .git}, {@code node_modules},
 * {@code target}). Excluded files are recorded in the inventory with a reason and are not retained in
 * the snapshot workspace.
 */
@Component
public class SensitiveFilePolicy {

    public static final String REASON_SENSITIVE = "SENSITIVE_FILE";
    public static final String REASON_OVERSIZE = "OVERSIZE";

    private static final Set<String> SKIP_DIRECTORIES = Set.of(
            ".git", "node_modules", "target", "build", "dist", "out", "bin", "obj", ".idea", ".vscode");

    /** Whether a directory (by simple name) should not be descended into or retained. */
    public boolean isSkippedDirectory(String directoryName) {
        return SKIP_DIRECTORIES.contains(directoryName);
    }

    /**
     * Classify a regular file by its repository-relative POSIX path and size.
     *
     * @return {@code null} skip reason when the file should be included; otherwise a safe reason code
     */
    public String skipReason(String relativePath, long sizeBytes, long maxFileSizeBytes) {
        if (sizeBytes > maxFileSizeBytes) {
            return REASON_OVERSIZE;
        }
        if (isSensitive(relativePath)) {
            return REASON_SENSITIVE;
        }
        return null;
    }

    private boolean isSensitive(String relativePath) {
        String[] segments = relativePath.split("/");
        String fileName = segments[segments.length - 1];

        for (String segment : segments) {
            if (segment.equals("secrets") || segment.equals("credentials")) {
                return true;
            }
        }
        if (fileName.equals(".env") || fileName.startsWith(".env.")) {
            return true;
        }
        if (fileName.endsWith(".pem") || fileName.endsWith(".key")) {
            return true;
        }
        if (fileName.equals("id_rsa") || fileName.equals("id_dsa")) {
            return true;
        }
        // .github/workflows/*secret*
        if (relativePath.startsWith(".github/workflows/") && fileName.toLowerCase().contains("secret")) {
            return true;
        }
        return false;
    }
}
