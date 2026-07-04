package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.github.application.port.GitHubPullRequestCommentClient;
import com.lvn.codementor.ai.repository.application.GitProviderCredentialStore;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.review.domain.ReviewFinding;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.domain.ReviewType;
import com.lvn.codementor.ai.review.persistence.ReviewFindingJpaRepository;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Publishes completed pull-request review summaries back to the provider.
 *
 * <p>The comment body is built only from persisted structured finding metadata. Source lines, workspace
 * paths and credentials are never included.
 */
@Service
public class PullRequestReviewPublisher {

    private static final Logger log = LoggerFactory.getLogger(PullRequestReviewPublisher.class);

    private static final int MAX_FINDINGS_IN_COMMENT = 10;

    private final ImportedRepositoryJpaRepository repositories;
    private final ReviewFindingJpaRepository findings;
    private final GitProviderCredentialStore credentialStore;
    private final GitHubPullRequestCommentClient commentClient;

    public PullRequestReviewPublisher(
            ImportedRepositoryJpaRepository repositories,
            ReviewFindingJpaRepository findings,
            GitProviderCredentialStore credentialStore,
            GitHubPullRequestCommentClient commentClient) {
        this.repositories = repositories;
        this.findings = findings;
        this.credentialStore = credentialStore;
        this.commentClient = commentClient;
    }

    public ReviewPublicationResult publish(ReviewJob job) {
        if (job.getReviewType() != ReviewType.PULL_REQUEST || job.getTargetPullRequestNumber() == null) {
            return ReviewPublicationResult.skipped(null);
        }

        try {
            ImportedRepository repository = repositories
                    .findByIdAndOrganizationId(job.getRepositoryId(), job.getOrganizationId())
                    .orElseThrow();
            if (repository.getProvider() != RepositoryProvider.GITHUB) {
                return ReviewPublicationResult.skipped("Pull request review comment skipped for unsupported provider.");
            }

            List<ReviewFinding> reviewFindings = findings.findByReviewJobIdOrderByCreatedAtAsc(job.getId());
            String body = buildComment(job, reviewFindings);
            String token = credentialStore.decryptAccessToken(repository.getGitProviderConnectionId());
            commentClient.createIssueComment(
                    token,
                    repository.getOwnerLogin(),
                    repository.getName(),
                    job.getTargetPullRequestNumber(),
                    body);
            return ReviewPublicationResult.publishedComment();
        } catch (RuntimeException e) {
            log.warn("Pull request review comment publishing failed for review job {}", job.getId(), e);
            return ReviewPublicationResult.skipped("Pull request review comment publishing failed safely.");
        }
    }

    private static String buildComment(ReviewJob job, List<ReviewFinding> findings) {
        StringBuilder body = new StringBuilder();
        body.append("<!-- codementor-ai-review:").append(job.getId()).append(" -->\n");
        body.append("## CodeMentor AI review\n\n");
        body.append("Review job `").append(job.getId()).append("` completed with ")
                .append(findings.size()).append(" finding");
        if (findings.size() != 1) {
            body.append("s");
        }
        body.append(".\n\n");

        appendSeveritySummary(body, findings);
        if (findings.isEmpty()) {
            body.append("No issues found by the configured analyzers and rule sets.\n");
            return body.toString();
        }

        body.append("### Top findings\n");
        findings.stream()
                .sorted(Comparator
                        .comparing(PullRequestReviewPublisher::severityRank)
                        .thenComparing(ReviewFinding::getFilePath)
                        .thenComparing(f -> f.getLineStart() == null ? Integer.MAX_VALUE : f.getLineStart()))
                .limit(MAX_FINDINGS_IN_COMMENT)
                .forEach(f -> appendFinding(body, f));
        if (findings.size() > MAX_FINDINGS_IN_COMMENT) {
            body.append("\n_")
                    .append(findings.size() - MAX_FINDINGS_IN_COMMENT)
                    .append(" additional finding(s) omitted from this comment._\n");
        }
        return body.toString();
    }

    private static void appendSeveritySummary(StringBuilder body, List<ReviewFinding> findings) {
        Map<ReviewFindingSeverity, Long> counts = new EnumMap<>(ReviewFindingSeverity.class);
        for (ReviewFinding finding : findings) {
            counts.merge(finding.getSeverity(), 1L, Long::sum);
        }
        body.append("### Summary\n");
        for (ReviewFindingSeverity severity : ReviewFindingSeverity.values()) {
            body.append("- ").append(severity).append(": ")
                    .append(counts.getOrDefault(severity, 0L)).append("\n");
        }
        body.append("\n");
    }

    private static void appendFinding(StringBuilder body, ReviewFinding finding) {
        body.append("- **").append(finding.getSeverity()).append("** ");
        body.append(escapeMarkdown(finding.getTitle())).append(" - `")
                .append(escapeInlineCode(finding.getFilePath())).append("`");
        if (finding.getLineStart() != null) {
            body.append(":").append(finding.getLineStart());
        }
        body.append(" (").append(escapeMarkdown(finding.getCategory())).append(")");
        if (finding.getRuleId() != null && !finding.getRuleId().isBlank()) {
            body.append(" `").append(escapeInlineCode(finding.getRuleId())).append("`");
        }
        body.append("\n");
    }

    private static int severityRank(ReviewFinding finding) {
        return switch (finding.getSeverity()) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
            case INFO -> 4;
        };
    }

    private static String escapeMarkdown(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_");
    }

    private static String escapeInlineCode(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("`", "'");
    }
}
