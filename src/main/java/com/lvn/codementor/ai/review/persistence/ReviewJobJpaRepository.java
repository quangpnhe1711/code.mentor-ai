package com.lvn.codementor.ai.review.persistence;

import com.lvn.codementor.ai.review.domain.ReviewJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewJobJpaRepository extends JpaRepository<ReviewJob, UUID> {

    /** All review jobs for a repository, scoped to its owning organization (listing). */
    List<ReviewJob> findByOrganizationIdAndRepositoryIdOrderByCreatedAtDesc(
            UUID organizationId, UUID repositoryId);

    /** A review job scoped to its owning organization and repository (detail; enforces ownership). */
    Optional<ReviewJob> findByIdAndOrganizationIdAndRepositoryId(
            UUID id, UUID organizationId, UUID repositoryId);
}
