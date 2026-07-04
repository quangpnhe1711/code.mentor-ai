package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.review.config.ReviewWorkerProperties;
import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.domain.ReviewJobStatus;
import com.lvn.codementor.ai.review.persistence.ReviewJobJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ReviewJobWorkerService {

    private static final Logger log = LoggerFactory.getLogger(ReviewJobWorkerService.class);

    private final ReviewJobJpaRepository reviewJobs;
    private final ReviewJobExecutionService executionService;
    private final ReviewWorkerProperties properties;

    public ReviewJobWorkerService(
            ReviewJobJpaRepository reviewJobs,
            ReviewJobExecutionService executionService,
            ReviewWorkerProperties properties) {
        this.reviewJobs = reviewJobs;
        this.executionService = executionService;
        this.properties = properties;
    }

    public int processNextBatch() {
        int processed = 0;
        for (int i = 0; i < properties.batchSize(); i++) {
            Optional<ReviewJob> next = nextQueuedJob();
            if (next.isEmpty()) {
                return processed;
            }
            try {
                executionService.runSystem(next.get().getId());
                processed++;
            } catch (AppException e) {
                if (e.code() == ErrorCode.REVIEW_JOB_NOT_RUNNABLE) {
                    log.debug("Skipping review job {} because it is no longer runnable", next.get().getId());
                    continue;
                }
                throw e;
            }
        }
        return processed;
    }

    private Optional<ReviewJob> nextQueuedJob() {
        List<ReviewJob> jobs = reviewJobs.findRunnableQueuedJobs(
                ReviewJobStatus.QUEUED, Instant.now(), PageRequest.of(0, 1));
        return jobs.stream().findFirst();
    }
}
