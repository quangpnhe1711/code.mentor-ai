package com.lvn.codementor.ai.ruleengine.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.ruleengine.api.response.RepositoryRuleSetAssignmentResponse;
import com.lvn.codementor.ai.ruleengine.application.RuleEngineService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}")
public class RepositoryRuleSetController {

    private final CurrentUser currentUser;
    private final RuleEngineService ruleEngineService;

    public RepositoryRuleSetController(CurrentUser currentUser, RuleEngineService ruleEngineService) {
        this.currentUser = currentUser;
        this.ruleEngineService = ruleEngineService;
    }

    @PostMapping("/rule-sets/{ruleSetId}/activate")
    public ResponseEntity<ApiResponse<RepositoryRuleSetAssignmentResponse>> activateRuleSet(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID ruleSetId) {
        UUID userId = currentUser.requireUserId();
        RepositoryRuleSetAssignmentResponse data = RepositoryRuleSetAssignmentResponse.from(
                ruleEngineService.activateRuleSet(userId, organizationId, repositoryId, ruleSetId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    @GetMapping("/active-rule-set")
    public ApiResponse<RepositoryRuleSetAssignmentResponse> getActiveRuleSet(
            @PathVariable UUID organizationId, @PathVariable UUID repositoryId) {
        UUID userId = currentUser.requireUserId();
        return ApiResponse.ok(
                RepositoryRuleSetAssignmentResponse.from(
                        ruleEngineService.getActiveRuleSet(userId, organizationId, repositoryId)),
                requestId());
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
