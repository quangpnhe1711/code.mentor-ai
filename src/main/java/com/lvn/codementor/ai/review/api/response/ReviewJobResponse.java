package com.lvn.codementor.ai.review.api.response;

import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.domain.ReviewJobStatus;
import com.lvn.codementor.ai.review.domain.ReviewType;
import java.time.Instant;
import java.util.UUID;

/**
 * Review-job view returned by the API. Metadata only: ids, lifecycle state, the copied input hash, and
 * safe fields. It never includes source content, the workspace path, provider errors, or credentials.
 */
public record ReviewJobResponse(
        UUID id,
        UUID organizationId,
        UUID repositoryId,
        UUID snapshotId,
        UUID analysisInputId,
        ReviewJobStatus status,
        ReviewType reviewType,
        Integer targetPullRequestNumber,
        String targetRef,
        String inputHash,
        String aiProvider,
        String aiModel,
        String promptVersion,
        int totalFindings,
        String errorReason,
        Instant startedAt,
        Instant completedAt,
        int attemptCount,
        int maxAttempts,
        Instant nextRunAt,
        String lastFailureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static ReviewJobResponse from(ReviewJob job) {
        return new ReviewJobResponse(
                job.getId(),
                job.getOrganizationId(),
                job.getRepositoryId(),
                job.getSnapshotId(),
                job.getAnalysisInputId(),
                job.getStatus(),
                job.getReviewType(),
                job.getTargetPullRequestNumber(),
                job.getTargetRef(),
                job.getInputHash(),
                job.getAiProvider(),
                job.getAiModel(),
                job.getPromptVersion(),
                job.getTotalFindings(),
                job.getErrorReason(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getNextRunAt(),
                job.getLastFailureReason(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
