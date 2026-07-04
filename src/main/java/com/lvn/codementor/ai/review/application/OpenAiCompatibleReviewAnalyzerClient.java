package com.lvn.codementor.ai.review.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.review.application.LocalDeterministicReviewAnalyzer.Finding;
import com.lvn.codementor.ai.review.config.ReviewAiProperties;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "codementor.review.ai.provider", havingValue = "OPENAI_COMPATIBLE")
public class OpenAiCompatibleReviewAnalyzerClient implements ReviewAnalyzerClient {

    private static final String SYSTEM_PROMPT = """
            You are CodeMentor AI, a senior software engineer doing a thorough code review of the
            provided sanitized source files. Report substantive findings, not just surface patterns.

            Look for real problems across these dimensions, wherever they apply:
            - Correctness: logic errors, off-by-one, null/undefined dereferences, unhandled edge cases,
              incorrect async/await or promise handling, race conditions, wrong operators/conditions.
            - Security: injection (SQL/command/XSS), unsafe input handling, auth/authorization gaps,
              unsafe deserialization, path traversal, SSRF, weak or misused crypto (go well beyond
              just spotting a hardcoded secret).
            - Error handling: swallowed exceptions, missing validation, silent failures, leaked internals.
            - Resources & performance: leaks, unbounded loops/allocations, N+1 queries, blocking calls
              on hot paths, missing pagination or limits.
            - API & design: broken invariants, inconsistent state, misuse of libraries/frameworks,
              and inconsistencies ACROSS the files provided (imports, call sites, shared state).
            - Maintainability: dead code, duplication, misleading names — but prefer higher-impact issues.

            Reason across the files together (follow imports, calls, and shared state); do not restrict
            yourself to single-line pattern matches. Prioritize genuine defects over style nits, and set
            severity honestly: CRITICAL/HIGH for real bugs or security issues, LOW/INFO for minor points.

            Return ONLY strict JSON — an object with a "findings" array, nothing else. Each finding:
            filePath, lineStart, lineEnd, severity, category, title,
            description (state the root cause and the impact), suggestion (a concrete fix),
            ruleId (a short kebab-case slug), confidence (0..1).
            severity must be one of INFO, LOW, MEDIUM, HIGH, CRITICAL.
            Do not include source snippets, secrets, stack traces, or any text outside the JSON.
            """;

    private final ReviewAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiCompatibleReviewAnalyzerClient(
            ReviewAiProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public ReviewAnalyzerResult analyze(List<CodeAnalysisFile> files) {
        if (properties.apiKey().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Review AI provider is not configured");
        }
        try {
            Map<String, Object> request = Map.of(
                    "model", properties.model(),
                    "temperature", 0,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userPrompt(files))));
            String response = restClient.post()
                    // base-url carries the version segment (OpenAI: /v1, Gemini: /v1beta/openai);
                    // only the resource is appended here.
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return new ReviewAnalyzerResult(
                    properties.provider(),
                    properties.model(),
                    properties.promptVersion(),
                    parseFindings(response));
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Review AI provider call failed", e);
        }
    }

    private String userPrompt(List<CodeAnalysisFile> files) {
        StringBuilder prompt = new StringBuilder(
                "Review these sanitized files together and report substantive, cross-file findings:\n");
        files.stream()
                .limit(properties.maxFiles())
                .forEach(file -> prompt
                        .append("\n--- FILE: ").append(file.path()).append(" ---\n")
                        .append(truncate(file.sanitizedContent(), properties.maxCharsPerFile()))
                        .append('\n'));
        return prompt.toString();
    }

    private List<Finding> parseFindings(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = stripJsonFences(
                    root.path("choices").path(0).path("message").path("content").asText());
            JsonNode contentRoot = objectMapper.readTree(content);
            JsonNode findings = contentRoot.has("findings") ? contentRoot.path("findings") : contentRoot;
            if (!findings.isArray()) {
                return List.of();
            }
            List<Finding> parsed = new ArrayList<>();
            for (JsonNode node : findings) {
                String filePath = requiredText(node, "filePath");
                String severity = requiredText(node, "severity").toUpperCase(Locale.ROOT);
                parsed.add(new Finding(
                        filePath,
                        nullableInt(node, "lineStart"),
                        nullableInt(node, "lineEnd"),
                        ReviewFindingSeverity.valueOf(severity),
                        requiredText(node, "category"),
                        requiredText(node, "title"),
                        requiredText(node, "description"),
                        nullableText(node, "suggestion"),
                        nullableText(node, "ruleId"),
                        BigDecimal.valueOf(node.path("confidence").asDouble(0.75)),
                        // The AI is instructed not to echo source snippets, so none is stored for AI findings.
                        null));
            }
            return parsed;
        } catch (Exception e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Review AI provider returned invalid findings", e);
        }
    }

    /** Some models wrap JSON in a ```json fenced block; strip it so parsing stays robust. */
    private static String stripJsonFences(String content) {
        String c = content.strip();
        if (c.startsWith("```")) {
            int firstNewline = c.indexOf('\n');
            if (firstNewline >= 0) {
                c = c.substring(firstNewline + 1);
            }
            int closingFence = c.lastIndexOf("```");
            if (closingFence >= 0) {
                c = c.substring(0, closingFence);
            }
        }
        return c.strip();
    }

    private static String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n[TRUNCATED]";
    }

    private static String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing field");
        }
        return value;
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }
}
