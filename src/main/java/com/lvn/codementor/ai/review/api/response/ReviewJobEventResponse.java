package com.lvn.codementor.ai.review.api.response;

import com.lvn.codementor.ai.review.domain.ReviewJobEvent;
import com.lvn.codementor.ai.review.domain.ReviewJobStatus;
import java.time.Instant;
import java.util.UUID;

/** Review-job status-transition event view. The message is always caller-safe. */
public record ReviewJobEventResponse(
        UUID id, UUID reviewJobId, ReviewJobStatus status, String message, Instant createdAt) {

    public static ReviewJobEventResponse from(ReviewJobEvent event) {
        return new ReviewJobEventResponse(
                event.getId(),
                event.getReviewJobId(),
                event.getStatus(),
                event.getMessage(),
                event.getCreatedAt());
    }
}
