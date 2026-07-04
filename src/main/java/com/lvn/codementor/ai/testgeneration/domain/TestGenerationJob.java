package com.lvn.codementor.ai.testgeneration.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "test_generation_jobs")
public class TestGenerationJob extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "analysis_input_id", nullable = false)
    private UUID analysisInputId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TestGenerationJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TestGenerationTargetType targetType;

    @Column(name = "target_file_path")
    private String targetFilePath;

    @Column(name = "target_pull_request_number")
    private Integer targetPullRequestNumber;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "ai_provider", nullable = false)
    private String aiProvider;

    @Column(name = "ai_model", nullable = false)
    private String aiModel;

    @Column(name = "input_hash")
    private String inputHash;

    @Column(name = "generated_test_count", nullable = false)
    private int generatedTestCount;

    @Column(name = "error_reason")
    private String errorReason;

    protected TestGenerationJob() {
        // for JPA
    }

    public TestGenerationJob(
            UUID organizationId,
            UUID repositoryId,
            UUID snapshotId,
            UUID analysisInputId,
            UUID createdByUserId,
            TestGenerationTargetType targetType,
            String targetFilePath,
            Integer targetPullRequestNumber,
            String inputHash) {
        this.organizationId = organizationId;
        this.repositoryId = repositoryId;
        this.snapshotId = snapshotId;
        this.analysisInputId = analysisInputId;
        this.createdByUserId = createdByUserId;
        this.status = TestGenerationJobStatus.GENERATING;
        this.targetType = targetType;
        this.targetFilePath = targetFilePath;
        this.targetPullRequestNumber = targetPullRequestNumber;
        this.promptVersion = "test-generation-local-v1";
        this.aiProvider = "LOCAL";
        this.aiModel = "deterministic-test-generator";
        this.inputHash = inputHash;
    }

    public void markCompleted(int generatedTestCount) {
        this.status = TestGenerationJobStatus.COMPLETED;
        this.generatedTestCount = generatedTestCount;
        this.errorReason = null;
    }

    public void markFailed(String reason) {
        this.status = TestGenerationJobStatus.FAILED;
        this.errorReason = reason;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public UUID getAnalysisInputId() {
        return analysisInputId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public TestGenerationJobStatus getStatus() {
        return status;
    }

    public TestGenerationTargetType getTargetType() {
        return targetType;
    }

    public String getTargetFilePath() {
        return targetFilePath;
    }

    public Integer getTargetPullRequestNumber() {
        return targetPullRequestNumber;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getInputHash() {
        return inputHash;
    }

    public int getGeneratedTestCount() {
        return generatedTestCount;
    }

    public String getErrorReason() {
        return errorReason;
    }
}
