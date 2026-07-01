package com.lvn.codementor.ai.review.persistence;

import com.lvn.codementor.ai.review.domain.ReviewFinding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewFindingJpaRepository extends JpaRepository<ReviewFinding, UUID> {

    /** All findings for a review job (empty until the future worker phase generates them). */
    List<ReviewFinding> findByReviewJobIdOrderByCreatedAtAsc(UUID reviewJobId);
}
