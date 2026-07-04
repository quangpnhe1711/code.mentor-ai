package com.lvn.codementor.ai.administration.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private WebhookProvider provider;

    @Column(name = "delivery_id", nullable = false)
    private String deliveryId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "action")
    private String action;

    @Column(name = "external_repo_id")
    private String externalRepoId;

    @Column(name = "repository_full_name")
    private String repositoryFullName;

    @Column(name = "payload_sha256", nullable = false)
    private String payloadSha256;

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private WebhookProcessingStatus processingStatus;

    @Column(name = "review_job_id")
    private UUID reviewJobId;

    @Column(name = "error_reason")
    private String errorReason;

    protected WebhookEvent() {
        // for JPA
    }

    public WebhookEvent(
            WebhookProvider provider,
            String deliveryId,
            String eventType,
            String action,
            String externalRepoId,
            String repositoryFullName,
            String payloadSha256,
            boolean signatureVerified) {
        this.provider = provider;
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.action = action;
        this.externalRepoId = externalRepoId;
        this.repositoryFullName = repositoryFullName;
        this.payloadSha256 = payloadSha256;
        this.signatureVerified = signatureVerified;
        this.processingStatus = WebhookProcessingStatus.RECEIVED;
    }

    public void markProcessed(UUID reviewJobId) {
        this.processingStatus = WebhookProcessingStatus.PROCESSED;
        this.reviewJobId = reviewJobId;
        this.errorReason = null;
    }

    public void markIgnored(String reason) {
        this.processingStatus = WebhookProcessingStatus.IGNORED;
        this.errorReason = reason;
    }

    public void markFailed(String reason) {
        this.processingStatus = WebhookProcessingStatus.FAILED;
        this.errorReason = reason;
    }

    public WebhookProvider getProvider() {
        return provider;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAction() {
        return action;
    }

    public String getExternalRepoId() {
        return externalRepoId;
    }

    public String getRepositoryFullName() {
        return repositoryFullName;
    }

    public String getPayloadSha256() {
        return payloadSha256;
    }

    public boolean isSignatureVerified() {
        return signatureVerified;
    }

    public WebhookProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public UUID getReviewJobId() {
        return reviewJobId;
    }

    public String getErrorReason() {
        return errorReason;
    }
}
