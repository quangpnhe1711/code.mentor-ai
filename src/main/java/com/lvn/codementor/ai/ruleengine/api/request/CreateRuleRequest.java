package com.lvn.codementor.ai.ruleengine.api.request;

public record CreateRuleRequest(
        String ruleKey,
        String title,
        String instruction,
        String category,
        String defaultSeverity,
        Boolean enabled) {
}
