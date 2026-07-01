package com.lvn.codementor.ai.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An append-only status-transition record for a {@link ReviewJob}. Unlike other aggregates this row is
 * never updated, so it intentionally does not extend {@code BaseEntity} — it has {@code created_at} but
 * no {@code updated_at}. The {@code message} is always caller-safe (no path/content/secret/stack trace).
 */
@Entity
@Table(name = "review_job_events")
public class ReviewJobEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "review_job_id", nullable = false)
    private UUID reviewJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewJobStatus status;

    @Column(name = "message")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReviewJobEvent() {
        // for JPA
    }

    public ReviewJobEvent(UUID reviewJobId, ReviewJobStatus status, String message) {
        this.id = UUID.randomUUID();
        this.reviewJobId = reviewJobId;
        this.status = status;
        this.message = message;
    }

    @PrePersist
    void onPersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getReviewJobId() {
        return reviewJobId;
    }

    public ReviewJobStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
