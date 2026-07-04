package com.lvn.codementor.ai.organization.api.request;

/** Adds an already-authenticated GitHub user to an organization. */
public record AddOrganizationMemberRequest(String githubUserId, String role) {
}
