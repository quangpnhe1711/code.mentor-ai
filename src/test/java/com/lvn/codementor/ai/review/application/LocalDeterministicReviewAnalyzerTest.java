package com.lvn.codementor.ai.review.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.review.application.LocalDeterministicReviewAnalyzer.Finding;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit checks for the deterministic local analyzer rules (no Spring, no DB). */
class LocalDeterministicReviewAnalyzerTest {

    private final LocalDeterministicReviewAnalyzer analyzer = new LocalDeterministicReviewAnalyzer();

    @Test
    void findsTodoAndDebugAndErrorHandling() {
        String java = "class A {\n"
                + "  void run() {\n"
                + "    // TODO: later\n"
                + "    System.out.println(\"x\");\n"
                + "    try { work(); } catch (Exception e) {}\n"
                + "    try { work(); } catch (Exception e) { e.printStackTrace(); }\n"
                + "  }\n"
                + "}\n";
        List<Finding> findings = analyzer.analyze(List.of(new CodeAnalysisFile("A.java", java, 0)));

        assertThat(ruleIds(findings)).contains("todo-fixme", "system-out-println", "empty-catch", "print-stack-trace");
        Finding todo = byRule(findings, "todo-fixme");
        assertThat(todo.severity()).isEqualTo(ReviewFindingSeverity.LOW);
        assertThat(todo.lineStart()).isEqualTo(3);
        // No source snippet leaks into the finding text.
        assertThat(todo.description()).doesNotContain("later");
    }

    @Test
    void findsConsoleLogOnlyInJsTs() {
        String ts = "export function f() {\n  console.log(\"debug\");\n}\n";
        assertThat(ruleIds(analyzer.analyze(List.of(new CodeAnalysisFile("a.ts", ts, 0))))).contains("console-log");
        // A .java file with the literal text is not treated as JS/TS console.log.
        assertThat(ruleIds(analyzer.analyze(List.of(new CodeAnalysisFile("A.java", ts, 0)))))
                .doesNotContain("console-log");
    }

    @Test
    void maskedSecretDoesNotTriggerButLeftoverDoes() {
        // Already-masked value -> no security finding.
        String masked = "password = [REDACTED_SECRET]\n";
        assertThat(ruleIds(analyzer.analyze(List.of(new CodeAnalysisFile("c.properties", masked, 1)))))
                .doesNotContain("hardcoded-secret");
        // A short value the masker leaves alone -> flagged HIGH/SECURITY.
        String leftover = "password = abc\n";
        List<Finding> findings = analyzer.analyze(List.of(new CodeAnalysisFile("c.properties", leftover, 0)));
        assertThat(ruleIds(findings)).contains("hardcoded-secret");
        assertThat(byRule(findings, "hardcoded-secret").severity()).isEqualTo(ReviewFindingSeverity.HIGH);
        assertThat(byRule(findings, "hardcoded-secret").description()).doesNotContain("abc");
    }

    private static List<String> ruleIds(List<Finding> findings) {
        return findings.stream().map(Finding::ruleId).toList();
    }

    private static Finding byRule(List<Finding> findings, String ruleId) {
        return findings.stream().filter(f -> f.ruleId().equals(ruleId)).findFirst().orElseThrow();
    }
}
