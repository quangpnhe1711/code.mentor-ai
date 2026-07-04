package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.review.application.LocalDeterministicReviewAnalyzer.Finding;
import com.lvn.codementor.ai.review.domain.ReviewFinding;
import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.persistence.ReviewFindingJpaRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Persists analyzer findings for a review job. {@code organization_id} and {@code repository_id} are
 * copied from the job so every finding is scoped identically to its job. Besides structured metadata,
 * each finding stores the offending line as a bounded, secret-masked snippet (the secret rule redacts
 * the matched value); AI-provider findings carry no snippet.
 */
@Component
public class ReviewFindingWriter {

    private final ReviewFindingJpaRepository findings;

    public ReviewFindingWriter(ReviewFindingJpaRepository findings) {
        this.findings = findings;
    }

    /** Persist all findings for the job and return the number of rows written. */
    public int write(ReviewJob job, List<Finding> drafts) {
        List<ReviewFinding> rows = drafts.stream()
                .map(d -> new ReviewFinding(
                        job.getId(),
                        job.getOrganizationId(),
                        job.getRepositoryId(),
                        d.filePath(),
                        d.lineStart(),
                        d.lineEnd(),
                        d.severity(),
                        d.category(),
                        d.title(),
                        d.description(),
                        d.suggestion(),
                        d.ruleId(),
                        d.confidence(),
                        d.snippet()))
                .toList();
        findings.saveAll(rows);
        return rows.size();
    }
}
