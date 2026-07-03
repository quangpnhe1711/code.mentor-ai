package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * A temporary, deterministic, local code analyzer used to validate the review-execution pipeline. It
 * runs simple pattern rules over already-sanitized (secret-masked) in-memory file content and emits
 * structured findings. <strong>It is not the final AI review engine</strong> and calls no external
 * provider.
 *
 * <p>Findings carry only a file path, line range, and generic per-rule text — <strong>never a source
 * snippet or a matched value</strong> — so nothing derived from source content is persisted.
 *
 * <p>ponytail: line-oriented substring/regex scan (empty-catch uses a single small regex). Good enough
 * to exercise the pipeline; replace wholesale when the real AI analyzer lands.
 */
@Component
public class LocalDeterministicReviewAnalyzer {

    /** A finding draft (no ids yet); the writer attaches job/org/repo before persisting. */
    public record Finding(
            String filePath,
            Integer lineStart,
            Integer lineEnd,
            ReviewFindingSeverity severity,
            String category,
            String title,
            String description,
            String suggestion,
            String ruleId,
            BigDecimal confidence) {
    }

    private static final Pattern EMPTY_CATCH =
            Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");
    // Assignment to a secret-like name whose value survived masking (i.e. was not replaced with the marker).
    private static final Pattern LEFTOVER_SECRET = Pattern.compile(
            "(?i)(?:password|passwd|api[_-]?key|apikey|access[_-]?token|accesstoken|secret[_-]?key)"
                    + "\\s*[:=]\\s*[\"']?([^\\s\"';]{1,})");
    private static final String REDACTED = "[REDACTED_SECRET]";

    public List<Finding> analyze(List<CodeAnalysisFile> files) {
        List<Finding> findings = new ArrayList<>();
        for (CodeAnalysisFile file : files) {
            analyzeFile(file, findings);
        }
        return findings;
    }

    private void analyzeFile(CodeAnalysisFile file, List<Finding> out) {
        String path = file.path();
        String ext = extensionOf(path);
        boolean java = ext.equals("java");
        boolean jsTs = ext.equals("js") || ext.equals("jsx") || ext.equals("ts") || ext.equals("tsx");

        String content = file.sanitizedContent();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;

            if (line.contains("TODO") || line.contains("FIXME")) {
                out.add(new Finding(path, lineNo, lineNo, ReviewFindingSeverity.LOW, "MAINTAINABILITY",
                        "TODO/FIXME comment",
                        "A TODO or FIXME marker was found on this line.",
                        "Resolve the outstanding work or track it in an issue, then remove the marker.",
                        "todo-fixme", BigDecimal.valueOf(0.95)));
            }
            if (jsTs && line.contains("console.log")) {
                out.add(new Finding(path, lineNo, lineNo, ReviewFindingSeverity.LOW, "DEBUG_CODE",
                        "console.log debug statement",
                        "A console.log debug statement was found on this line.",
                        "Remove the debug statement or use a proper logger.",
                        "console-log", BigDecimal.valueOf(0.90)));
            }
            if (java && line.contains("System.out.println")) {
                out.add(new Finding(path, lineNo, lineNo, ReviewFindingSeverity.LOW, "DEBUG_CODE",
                        "System.out.println debug statement",
                        "A System.out.println debug statement was found on this line.",
                        "Remove the debug statement or use a logging framework.",
                        "system-out-println", BigDecimal.valueOf(0.90)));
            }
            if (java && line.contains("printStackTrace")) {
                out.add(new Finding(path, lineNo, lineNo, ReviewFindingSeverity.MEDIUM, "ERROR_HANDLING",
                        "printStackTrace call",
                        "An exception is handled by printing its stack trace.",
                        "Log the exception through a logging framework or rethrow it appropriately.",
                        "print-stack-trace", BigDecimal.valueOf(0.90)));
            }
            addLeftoverSecret(path, line, lineNo, out);
        }

        addEmptyCatch(path, content, out);
    }

    private void addLeftoverSecret(String path, String line, int lineNo, List<Finding> out) {
        Matcher m = LEFTOVER_SECRET.matcher(line);
        while (m.find()) {
            // The value is already masked upstream; only a leftover (non-redacted) value is suspicious.
            if (!m.group(1).contains(REDACTED)) {
                out.add(new Finding(path, lineNo, lineNo, ReviewFindingSeverity.HIGH, "SECURITY",
                        "Hardcoded secret-like assignment",
                        "An assignment to a password/API-key/token-like name has a hardcoded value.",
                        "Move the value to a secret manager or environment variable; never commit secrets.",
                        "hardcoded-secret", BigDecimal.valueOf(0.60)));
                return; // one finding per line is enough
            }
        }
    }

    private void addEmptyCatch(String path, String content, List<Finding> out) {
        Matcher m = EMPTY_CATCH.matcher(content);
        while (m.find()) {
            int lineNo = lineNumberOf(content, m.start());
            out.add(new Finding(path, lineNo, lineNo, ReviewFindingSeverity.MEDIUM, "ERROR_HANDLING",
                    "Empty catch block",
                    "An exception is silently swallowed by an empty catch block.",
                    "Handle the exception, log it, or add a comment explaining why it is safe to ignore.",
                    "empty-catch", BigDecimal.valueOf(0.70)));
        }
    }

    private static int lineNumberOf(String content, int index) {
        int line = 1;
        for (int i = 0; i < index && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String extensionOf(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = path.substring(lastSlash + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
