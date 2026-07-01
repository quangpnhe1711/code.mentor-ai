package com.lvn.codementor.ai.review.persistence;

import com.lvn.codementor.ai.review.domain.ReviewJobEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewJobEventJpaRepository extends JpaRepository<ReviewJobEvent, UUID> {

    /** Status-transition history for a review job, oldest first. */
    List<ReviewJobEvent> findByReviewJobIdOrderByCreatedAtAsc(UUID reviewJobId);
}
