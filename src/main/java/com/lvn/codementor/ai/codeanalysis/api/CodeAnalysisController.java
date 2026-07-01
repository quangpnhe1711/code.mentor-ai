package com.lvn.codementor.ai.codeanalysis.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.codeanalysis.api.response.CodeAnalysisInputResponse;
import com.lvn.codementor.ai.codeanalysis.application.ReviewInputBuilderService;
import com.lvn.codementor.ai.codeanalysis.application.command.BuildReviewInputCommand;
import com.lvn.codementor.ai.codeanalysis.application.result.BuildReviewInputResult;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sanitized review-input preparation for a snapshot (pre-AI phase; no provider is called). Requires a
 * platform JWT (SecurityConfig) and organization membership (enforced in the service). Responses are
 * metadata-only — never raw/masked source content, the workspace path, or credential data.
 */
@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}")
public class CodeAnalysisController {

    private final CurrentUser currentUser;
    private final ReviewInputBuilderService builderService;

    public CodeAnalysisController(CurrentUser currentUser, ReviewInputBuilderService builderService) {
        this.currentUser = currentUser;
        this.builderService = builderService;
    }

    @PostMapping("/snapshots/{snapshotId}/analysis-inputs")
    public ResponseEntity<ApiResponse<CodeAnalysisInputResponse>> buildAnalysisInput(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID snapshotId) {
        UUID userId = currentUser.requireUserId();
        BuildReviewInputResult result = builderService.build(
                new BuildReviewInputCommand(userId, organizationId, repositoryId, snapshotId));
        ApiResponse<CodeAnalysisInputResponse> body =
                ApiResponse.ok(CodeAnalysisInputResponse.from(result.input()), requestId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/analysis-inputs/{analysisInputId}")
    public ApiResponse<CodeAnalysisInputResponse> getAnalysisInput(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID analysisInputId) {
        UUID userId = currentUser.requireUserId();
        CodeAnalysisInput input = builderService.get(userId, organizationId, repositoryId, analysisInputId);
        return ApiResponse.ok(CodeAnalysisInputResponse.from(input), requestId());
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
