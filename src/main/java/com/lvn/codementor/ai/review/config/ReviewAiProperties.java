package com.lvn.codementor.ai.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codementor.review.ai")
public record ReviewAiProperties(
        String provider,
        String model,
        String promptVersion,
        String baseUrl,
        String apiKey,
        int maxFiles,
        int maxCharsPerFile) {

    public ReviewAiProperties {
        provider = blankToDefault(provider, "LOCAL").trim().toUpperCase();
        model = blankToDefault(model, "deterministic-review-analyzer");
        promptVersion = blankToDefault(promptVersion, "review-local-v1");
        baseUrl = blankToDefault(baseUrl, "https://api.openai.com");
        apiKey = blankToDefault(apiKey, "");
        if (maxFiles <= 0) {
            maxFiles = 30;
        }
        if (maxCharsPerFile <= 0) {
            maxCharsPerFile = 8000;
        }
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
