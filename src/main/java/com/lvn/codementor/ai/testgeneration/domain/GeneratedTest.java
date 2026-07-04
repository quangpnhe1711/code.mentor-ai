package com.lvn.codementor.ai.testgeneration.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "generated_tests")
public class GeneratedTest extends BaseEntity {

    @Column(name = "test_generation_job_id", nullable = false)
    private UUID testGenerationJobId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "rationale", nullable = false)
    private String rationale;

    protected GeneratedTest() {
        // for JPA
    }

    public GeneratedTest(
            UUID testGenerationJobId,
            UUID organizationId,
            UUID repositoryId,
            String filePath,
            String language,
            String content,
            String rationale) {
        this.testGenerationJobId = testGenerationJobId;
        this.organizationId = organizationId;
        this.repositoryId = repositoryId;
        this.filePath = filePath;
        this.language = language;
        this.content = content;
        this.rationale = rationale;
    }

    public UUID getTestGenerationJobId() {
        return testGenerationJobId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getLanguage() {
        return language;
    }

    public String getContent() {
        return content;
    }

    public String getRationale() {
        return rationale;
    }
}
