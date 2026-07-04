package com.lvn.codementor.ai.organization.api.response;

import com.lvn.codementor.ai.identity.domain.User;
import com.lvn.codementor.ai.organization.domain.OrganizationMember;
import com.lvn.codementor.ai.organization.domain.OrganizationRole;
import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberResponse(
        UUID id,
        UUID organizationId,
        UUID userId,
        String githubUserId,
        String githubLogin,
        String displayName,
        OrganizationRole role,
        Instant createdAt,
        Instant updatedAt) {

    public static OrganizationMemberResponse from(OrganizationMember member, User user) {
        return new OrganizationMemberResponse(
                member.getId(),
                member.getOrganizationId(),
                member.getUserId(),
                user.getGithubUserId(),
                user.getGithubLogin(),
                user.getDisplayName(),
                member.getRole(),
                member.getCreatedAt(),
                member.getUpdatedAt());
    }
}
