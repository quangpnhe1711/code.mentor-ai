package com.lvn.codementor.ai.codeanalysis.application;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Computes a stable SHA-256 hash of a sanitized review input. The hash is derived from each file's
 * path and its <strong>already secret-masked</strong> content, in a canonical (path-sorted) order,
 * so the same snapshot content yields the same hash and any change to sanitized content or paths
 * yields a different one. Because inputs are masked first, the hash never depends on a plaintext
 * secret.
 */
@Component
public class ReviewInputHashService {

    public String hash(List<CodeAnalysisFile> files) {
        MessageDigest digest = newDigest();
        files.stream()
                .sorted(Comparator.comparing(CodeAnalysisFile::path))
                .forEach(file -> {
                    digest.update(file.path().getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    digest.update(file.sanitizedContent().getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
