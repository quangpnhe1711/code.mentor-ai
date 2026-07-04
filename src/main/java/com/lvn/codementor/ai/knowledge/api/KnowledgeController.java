package com.lvn.codementor.ai.knowledge.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.knowledge.api.request.AskRepositoryQuestionRequest;
import com.lvn.codementor.ai.knowledge.api.response.KnowledgeAnswerResponse;
import com.lvn.codementor.ai.knowledge.application.KnowledgeQueryService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/knowledge")
public class KnowledgeController {

    private final CurrentUser currentUser;
    private final KnowledgeQueryService knowledgeQueryService;

    public KnowledgeController(CurrentUser currentUser, KnowledgeQueryService knowledgeQueryService) {
        this.currentUser = currentUser;
        this.knowledgeQueryService = knowledgeQueryService;
    }

    @PostMapping("/questions")
    public ResponseEntity<ApiResponse<KnowledgeAnswerResponse>> ask(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @RequestBody AskRepositoryQuestionRequest request) {
        UUID userId = currentUser.requireUserId();
        KnowledgeAnswerResponse data = KnowledgeAnswerResponse.from(
                knowledgeQueryService.ask(userId, organizationId, repositoryId, request == null ? null : request.question()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
