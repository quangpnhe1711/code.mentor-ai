package com.lvn.codementor.ai.review.persistence;

import com.lvn.codementor.ai.review.domain.ReviewFinding;
import com.lvn.codementor.ai.review.domain.ReviewFindingSeverity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewFindingJpaRepository extends JpaRepository<ReviewFinding, UUID> {

    /** All findings for a review job (empty until the future worker phase generates them). */
    List<ReviewFinding> findByReviewJobIdOrderByCreatedAtAsc(UUID reviewJobId);

    @Query("""
            select f
            from ReviewFinding f
            where f.reviewJobId = :reviewJobId
              and (:severity is null or f.severity = :severity)
              and (:category is null or upper(f.category) = :category)
              and (:filePath is null or f.filePath = :filePath)
            order by f.createdAt asc
            """)
    List<ReviewFinding> findByReviewJobIdWithFilters(
            @Param("reviewJobId") UUID reviewJobId,
            @Param("severity") ReviewFindingSeverity severity,
            @Param("category") String category,
            @Param("filePath") String filePath);
}
