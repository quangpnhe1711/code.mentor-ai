package com.lvn.codementor.ai.codeanalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits for building a sanitized review input from a snapshot.
 *
 * @param maxFileSizeBytes    per-file ceiling; larger files are skipped from the input
 * @param maxTotalInputBytes  total sanitized-bytes ceiling; once reached, no more files are added
 * @param maxFileCount        included-file-count ceiling; once reached, no more files are added
 */
@ConfigurationProperties(prefix = "codementor.code-analysis")
public record CodeAnalysisProperties(long maxFileSizeBytes, long maxTotalInputBytes, int maxFileCount) {
}
