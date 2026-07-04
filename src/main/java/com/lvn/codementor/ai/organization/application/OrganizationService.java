package com.lvn.codementor.ai.organization.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.identity.domain.User;
import com.lvn.codementor.ai.identity.persistence.UserJpaRepository;
import com.lvn.codementor.ai.organization.domain.Organization;
import com.lvn.codementor.ai.organization.domain.OrganizationMember;
import com.lvn.codementor.ai.organization.domain.OrganizationRole;
import com.lvn.codementor.ai.organization.domain.OrganizationType;
import com.lvn.codementor.ai.organization.persistence.OrganizationJpaRepository;
import com.lvn.codementor.ai.organization.persistence.OrganizationMemberJpaRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationJpaRepository organizations;
    private final OrganizationMemberJpaRepository members;
    private final UserJpaRepository users;
    private final OrganizationAccessService access;

    public OrganizationService(
            OrganizationJpaRepository organizations,
            OrganizationMemberJpaRepository members,
            UserJpaRepository users,
            OrganizationAccessService access) {
        this.organizations = organizations;
        this.members = members;
        this.users = users;
        this.access = access;
    }

    @Transactional
    public Organization createTeamOrganization(UUID userId, String name, String requestedSlug) {
        String normalizedName = required(name, "name");
        String slug = slugFor(normalizedName, requestedSlug);
        if (organizations.existsBySlug(slug)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Organization slug is already in use");
        }

        Organization organization =
                organizations.save(new Organization(normalizedName, slug, OrganizationType.TEAM, userId));
        members.save(new OrganizationMember(organization.getId(), userId, OrganizationRole.OWNER));
        return organization;
    }

    public List<Organization> listForUser(UUID userId) {
        List<UUID> organizationIds = members.findByUserId(userId).stream()
                .map(OrganizationMember::getOrganizationId)
                .toList();
        return organizations.findAllById(organizationIds).stream()
                .sorted(Comparator.comparing(Organization::getCreatedAt))
                .toList();
    }

    public List<OrganizationMember> listMembers(UUID userId, UUID organizationId) {
        access.requireMember(userId, organizationId);
        organizations.findById(organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Organization not found"));
        return members.findByOrganizationIdOrderByCreatedAtAsc(organizationId);
    }

    @Transactional
    public OrganizationMember addExistingUser(
            UUID actorUserId, UUID organizationId, String githubUserId, String role) {
        access.requireAdmin(actorUserId, organizationId);
        organizations.findById(organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Organization not found"));
        User user = users.findByGithubUserId(required(githubUserId, "githubUserId"))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
        OrganizationRole memberRole = resolveInvitableRole(role);
        if (members.findByOrganizationIdAndUserId(organizationId, user.getId()).isPresent()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "User is already a member of this organization");
        }
        return members.save(new OrganizationMember(organizationId, user.getId(), memberRole));
    }

    public User getUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    private static OrganizationRole resolveInvitableRole(String raw) {
        String normalized = required(raw, "role").toUpperCase(Locale.ROOT);
        try {
            OrganizationRole role = OrganizationRole.valueOf(normalized);
            if (role == OrganizationRole.OWNER) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "OWNER role cannot be assigned through invitation");
            }
            return role;
        } catch (IllegalArgumentException e) {
            throw new AppException(
                    ErrorCode.VALIDATION_FAILED,
                    "Unsupported role; supported values are ADMIN, REVIEWER, DEVELOPER, VIEWER");
        }
    }

    private static String slugFor(String name, String requestedSlug) {
        String source = requestedSlug == null || requestedSlug.isBlank() ? name : requestedSlug;
        String slug = source.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "slug must contain letters or numbers");
        }
        if (requestedSlug == null || requestedSlug.isBlank()) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return slug;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, field + " is required");
        }
        return value.trim();
    }
}
