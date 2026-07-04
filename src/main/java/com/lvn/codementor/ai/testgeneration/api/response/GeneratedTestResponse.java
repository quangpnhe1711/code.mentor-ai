package com.lvn.codementor.ai.testgeneration.api.response;

import com.lvn.codementor.ai.testgeneration.domain.GeneratedTest;
import java.time.Instant;
import java.util.UUID;

public record GeneratedTestResponse(
        UUID id,
        UUID testGenerationJobId,
        UUID organizationId,
        UUID repositoryId,
        String filePath,
        String language,
        String content,
        String rationale,
        Instant createdAt,
        Instant updatedAt) {

    public static GeneratedTestResponse from(GeneratedTest generatedTest) {
        return new GeneratedTestResponse(
                generatedTest.getId(),
                generatedTest.getTestGenerationJobId(),
                generatedTest.getOrganizationId(),
                generatedTest.getRepositoryId(),
                generatedTest.getFilePath(),
                generatedTest.getLanguage(),
                generatedTest.getContent(),
                generatedTest.getRationale(),
                generatedTest.getCreatedAt(),
                generatedTest.getUpdatedAt());
    }
}
