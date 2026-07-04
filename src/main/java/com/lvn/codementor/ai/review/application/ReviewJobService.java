package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInputStatus;
import com.lvn.codementor.ai.codeanalysis.persistence.CodeAnalysisInputJpaRepository;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.review.config.ReviewWorkerProperties;
import com.lvn.codementor.ai.review.application.command.CreateReviewJobCommand;
import com.lvn.codementor.ai.review.application.command.ReviewFindingFilter;
import com.lvn.codementor.ai.review.application.result.CreateReviewJobResult;
import com.lvn.codementor.ai.review.domain.ReviewFinding;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.domain.ReviewJobEvent;
import com.lvn.codementor.ai.review.domain.ReviewJobStatus;
import com.lvn.codementor.ai.review.domain.ReviewType;
import com.lvn.codementor.ai.review.persistence.ReviewFindingJpaRepository;
import com.lvn.codementor.ai.review.persistence.ReviewJobEventJpaRepository;
import com.lvn.codementor.ai.review.persistence.ReviewJobJpaRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Review-job persistence and read access (pre-AI phase). Creating a job only validates the ownership
 * chain (organization → repository → analysis input) and input readiness, then persists a
 * {@code QUEUED} job plus its first {@code QUEUED} event. <strong>No AI provider is called, no source
 * is read, and no findings are generated.</strong> The input hash is copied verbatim from the source
 * analysis input for traceability. Reads are scoped so no cross-organization/repository access leaks.
 */
@Service
public class ReviewJobService {

    private final OrganizationAccessService organizationAccess;
    private final ImportedRepositoryJpaRepository repositories;
    private final CodeAnalysisInputJpaRepository analysisInputs;
    private final ReviewJobJpaRepository reviewJobs;
    private final ReviewFindingJpaRepository reviewFindings;
    private final ReviewJobEventJpaRepository reviewJobEvents;
    private final ReviewWorkerProperties workerProperties;

    public ReviewJobService(
            OrganizationAccessService organizationAccess,
            ImportedRepositoryJpaRepository repositories,
            CodeAnalysisInputJpaRepository analysisInputs,
            ReviewJobJpaRepository reviewJobs,
            ReviewFindingJpaRepository reviewFindings,
            ReviewJobEventJpaRepository reviewJobEvents,
            ReviewWorkerProperties workerProperties) {
        this.organizationAccess = organizationAccess;
        this.repositories = repositories;
        this.analysisInputs = analysisInputs;
        this.reviewJobs = reviewJobs;
        this.reviewFindings = reviewFindings;
        this.reviewJobEvents = reviewJobEvents;
        this.workerProperties = workerProperties;
    }

    @Transactional
    public CreateReviewJobResult create(CreateReviewJobCommand command) {
        organizationAccess.requireMember(command.userId(), command.organizationId());
        ReviewType reviewType = resolveReviewType(command.reviewType());

        repositories
                .findByIdAndOrganizationId(command.repositoryId(), command.organizationId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));

        // Scoping the lookup to org + repo enforces the analysis-input-belongs-to-repo/org chain.
        CodeAnalysisInput input = analysisInputs
                .findByIdAndOrganizationIdAndRepositoryId(
                        command.analysisInputId(), command.organizationId(), command.repositoryId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis input not found"));

        if (input.getStatus() != CodeAnalysisInputStatus.READY) {
            throw new AppException(
                    ErrorCode.CODE_ANALYSIS_INPUT_NOT_READY, "Analysis input is not ready for review");
        }

        Integer targetPullRequestNumber = targetPullRequestNumber(reviewType, command);
        String targetRef = targetRef(reviewType, command);
        ensureNoDuplicatePullRequestReview(command.organizationId(), command.repositoryId(), reviewType, targetPullRequestNumber);

        ReviewJob job = new ReviewJob(
                input.getOrganizationId(),
                input.getRepositoryId(),
                input.getSnapshotId(),
                input.getId(),
                command.userId(),
                reviewType,
                targetPullRequestNumber,
                targetRef,
                input.getInputHash());
        job.configureRetry(workerProperties.maxAttempts());
        job = reviewJobs.save(job);
        reviewJobEvents.save(new ReviewJobEvent(job.getId(), job.getStatus(), "Review job queued"));
        return new CreateReviewJobResult(job);
    }

    /** Review jobs for a repository, scoped to the caller's organization. */
    public List<ReviewJob> list(UUID userId, UUID organizationId, UUID repositoryId) {
        organizationAccess.requireMember(userId, organizationId);
        return reviewJobs.findByOrganizationIdAndRepositoryIdOrderByCreatedAtDesc(organizationId, repositoryId);
    }

