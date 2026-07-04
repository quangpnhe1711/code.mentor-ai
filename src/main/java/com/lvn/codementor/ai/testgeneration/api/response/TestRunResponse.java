package com.lvn.codementor.ai.testgeneration.api.response;

import com.lvn.codementor.ai.testgeneration.application.TestRunResult;
import com.lvn.codementor.ai.testgeneration.domain.TestRun;
import com.lvn.codementor.ai.testgeneration.domain.TestRunStatus;
import java.time.Instant;
import java.util.UUID;

public record TestRunResponse(
        UUID id,
        UUID generatedTestId,
        UUID organizationId,
        UUID repositoryId,
        TestRunStatus status,
        String sandboxMode,
        int timeoutSeconds,
        Instant startedAt,
        Instant completedAt,
        Integer exitCode,
        long durationMs,
        String stdout,
        String stderr,
        String failureExplanation,
        Instant createdAt,
        Instant updatedAt) {

    public static TestRunResponse from(TestRun run) {
        return new TestRunResponse(
                run.getId(),
                run.getGeneratedTestId(),
                run.getOrganizationId(),
                run.getRepositoryId(),
                run.getStatus(),
                run.getSandboxMode(),
                run.getTimeoutSeconds(),
                run.getStartedAt(),
                run.getCompletedAt(),
                null,
                0,
                null,
                null,
                null,
                run.getCreatedAt(),
                run.getUpdatedAt());
    }

    public static TestRunResponse from(TestRunResult result) {
        TestRun run = result.run();
        return new TestRunResponse(
                run.getId(),
                run.getGeneratedTestId(),
                run.getOrganizationId(),
                run.getRepositoryId(),
                run.getStatus(),
                run.getSandboxMode(),
                run.getTimeoutSeconds(),
                run.getStartedAt(),
                run.getCompletedAt(),
                result.result().getExitCode(),
                result.result().getDurationMs(),
                result.result().getStdout(),
                result.result().getStderr(),
                result.result().getFailureExplanation(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }
}
