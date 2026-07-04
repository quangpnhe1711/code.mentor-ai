package com.lvn.codementor.ai.review.persistence;

import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.domain.ReviewJobStatus;
import com.lvn.codementor.ai.review.domain.ReviewType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewJobJpaRepository extends JpaRepository<ReviewJob, UUID> {

    @Query("""
            select job
            from ReviewJob job
            where job.status = :status
              and (job.nextRunAt is null or job.nextRunAt <= :now)
            order by job.createdAt asc
            """)
    List<ReviewJob> findRunnableQueuedJobs(
            @Param("status") ReviewJobStatus status, @Param("now") Instant now, Pageable pageable);

    /** All review jobs for a repository, scoped to its owning organization (listing). */
    List<ReviewJob> findByOrganizationIdAndRepositoryIdOrderByCreatedAtDesc(
            UUID organizationId, UUID repositoryId);

    /** A review job scoped to its owning organization and repository (detail; enforces ownership). */
    Optional<ReviewJob> findByIdAndOrganizationIdAndRepositoryId(
            UUID id, UUID organizationId, UUID repositoryId);

    boolean existsByOrganizationIdAndRepositoryIdAndReviewTypeAndTargetPullRequestNumberAndStatusIn(
            UUID organizationId,
            UUID repositoryId,
            ReviewType reviewType,
            Integer targetPullRequestNumber,
            List<ReviewJobStatus> statuses);
}
