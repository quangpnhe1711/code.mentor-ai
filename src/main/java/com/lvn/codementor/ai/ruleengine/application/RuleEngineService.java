package com.lvn.codementor.ai.ruleengine.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import com.lvn.codementor.ai.ruleengine.domain.RepositoryRuleSetAssignment;
import com.lvn.codementor.ai.ruleengine.domain.Rule;
import com.lvn.codementor.ai.ruleengine.domain.RuleSet;
import com.lvn.codementor.ai.ruleengine.persistence.RepositoryRuleSetAssignmentJpaRepository;
import com.lvn.codementor.ai.ruleengine.persistence.RuleJpaRepository;
import com.lvn.codementor.ai.ruleengine.persistence.RuleSetJpaRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule-set management and repository activation. Role-specific permissions are still TBD, so this
 * phase enforces organization membership consistently with repository/review APIs.
 */
@Service
public class RuleEngineService {

    private final OrganizationAccessService organizationAccess;
    private final ImportedRepositoryJpaRepository repositories;
    private final RuleSetJpaRepository ruleSets;
    private final RuleJpaRepository rules;
    private final RepositoryRuleSetAssignmentJpaRepository assignments;

    public RuleEngineService(
            OrganizationAccessService organizationAccess,
            ImportedRepositoryJpaRepository repositories,
            RuleSetJpaRepository ruleSets,
            RuleJpaRepository rules,
            RepositoryRuleSetAssignmentJpaRepository assignments) {
        this.organizationAccess = organizationAccess;
        this.repositories = repositories;
        this.ruleSets = ruleSets;
        this.rules = rules;
        this.assignments = assignments;
    }

    @Transactional
    public RuleSet createRuleSet(UUID userId, UUID organizationId, String name, String description) {
        organizationAccess.requireMember(userId, organizationId);
        String normalizedName = requiredText(name, "name");
        return ruleSets.save(new RuleSet(organizationId, normalizedName, blankToNull(description), userId));
    }

    @Transactional(readOnly = true)
    public List<RuleSet> listRuleSets(UUID userId, UUID organizationId) {
        organizationAccess.requireMember(userId, organizationId);
        return ruleSets.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    @Transactional(readOnly = true)
    public RuleSet getRuleSet(UUID userId, UUID organizationId, UUID ruleSetId) {
        organizationAccess.requireMember(userId, organizationId);
        return requireRuleSet(organizationId, ruleSetId);
    }

    @Transactional
    public Rule createRule(
            UUID userId,
            UUID organizationId,
            UUID ruleSetId,
            String ruleKey,
            String title,
            String instruction,
            String category,
            String defaultSeverity,
            Boolean enabled) {
        organizationAccess.requireMember(userId, organizationId);
        requireRuleSet(organizationId, ruleSetId);
        return rules.save(new Rule(
                organizationId,
                ruleSetId,
                requiredText(ruleKey, "ruleKey"),
                requiredText(title, "title"),
                requiredText(instruction, "instruction"),
                requiredText(category, "category"),
                resolveSeverity(defaultSeverity),
                enabled == null || enabled,
                true));
    }

    @Transactional(readOnly = true)
    public List<Rule> listRules(UUID userId, UUID organizationId, UUID ruleSetId) {
        organizationAccess.requireMember(userId, organizationId);
        requireRuleSet(organizationId, ruleSetId);
        return rules.findByRuleSetIdOrderByCreatedAtAsc(ruleSetId);
    }

    @Transactional
    public RepositoryRuleSetAssignment activateRuleSet(
            UUID userId, UUID organizationId, UUID repositoryId, UUID ruleSetId) {
        organizationAccess.requireMember(userId, organizationId);
        repositories
                .findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));
        requireRuleSet(organizationId, ruleSetId);

        assignments.findByOrganizationIdAndRepositoryIdAndActiveTrue(organizationId, repositoryId)
                .ifPresent(existing -> {
                    existing.deactivate();
                    assignments.saveAndFlush(existing);
                });

        return assignments.save(new RepositoryRuleSetAssignment(organizationId, repositoryId, ruleSetId, userId));
    }

    @Transactional(readOnly = true)
    public RepositoryRuleSetAssignment getActiveRuleSet(UUID userId, UUID organizationId, UUID repositoryId) {
        organizationAccess.requireMember(userId, organizationId);
        repositories
                .findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));
        return assignments.findByOrganizationIdAndRepositoryIdAndActiveTrue(organizationId, repositoryId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Active rule set not found"));
    }

    private RuleSet requireRuleSet(UUID organizationId, UUID ruleSetId) {
        return ruleSets.findByIdAndOrganizationId(ruleSetId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Rule set not found"));
    }

    private static ReviewFindingSeverity resolveSeverity(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReviewFindingSeverity.LOW;
        }
        try {
            return ReviewFindingSeverity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Unsupported defaultSeverity");
        }
    }

    private static String requiredText(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, field + " is required");
        }
        return raw.trim();
    }

    private static String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }
}
