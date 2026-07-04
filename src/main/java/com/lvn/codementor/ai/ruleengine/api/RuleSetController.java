package com.lvn.codementor.ai.ruleengine.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.ruleengine.api.request.CreateRuleRequest;
import com.lvn.codementor.ai.ruleengine.api.request.CreateRuleSetRequest;
import com.lvn.codementor.ai.ruleengine.api.response.RuleResponse;
import com.lvn.codementor.ai.ruleengine.api.response.RuleSetResponse;
import com.lvn.codementor.ai.ruleengine.application.RuleEngineService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/rule-sets")
public class RuleSetController {

    private final CurrentUser currentUser;
    private final RuleEngineService ruleEngineService;

    public RuleSetController(CurrentUser currentUser, RuleEngineService ruleEngineService) {
        this.currentUser = currentUser;
        this.ruleEngineService = ruleEngineService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RuleSetResponse>> createRuleSet(
            @PathVariable UUID organizationId, @RequestBody CreateRuleSetRequest request) {
        UUID userId = currentUser.requireUserId();
        RuleSetResponse data = RuleSetResponse.from(
                ruleEngineService.createRuleSet(userId, organizationId, request.name(), request.description()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    @GetMapping
    public ApiResponse<List<RuleSetResponse>> listRuleSets(@PathVariable UUID organizationId) {
        UUID userId = currentUser.requireUserId();
        List<RuleSetResponse> data = ruleEngineService.listRuleSets(userId, organizationId).stream()
                .map(RuleSetResponse::from)
                .toList();
        return ApiResponse.ok(data, requestId());
    }

    @GetMapping("/{ruleSetId}")
    public ApiResponse<RuleSetResponse> getRuleSet(
            @PathVariable UUID organizationId, @PathVariable UUID ruleSetId) {
        UUID userId = currentUser.requireUserId();
        return ApiResponse.ok(
                RuleSetResponse.from(ruleEngineService.getRuleSet(userId, organizationId, ruleSetId)),
                requestId());
    }

    @PostMapping("/{ruleSetId}/rules")
    public ResponseEntity<ApiResponse<RuleResponse>> createRule(
            @PathVariable UUID organizationId,
            @PathVariable UUID ruleSetId,
            @RequestBody CreateRuleRequest request) {
        UUID userId = currentUser.requireUserId();
        RuleResponse data = RuleResponse.from(ruleEngineService.createRule(
                userId,
                organizationId,
                ruleSetId,
                request.ruleKey(),
                request.title(),
                request.instruction(),
                request.category(),
                request.defaultSeverity(),
                request.enabled()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    @GetMapping("/{ruleSetId}/rules")
    public ApiResponse<List<RuleResponse>> listRules(
            @PathVariable UUID organizationId, @PathVariable UUID ruleSetId) {
        UUID userId = currentUser.requireUserId();
        List<RuleResponse> data = ruleEngineService.listRules(userId, organizationId, ruleSetId).stream()
                .map(RuleResponse::from)
                .toList();
        return ApiResponse.ok(data, requestId());
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
