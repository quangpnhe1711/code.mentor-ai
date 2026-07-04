package com.lvn.codementor.ai.testgeneration.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_runs")
public class TestRun extends BaseEntity {

    @Column(name = "generated_test_id", nullable = false)
    private UUID generatedTestId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TestRunStatus status;

    @Column(name = "sandbox_mode", nullable = false)
    private String sandboxMode;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TestRun() {
        // for JPA
    }

    public TestRun(UUID generatedTestId, UUID organizationId, UUID repositoryId, int timeoutSeconds) {
        this.generatedTestId = generatedTestId;
        this.organizationId = organizationId;
        this.repositoryId = repositoryId;
        this.status = TestRunStatus.PENDING;
        this.sandboxMode = "SANDBOX_SIMULATED";
        this.timeoutSeconds = timeoutSeconds;
    }

    public void markRunning() {
        this.status = TestRunStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void markCompleted(TestRunStatus status) {
        this.status = status;
        this.completedAt = Instant.now();
    }

    public UUID getGeneratedTestId() {
        return generatedTestId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public TestRunStatus getStatus() {
        return status;
    }

    public String getSandboxMode() {
        return sandboxMode;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
