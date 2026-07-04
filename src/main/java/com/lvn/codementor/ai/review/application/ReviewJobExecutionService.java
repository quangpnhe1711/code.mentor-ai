package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.codeanalysis.application.ReviewInputMaterializer;
import com.lvn.codementor.ai.codeanalysis.application.result.MaterializedReviewInput;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.review.config.ReviewWorkerProperties;
import com.lvn.codementor.ai.review.application.result.ReviewExecutionResult;
import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.domain.ReviewJobEvent;
import com.lvn.codementor.ai.review.persistence.ReviewJobEventJpaRepository;
import com.lvn.codementor.ai.review.persistence.ReviewJobJpaRepository;
import com.lvn.codementor.ai.ruleengine.application.LocalRuleEngineEvaluator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes a QUEUED review job with a local deterministic analyzer (pre-AI phase; no external
 * provider). Flow: validate ownership + runnable state for user-triggered runs, transition
 * {@code QUEUED → RUNNING}, re-materialize the sanitized input from the snapshot <em>in memory</em>,
 * analyze, persist findings, then {@code → COMPLETED}. Any failure after RUNNING is caught and the job
 * is marked {@code FAILED} with a <strong>safe</strong> reason — no path, source content, secret,
 * provider error, or stack trace ever reaches the client or an event message.
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
    private final ReviewAnalyzerClient analyzerClient;
    private final LocalRuleEngineEvaluator ruleEngineEvaluator;
    private final ReviewFindingWriter findingWriter;
    private final ReviewWorkerProperties workerProperties;
    private final PullRequestReviewPublisher reviewPublisher;

    public ReviewJobExecutionService(
            OrganizationAccessService organizationAccess,
            ImportedRepositoryJpaRepository repositories,
            ReviewJobJpaRepository reviewJobs,
            ReviewJobEventJpaRepository reviewJobEvents,
            ReviewInputMaterializer materializer,
            ReviewAnalyzerClient analyzerClient,
            LocalRuleEngineEvaluator ruleEngineEvaluator,
            ReviewFindingWriter findingWriter,
            ReviewWorkerProperties workerProperties,
            PullRequestReviewPublisher reviewPublisher) {
        this.organizationAccess = organizationAccess;
        this.repositories = repositories;
        this.reviewJobs = reviewJobs;
        this.reviewJobEvents = reviewJobEvents;
        this.materializer = materializer;
        this.analyzerClient = analyzerClient;
        this.ruleEngineEvaluator = ruleEngineEvaluator;
        this.findingWriter = findingWriter;
        this.workerProperties = workerProperties;
        this.reviewPublisher = reviewPublisher;
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

        return execute(job, false);
    }

    @Transactional
    public ReviewExecutionResult runSystem(UUID reviewJobId) {
        ReviewJob job = reviewJobs
                .findById(reviewJobId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Review job not found"));

        if (!job.isRunnable()) {
            throw new AppException(ErrorCode.REVIEW_JOB_NOT_RUNNABLE, "Review job is not in a runnable state");
        }

        return execute(job, true);
    }

    private ReviewExecutionResult execute(ReviewJob job, boolean retryable) {
        job.markRunning();
        reviewJobs.save(job);
        recordEvent(job, "Review job started.");

        try {
            MaterializedReviewInput input = materializer.materialize(job.getSnapshotId());
            ReviewAnalyzerResult analyzerResult = analyzerClient.analyze(input.files());
            job.markAnalyzer(
                    analyzerResult.aiProvider(), analyzerResult.aiModel(), analyzerResult.promptVersion());
            List<LocalDeterministicReviewAnalyzer.Finding> findings = new ArrayList<>(analyzerResult.findings());
            findings.addAll(ruleEngineEvaluator.evaluate(job.getOrganizationId(), job.getRepositoryId(), input.files()));
            int count = findingWriter.write(job, findings);
            job.markCompleted(count);
            reviewJobs.save(job);
            recordEvent(job, "Review job completed.");
            ReviewPublicationResult publication = reviewPublisher.publish(job);
            if (publication.eventMessage() != null) {
                recordEvent(job, publication.eventMessage());
            }
        } catch (RuntimeException e) {
            // Internal detail is logged only; the client and event message get a safe, generic reason.
            log.warn("Review job {} failed during execution", job.getId(), e);
            if (retryable && job.canRetry()) {
                job.markRetryQueued(SAFE_FAILURE_REASON, Instant.now().plusMillis(workerProperties.retryBackoffMs()));
                reviewJobs.save(job);
                recordEvent(job, "Review job retry scheduled.");
            } else {
                job.markFailed(SAFE_FAILURE_REASON);
                reviewJobs.save(job);
                recordEvent(job, SAFE_FAILURE_REASON);
            }
        }
        return new ReviewExecutionResult(job);
    }

    private void recordEvent(ReviewJob job, String message) {
        reviewJobEvents.save(new ReviewJobEvent(job.getId(), job.getStatus(), message));
    }
}
