package com.lvn.codementor.ai.codeanalysis.application;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Replaces detected secrets in otherwise-allowed text with a fixed marker before the content is
 * hashed or measured. The <strong>original secret is never logged, stored, or returned</strong> —
 * only a count of replacements leaves this component (plus the sanitized text, which the builder
 * keeps in memory only).
 *
 * <p>Detected: GitHub tokens ({@code ghp_}/{@code gho_}/…, {@code github_pat_}), JWT-looking values,
 * AWS access key ids, PEM private-key blocks, and {@code key = value} / {@code KEY=VALUE} pairs for
 * suspicious key names (api key, access/secret token, password, private key, …). A file whose
 * content is <em>mostly</em> secret (a PEM key block, or a high redacted-character ratio) is flagged
 * as a pure secret file so the caller can skip it entirely rather than emit an almost-empty file.
 */
@Component
public class SecretMasker {

    public static final String REDACTED = "[REDACTED_SECRET]";

    /** Fraction of non-whitespace characters that, once redacted, marks a file as "pure secret". */
    private static final double PURE_SECRET_RATIO = 0.6;

    /** A masking rule: {@code group} is the capture group to redact (0 = whole match). */
    private record Rule(Pattern pattern, int group, boolean privateKeyBlock) {
    }

    private static final List<Rule> RULES = List.of(
            // PEM private-key blocks — redact the whole block; strong "pure secret" signal.
            new Rule(
                    Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----",
                            Pattern.DOTALL),
                    0, true),
            // key = value / key: value for suspicious key names (case-insensitive) — redact the value.
            new Rule(
                    Pattern.compile(
                            "(?i)(?:api[_-]?key|apikey|access[_-]?token|secret[_-]?access[_-]?key|secret[_-]?key"
                                    + "|client[_-]?secret|auth[_-]?token|access[_-]?key[_-]?id|access[_-]?key"
                                    + "|private[_-]?key|password|passwd|secret)\\s*[:=]\\s*[\"']?([^\\s\"',;]{4,})"),
                    1, false),
            // Uppercase ENV-style KEY=VALUE for suspicious key names — redact the value (never a placeholder).
            new Rule(
                    Pattern.compile(
                            "(?m)^\\s*[A-Z][A-Z0-9_]*"
                                    + "(?:SECRET|TOKEN|PASSWORD|PASSWD|APIKEY|API_KEY|PRIVATE_KEY|ACCESS_KEY|CREDENTIAL)"
                                    + "[A-Z0-9_]*\\s*=\\s*([^\\s\\[]\\S*)"),
                    1, false),
            // Standalone token formats (applied after key=value so values are counted once).
            new Rule(Pattern.compile("(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,}"), 0, false),
            new Rule(Pattern.compile("github_pat_[A-Za-z0-9_]{20,}"), 0, false),
            new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}"), 0, false),
            new Rule(Pattern.compile("AKIA[0-9A-Z]{16}"), 0, false));

    public MaskResult mask(String content) {
        String working = content;
        int count = 0;
        long maskedChars = 0;
        boolean privateKeyBlock = false;

        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(working);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String secret = matcher.group(rule.group());
                if (secret == null || secret.isBlank() || secret.equals(REDACTED)) {
                    matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
                    continue;
                }
                String whole = matcher.group();
                int start = matcher.start(rule.group()) - matcher.start();
                int end = matcher.end(rule.group()) - matcher.start();
                String replacement = whole.substring(0, start) + REDACTED + whole.substring(end);
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
                count++;
                maskedChars += secret.length();
                if (rule.privateKeyBlock()) {
                    privateKeyBlock = true;
                }
            }
            matcher.appendTail(out);
            working = out.toString();
        }

        boolean pureSecretFile = privateKeyBlock || isMostlySecret(content, maskedChars);
        return new MaskResult(working, count, pureSecretFile);
    }

    private static boolean isMostlySecret(String original, long maskedChars) {
        long nonWhitespace = original.chars().filter(c -> !Character.isWhitespace(c)).count();
        return nonWhitespace > 0 && (double) maskedChars / nonWhitespace >= PURE_SECRET_RATIO;
    }

    /**
     * @param sanitized       the content with every detected secret replaced by {@link #REDACTED}
     * @param maskedCount     number of secrets replaced
     * @param pureSecretFile  whether the file is essentially all secret and should be skipped entirely
     */
    public record MaskResult(String sanitized, int maskedCount, boolean pureSecretFile) {
    }
}
