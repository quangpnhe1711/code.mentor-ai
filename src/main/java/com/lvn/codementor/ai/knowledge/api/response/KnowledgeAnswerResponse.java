package com.lvn.codementor.ai.knowledge.api.response;

import com.lvn.codementor.ai.knowledge.application.KnowledgeAnswer;
import java.util.List;
import java.util.UUID;

public record KnowledgeAnswerResponse(
        UUID sessionId,
        UUID organizationId,
        UUID repositoryId,
        UUID snapshotId,
        String answer,
        List<CitationResponse> citations) {

    public static KnowledgeAnswerResponse from(KnowledgeAnswer answer) {
        return new KnowledgeAnswerResponse(
                answer.session().getId(),
                answer.session().getOrganizationId(),
                answer.session().getRepositoryId(),
                answer.session().getSnapshotId(),
                answer.answer(),
                answer.citations().stream().map(CitationResponse::from).toList());
    }
}
