package com.lvn.codementor.ai.organization.api.request;

/** Request body for creating a team organization. */
public record CreateOrganizationRequest(String name, String slug) {
}
