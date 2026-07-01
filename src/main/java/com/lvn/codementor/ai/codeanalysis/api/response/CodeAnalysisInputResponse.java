package com.lvn.codementor.ai.codeanalysis.api.response;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInputStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Analysis-input view returned by the API. Intentionally metadata-only: it never includes raw or
 * masked source content, the workspace path, or any credential/secret data.
 */
public record CodeAnalysisInputResponse(
        UUID id,
        UUID snapshotId,
        UUID repositoryId,
        UUID organizationId,
        CodeAnalysisInputStatus status,
        String inputHash,
        int includedFileCount,
        int skippedFileCount,
        int maskedSecretCount,
        long totalInputBytes,
        String errorReason,
        Instant createdAt,
        Instant updatedAt) {

    public static CodeAnalysisInputResponse from(CodeAnalysisInput input) {
        return new CodeAnalysisInputResponse(
                input.getId(),
                input.getSnapshotId(),
                input.getRepositoryId(),
                input.getOrganizationId(),
                input.getStatus(),
                input.getInputHash(),
                input.getIncludedFileCount(),
                input.getSkippedFileCount(),
                input.getMaskedSecretCount(),
                input.getTotalInputBytes(),
                input.getErrorReason(),
                input.getCreatedAt(),
                input.getUpdatedAt());
    }
}
