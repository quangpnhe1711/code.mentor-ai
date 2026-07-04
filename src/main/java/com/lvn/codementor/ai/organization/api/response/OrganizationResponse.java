package com.lvn.codementor.ai.organization.api.response;

import com.lvn.codementor.ai.organization.domain.Organization;
import com.lvn.codementor.ai.organization.domain.OrganizationType;
import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        OrganizationType type,
        UUID ownerUserId,
        Instant createdAt,
        Instant updatedAt) {

    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getType(),
                organization.getOwnerUserId(),
                organization.getCreatedAt(),
                organization.getUpdatedAt());
    }
}
