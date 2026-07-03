package com.lvn.codementor.ai.codeanalysis.application.result;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import java.util.List;

/**
 * The sanitized, secret-masked files of a review input, rebuilt from a snapshot workspace and held
 * <strong>in memory only</strong>. Never persisted and never returned by the API — the per-file
 * content exists transiently for hashing (builder) or analysis (review execution).
 *
 * @param files              sanitized files that survived selection + masking (path + masked content)
 * @param skippedCount       files excluded (binary/oversized/sensitive/disallowed/over-limit)
 * @param maskedSecretCount  total secret occurrences replaced across included files
 * @param totalInputBytes    total sanitized bytes across included files
 */
public record MaterializedReviewInput(
        List<CodeAnalysisFile> files, int skippedCount, int maskedSecretCount, long totalInputBytes) {
}
