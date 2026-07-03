package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.codeanalysis.application.ReviewInputMaterializer;
import com.lvn.codementor.ai.codeanalysis.application.result.MaterializedReviewInput;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.review.application.LocalDeterministicReviewAnalyzer.Finding;
import com.lvn.codementor.ai.review.application.result.ReviewExecutionResult;
import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.domain.ReviewJobEvent;
import com.lvn.codementor.ai.review.persistence.ReviewJobEventJpaRepository;
import com.lvn.codementor.ai.review.persistence.ReviewJobJpaRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronously executes a QUEUED review job with a local deterministic analyzer (pre-AI phase; no
 * external provider, no async worker). Flow: validate ownership + runnable state, transition
 * {@code QUEUED → RUNNING}, re-materialize the sanitized input from the snapshot <em>in memory</em>,
 * analyze, persist findings, then {@code → COMPLETED}. Any failure after RUNNING is caught and the job
 * is marked {@code FAILED} with a <strong>safe</strong> reason — no path, source content, secret,
 * provider error, or stack trace ever reaches the client or an event message.
 *
 * <p>ponytail: the runner is folded into this service — synchronous execution needs no separate
 * ReviewJobRunner indirection. Split it out when async/queued execution lands.
 */
@Service
public class ReviewJobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ReviewJobExecutionService.class);

    private static final String SAFE_FAILURE_REASON = "Review job failed safely.";

    private final OrganizationAccessService organizationAccess;
    private final ImportedRepositoryJpaRepository repositories;
    private final ReviewJobJpaRepository reviewJobs;
    private final ReviewJobEventJpaRepository reviewJobEvents;
    private final ReviewInputMaterializer materializer;
    private final LocalDeterministicReviewAnalyzer analyzer;
    private final ReviewFindingWriter findingWriter;

    public ReviewJobExecutionService(
            OrganizationAccessService organizationAccess,
            ImportedRepositoryJpaRepository repositories,
            ReviewJobJpaRepository reviewJobs,
            ReviewJobEventJpaRepository reviewJobEvents,
            ReviewInputMaterializer materializer,
            LocalDeterministicReviewAnalyzer analyzer,
            ReviewFindingWriter findingWriter) {
        this.organizationAccess = organizationAccess;
        this.repositories = repositories;
        this.reviewJobs = reviewJobs;
        this.reviewJobEvents = reviewJobEvents;
        this.materializer = materializer;
        this.analyzer = analyzer;
        this.findingWriter = findingWriter;
    }

    @Transactional
    public ReviewExecutionResult run(UUID userId, UUID organizationId, UUID repositoryId, UUID reviewJobId) {
        organizationAccess.requireMember(userId, organizationId);

        repositories
                .findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));

        ReviewJob job = reviewJobs
                .findByIdAndOrganizationIdAndRepositoryId(reviewJobId, organizationId, repositoryId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Review job not found"));

        if (!job.isRunnable()) {
            throw new AppException(ErrorCode.REVIEW_JOB_NOT_RUNNABLE, "Review job is not in a runnable state");
        }

        job.markRunning();
        reviewJobs.save(job);
        recordEvent(job, "Review job started.");

        try {
            MaterializedReviewInput input = materializer.materialize(job.getSnapshotId());
            List<Finding> findings = analyzer.analyze(input.files());
            int count = findingWriter.write(job, findings);
            job.markCompleted(count);
            reviewJobs.save(job);
            recordEvent(job, "Review job completed.");
        } catch (RuntimeException e) {
            // Internal detail is logged only; the client and event message get a safe, generic reason.
            log.warn("Review job {} failed during execution", job.getId(), e);
            job.markFailed(SAFE_FAILURE_REASON);
            reviewJobs.save(job);
            recordEvent(job, SAFE_FAILURE_REASON);
        }
        return new ReviewExecutionResult(job);
    }

    private void recordEvent(ReviewJob job, String message) {
        reviewJobEvents.save(new ReviewJobEvent(job.getId(), job.getStatus(), message));
    }
}
