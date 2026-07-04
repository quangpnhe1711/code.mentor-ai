package com.lvn.codementor.ai.review.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codementor.review.worker.enabled", havingValue = "true")
public class ReviewJobWorkerScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewJobWorkerScheduler.class);

    private final ReviewJobWorkerService workerService;

    public ReviewJobWorkerScheduler(ReviewJobWorkerService workerService) {
        this.workerService = workerService;
    }

    @Scheduled(fixedDelayString = "${codementor.review.worker.fixed-delay-ms:5000}")
    public void processQueuedReviewJobs() {
        int processed = workerService.processNextBatch();
        if (processed > 0) {
            log.info("Processed {} queued review job(s)", processed);
        }
    }
}
