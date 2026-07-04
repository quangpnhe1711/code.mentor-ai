package com.lvn.codementor.ai.testgeneration.api.response;

import com.lvn.codementor.ai.testgeneration.domain.TestGenerationJob;
import com.lvn.codementor.ai.testgeneration.domain.TestGenerationJobStatus;
import com.lvn.codementor.ai.testgeneration.domain.TestGenerationTargetType;
import java.time.Instant;
import java.util.UUID;

public record TestGenerationJobResponse(
        UUID id,
        UUID organizationId,
        UUID repositoryId,
        UUID snapshotId,
        UUID analysisInputId,
        TestGenerationJobStatus status,
        TestGenerationTargetType targetType,
        String targetFilePath,
        Integer targetPullRequestNumber,
        String promptVersion,
        String aiProvider,
        String aiModel,
        String inputHash,
        int generatedTestCount,
        String errorReason,
        Instant createdAt,
        Instant updatedAt) {

    public static TestGenerationJobResponse from(TestGenerationJob job) {
        return new TestGenerationJobResponse(
                job.getId(),
                job.getOrganizationId(),
                job.getRepositoryId(),
                job.getSnapshotId(),
                job.getAnalysisInputId(),
                job.getStatus(),
                job.getTargetType(),
                job.getTargetFilePath(),
                job.getTargetPullRequestNumber(),
                job.getPromptVersion(),
                job.getAiProvider(),
                job.getAiModel(),
                job.getInputHash(),
                job.getGeneratedTestCount(),
                job.getErrorReason(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
