package com.lvn.codementor.ai.organization.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.identity.domain.User;
import com.lvn.codementor.ai.organization.api.request.AddOrganizationMemberRequest;
import com.lvn.codementor.ai.organization.api.request.CreateOrganizationRequest;
import com.lvn.codementor.ai.organization.api.response.OrganizationMemberResponse;
import com.lvn.codementor.ai.organization.api.response.OrganizationResponse;
import com.lvn.codementor.ai.organization.application.OrganizationService;
import com.lvn.codementor.ai.organization.domain.OrganizationMember;
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
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final CurrentUser currentUser;
    private final OrganizationService organizationService;

    public OrganizationController(CurrentUser currentUser, OrganizationService organizationService) {
        this.currentUser = currentUser;
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @RequestBody CreateOrganizationRequest request) {
        UUID userId = currentUser.requireUserId();
        OrganizationResponse data = OrganizationResponse.from(
                organizationService.createTeamOrganization(userId, request.name(), request.slug()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    @GetMapping
    public ApiResponse<List<OrganizationResponse>> listOrganizations() {
        UUID userId = currentUser.requireUserId();
        List<OrganizationResponse> data = organizationService.listForUser(userId).stream()
                .map(OrganizationResponse::from)
                .toList();
        return ApiResponse.ok(data, requestId());
    }

    @GetMapping("/{organizationId}/members")
    public ApiResponse<List<OrganizationMemberResponse>> listMembers(@PathVariable UUID organizationId) {
        UUID userId = currentUser.requireUserId();
        List<OrganizationMemberResponse> data = organizationService.listMembers(userId, organizationId).stream()
                .map(member -> OrganizationMemberResponse.from(member, organizationService.getUser(member.getUserId())))
                .toList();
        return ApiResponse.ok(data, requestId());
    }

    @PostMapping("/{organizationId}/members")
    public ResponseEntity<ApiResponse<OrganizationMemberResponse>> addMember(
            @PathVariable UUID organizationId,
            @RequestBody AddOrganizationMemberRequest request) {
        UUID userId = currentUser.requireUserId();
        OrganizationMember member = organizationService.addExistingUser(
                userId, organizationId, request.githubUserId(), request.role());
        User addedUser = organizationService.getUser(member.getUserId());
        OrganizationMemberResponse data = OrganizationMemberResponse.from(member, addedUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
