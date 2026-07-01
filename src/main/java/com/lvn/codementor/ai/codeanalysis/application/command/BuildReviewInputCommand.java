package com.lvn.codementor.ai.codeanalysis.application.command;

import java.util.UUID;

/**
 * Request to build a sanitized review input from a snapshot, scoped to the acting user and the
 * organization/repository/snapshot in the request path.
 */
public record BuildReviewInputCommand(
        UUID userId, UUID organizationId, UUID repositoryId, UUID snapshotId) {
}
