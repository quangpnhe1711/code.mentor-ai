package com.lvn.codementor.ai.ruleengine.application;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.review.application.LocalDeterministicReviewAnalyzer;
import com.lvn.codementor.ai.review.application.LocalDeterministicReviewAnalyzer.Finding;
import com.lvn.codementor.ai.ruleengine.domain.Rule;
import com.lvn.codementor.ai.ruleengine.persistence.RepositoryRuleSetAssignmentJpaRepository;
import com.lvn.codementor.ai.ruleengine.persistence.RuleJpaRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/**
 * Local rule evaluator for the pre-AI phase.
 *
 * <p>Natural-language interpretation is intentionally conservative until the AI provider phase lands.
 * The evaluator supports explicit patterns embedded in rule instructions, such as quoted phrases,
 * {@code contains "text"}, and {@code regex:...}. Findings are generic and never persist snippets.
 */
@Component
public class LocalRuleEngineEvaluator {

    private static final Pattern REGEX_DIRECTIVE =
            Pattern.compile("(?is)\\bregex\\s*:\\s*(?:`([^`]+)`|/([^/]+)/|([^\\r\\n]+))");
    private static final Pattern CONTAINS_DIRECTIVE =
            Pattern.compile("(?is)\\bcontains\\s+[\"'`]([^\"'`]+)[\"'`]");
    private static final Pattern QUOTED_PHRASE =
            Pattern.compile("[\"'`]([^\"'`]{2,})[\"'`]");
    private static final int MAX_FINDINGS_PER_RULE = 25;

    private final RepositoryRuleSetAssignmentJpaRepository assignments;
    private final RuleJpaRepository rules;

    public LocalRuleEngineEvaluator(
            RepositoryRuleSetAssignmentJpaRepository assignments,
            RuleJpaRepository rules) {
        this.assignments = assignments;
        this.rules = rules;
    }

    public List<Finding> evaluate(UUID organizationId, UUID repositoryId, List<CodeAnalysisFile> files) {
        Optional<UUID> activeRuleSetId = assignments
                .findByOrganizationIdAndRepositoryIdAndActiveTrue(organizationId, repositoryId)
                .map(a -> a.getRuleSetId());
        if (activeRuleSetId.isEmpty()) {
            return List.of();
        }

        List<Rule> activeRules = rules.findByRuleSetIdOrderByCreatedAtAsc(activeRuleSetId.get()).stream()
                .filter(Rule::isEnabled)
                .toList();
        if (activeRules.isEmpty()) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();
        for (Rule rule : activeRules) {
            evaluateRule(rule, files, findings);
        }
        return findings;
    }

    private void evaluateRule(Rule rule, List<CodeAnalysisFile> files, List<Finding> out) {
        List<CompiledRulePattern> patterns = compilePatterns(rule);
        if (patterns.isEmpty()) {
            return;
        }

        int emitted = 0;
        for (CodeAnalysisFile file : files) {
            String[] lines = file.sanitizedContent().split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (matchesAny(patterns, lines[i])) {
                    out.add(toFinding(rule, file.path(), i + 1, lines[i]));
                    emitted++;
                    if (emitted >= MAX_FINDINGS_PER_RULE) {
                        return;
                    }
                }
            }
        }
    }

    private static List<CompiledRulePattern> compilePatterns(Rule rule) {
        String instruction = rule.getInstruction() == null ? "" : rule.getInstruction();
        List<CompiledRulePattern> patterns = new ArrayList<>();

        var regexMatcher = REGEX_DIRECTIVE.matcher(instruction);
        while (regexMatcher.find()) {
            String raw = firstNonNull(regexMatcher.group(1), regexMatcher.group(2), regexMatcher.group(3));
            addRegexPattern(patterns, raw);
        }

        var containsMatcher = CONTAINS_DIRECTIVE.matcher(instruction);
        while (containsMatcher.find()) {
            patterns.add(CompiledRulePattern.literal(containsMatcher.group(1)));
        }

        var phraseMatcher = QUOTED_PHRASE.matcher(instruction);
        while (phraseMatcher.find()) {
            patterns.add(CompiledRulePattern.literal(phraseMatcher.group(1)));
        }

        addConventionFallback(rule, patterns);
        return patterns;
    }

    private static void addRegexPattern(List<CompiledRulePattern> patterns, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            patterns.add(CompiledRulePattern.regex(raw.trim()));
        } catch (PatternSyntaxException ignored) {
            // Invalid custom pattern is ignored for this pre-AI evaluator; rule management validation
            // remains generic so teams can draft natural-language instructions freely.
        }
    }

    private static void addConventionFallback(Rule rule, List<CompiledRulePattern> patterns) {
        String text = (rule.getTitle() + " " + rule.getInstruction()).toLowerCase();
        if (text.contains("hardcoded") && (text.contains("ui") || text.contains("user-facing"))) {
            patterns.add(CompiledRulePattern.regex("\"[^\"]{4,}\""));
            patterns.add(CompiledRulePattern.regex("'[^']{4,}'"));
        }
        if (text.contains("system.out.println")) {
            patterns.add(CompiledRulePattern.literal("System.out.println"));
        }
        if (text.contains("console.log")) {
            patterns.add(CompiledRulePattern.literal("console.log"));
        }
    }

    private static boolean matchesAny(List<CompiledRulePattern> patterns, String line) {
        for (CompiledRulePattern pattern : patterns) {
            if (pattern.matches(line)) {
                return true;
            }
        }
        return false;
    }

    private static Finding toFinding(Rule rule, String path, int lineNo, String line) {
        return new Finding(
                path,
                lineNo,
                lineNo,
                rule.getDefaultSeverity(),
                rule.getCategory(),
                "Rule violation: " + rule.getTitle(),
                "A custom rule matched this line.",
                "Review the line against the active rule set and update the code or disable the rule if not applicable.",
                rule.getRuleKey(),
                BigDecimal.valueOf(0.75),
                LocalDeterministicReviewAnalyzer.snippetOf(line));
    }

    private static String firstNonNull(String a, String b, String c) {
        if (a != null) {
            return a;
        }
        return b != null ? b : c;
    }

    private record CompiledRulePattern(String literal, Pattern regex) {

        static CompiledRulePattern literal(String literal) {
            return new CompiledRulePattern(literal, null);
        }

        static CompiledRulePattern regex(String regex) {
            return new CompiledRulePattern(null, Pattern.compile(regex));
        }

        boolean matches(String line) {
            if (literal != null) {
                return line.contains(literal);
            }
            return regex.matcher(line).find();
        }
    }
}
