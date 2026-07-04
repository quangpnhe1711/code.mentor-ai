package com.lvn.codementor.ai.ruleengine.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One review convention. Custom rules may use natural-language instructions.
 */
@Entity
@Table(name = "rules")
public class Rule extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "rule_set_id", nullable = false)
    private UUID ruleSetId;

    @Column(name = "rule_key", nullable = false)
    private String ruleKey;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "instruction", nullable = false)
    private String instruction;

    @Column(name = "category", nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_severity", nullable = false)
    private ReviewFindingSeverity defaultSeverity;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "custom", nullable = false)
    private boolean custom;

    protected Rule() {
        // for JPA
    }

    public Rule(
            UUID organizationId,
            UUID ruleSetId,
            String ruleKey,
            String title,
            String instruction,
            String category,
            ReviewFindingSeverity defaultSeverity,
            boolean enabled,
            boolean custom) {
        this.organizationId = organizationId;
        this.ruleSetId = ruleSetId;
        this.ruleKey = ruleKey;
        this.title = title;
        this.instruction = instruction;
        this.category = category;
        this.defaultSeverity = defaultSeverity;
        this.enabled = enabled;
        this.custom = custom;
    }

    public void update(
            String title,
            String instruction,
            String category,
            ReviewFindingSeverity defaultSeverity,
            boolean enabled) {
        this.title = title;
        this.instruction = instruction;
        this.category = category;
        this.defaultSeverity = defaultSeverity;
        this.enabled = enabled;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRuleSetId() {
        return ruleSetId;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public String getTitle() {
        return title;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getCategory() {
        return category;
    }

    public ReviewFindingSeverity getDefaultSeverity() {
        return defaultSeverity;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCustom() {
        return custom;
    }
}
