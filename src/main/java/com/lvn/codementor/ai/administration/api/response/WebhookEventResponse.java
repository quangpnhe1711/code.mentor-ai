package com.lvn.codementor.ai.administration.api.response;

import com.lvn.codementor.ai.administration.application.WebhookIntakeResult;
import com.lvn.codementor.ai.administration.domain.WebhookEvent;
import com.lvn.codementor.ai.administration.domain.WebhookProcessingStatus;
import com.lvn.codementor.ai.administration.domain.WebhookProvider;
import java.time.Instant;
import java.util.UUID;

public record WebhookEventResponse(
        UUID id,
        WebhookProvider provider,
        String deliveryId,
        String eventType,
        String action,
        String externalRepoId,
        String repositoryFullName,
        boolean signatureVerified,
        WebhookProcessingStatus processingStatus,
        UUID reviewJobId,
        String errorReason,
        boolean replay,
        Instant createdAt,
        Instant updatedAt) {

    public static WebhookEventResponse from(WebhookIntakeResult result) {
        WebhookEvent event = result.event();
        return new WebhookEventResponse(
                event.getId(),
                event.getProvider(),
                event.getDeliveryId(),
                event.getEventType(),
                event.getAction(),
                event.getExternalRepoId(),
                event.getRepositoryFullName(),
                event.isSignatureVerified(),
                event.getProcessingStatus(),
                event.getReviewJobId(),
                event.getErrorReason(),
                result.replay(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
