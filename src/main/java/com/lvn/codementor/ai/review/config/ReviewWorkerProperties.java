package com.lvn.codementor.ai.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codementor.review.worker")
public record ReviewWorkerProperties(boolean enabled, int batchSize, int maxAttempts, long retryBackoffMs) {

    public ReviewWorkerProperties {
        if (batchSize <= 0) {
            batchSize = 5;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 3;
        }
        if (retryBackoffMs < 0) {
            retryBackoffMs = 0;
        }
    }
}
