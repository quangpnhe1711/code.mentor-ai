package com.lvn.codementor.ai.testgeneration.api.request;

public record CreateTestGenerationJobRequest(
        String targetType,
        String targetFilePath,
        Integer targetPullRequestNumber) {
}