    /** A review job scoped to the caller's organization and repository. */
    public ReviewJob get(UUID userId, UUID organizationId, UUID repositoryId, UUID reviewJobId) {
        organizationAccess.requireMember(userId, organizationId);
        return reviewJobs
                .findByIdAndOrganizationIdAndRepositoryId(reviewJobId, organizationId, repositoryId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Review job not found"));
    }

    /** Findings for a review job (empty until the future worker phase). Verifies ownership first. */
    public List<ReviewFinding> listFindings(
            UUID userId, UUID organizationId, UUID repositoryId, UUID reviewJobId) {
        return listFindings(userId, organizationId, repositoryId, reviewJobId, new ReviewFindingFilter(null, null, null));
    }

    /** Findings for a review job filtered by severity/category/file. Verifies ownership first. */
    public List<ReviewFinding> listFindings(
            UUID userId,
            UUID organizationId,
            UUID repositoryId,
            UUID reviewJobId,
            ReviewFindingFilter filter) {
        ReviewJob job = get(userId, organizationId, repositoryId, reviewJobId);
        ReviewFindingSeverity severity = resolveSeverity(filter.severity());
        String category = normalizeCategory(filter.category());
        String filePath = normalizeBlank(filter.filePath());
        if (severity == null && category == null && filePath == null) {
            return reviewFindings.findByReviewJobIdOrderByCreatedAtAsc(job.getId());
        }
        return reviewFindings.findByReviewJobIdWithFilters(job.getId(), severity, category, filePath);
    }

    /** Status-transition events for a review job. Verifies ownership first. */
    public List<ReviewJobEvent> listEvents(
            UUID userId, UUID organizationId, UUID repositoryId, UUID reviewJobId) {
        ReviewJob job = get(userId, organizationId, repositoryId, reviewJobId);
        return reviewJobEvents.findByReviewJobIdOrderByCreatedAtAsc(job.getId());
    }

    private static ReviewType resolveReviewType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReviewType.FULL_REPOSITORY;
        }
        try {
            return ReviewType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(
                    ErrorCode.VALIDATION_FAILED,
                    "Unsupported reviewType; supported values are FULL_REPOSITORY, PULL_REQUEST, BRANCH");
        }
    }

    private static ReviewFindingSeverity resolveSeverity(String raw) {
        String normalized = normalizeBlank(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return ReviewFindingSeverity.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(
                    ErrorCode.VALIDATION_FAILED,
                    "Unsupported severity; supported values are INFO, LOW, MEDIUM, HIGH, CRITICAL");
        }
    }

    private static String normalizeCategory(String raw) {
        String normalized = normalizeBlank(raw);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeBlank(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private static Integer targetPullRequestNumber(ReviewType reviewType, CreateReviewJobCommand command) {
        if (reviewType == ReviewType.PULL_REQUEST) {
            Integer number = command.targetPullRequestNumber();
            if (number == null || number <= 0) {
                throw new AppException(
                        ErrorCode.VALIDATION_FAILED,
                        "targetPullRequestNumber is required for PULL_REQUEST reviews");
            }
            if (command.targetRef() != null && !command.targetRef().isBlank()) {
                throw new AppException(
                        ErrorCode.VALIDATION_FAILED,
                        "targetRef is not supported for PULL_REQUEST reviews");
            }
            return number;
        }
        if (command.targetPullRequestNumber() != null) {
            throw new AppException(
                    ErrorCode.VALIDATION_FAILED,
                    "targetPullRequestNumber is only supported for PULL_REQUEST reviews");
        }
        return null;
    }

    private static String targetRef(ReviewType reviewType, CreateReviewJobCommand command) {
        if (reviewType == ReviewType.BRANCH) {
            String ref = command.targetRef();
            if (ref == null || ref.isBlank()) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "targetRef is required for BRANCH reviews");
            }
            return ref.trim();
        }
        if (command.targetRef() != null && !command.targetRef().isBlank()) {
            throw new AppException(
                    ErrorCode.VALIDATION_FAILED, "targetRef is only supported for BRANCH reviews");
        }
        return null;
    }

    private void ensureNoDuplicatePullRequestReview(
            UUID organizationId, UUID repositoryId, ReviewType reviewType, Integer targetPullRequestNumber) {
        if (reviewType != ReviewType.PULL_REQUEST || targetPullRequestNumber == null) {
            return;
        }
        boolean existing = reviewJobs.existsByOrganizationIdAndRepositoryIdAndReviewTypeAndTargetPullRequestNumberAndStatusIn(
                organizationId,
                repositoryId,
                ReviewType.PULL_REQUEST,
                targetPullRequestNumber,
                List.of(ReviewJobStatus.QUEUED, ReviewJobStatus.RUNNING));
        if (existing) {
            throw new AppException(
                    ErrorCode.REVIEW_ALREADY_RUNNING,
                    "A review is already queued or running for this pull request");
        }
    }
}
