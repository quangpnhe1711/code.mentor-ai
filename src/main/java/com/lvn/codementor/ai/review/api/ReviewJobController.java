package com.lvn.codementor.ai.review.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.review.api.request.CreateReviewJobRequest;
import com.lvn.codementor.ai.review.api.response.ReviewFindingResponse;
import com.lvn.codementor.ai.review.api.response.ReviewJobEventResponse;
import com.lvn.codementor.ai.review.api.response.ReviewJobResponse;
import com.lvn.codementor.ai.review.application.ReviewJobExecutionService;
import com.lvn.codementor.ai.review.application.ReviewJobService;
import com.lvn.codementor.ai.review.application.command.CreateReviewJobCommand;
import com.lvn.codementor.ai.review.application.result.CreateReviewJobResult;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Review-job creation and inspection under an organization's repository (pre-AI phase; no provider is
 * called). Requires a platform JWT (SecurityConfig) and organization membership (enforced in the
 * service). All access is scoped by the ownership chain organization → repository → analysis input →
 * review job; responses are metadata-only (no source content, workspace path, or credential data).
 */
@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}")
public class ReviewJobController {

    private final CurrentUser currentUser;
    private final ReviewJobService reviewJobService;
    private final ReviewJobExecutionService executionService;

    public ReviewJobController(
            CurrentUser currentUser,
            ReviewJobService reviewJobService,
            ReviewJobExecutionService executionService) {
        this.currentUser = currentUser;
        this.reviewJobService = reviewJobService;
        this.executionService = executionService;
    }

    @PostMapping("/analysis-inputs/{analysisInputId}/review-jobs")
    public ResponseEntity<ApiResponse<ReviewJobResponse>> createReviewJob(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID analysisInputId,
            @RequestBody(required = false) CreateReviewJobRequest request) {
        UUID userId = currentUser.requireUserId();
        String reviewType = request == null ? null : request.reviewType();
        CreateReviewJobResult result = reviewJobService.create(
                new CreateReviewJobCommand(userId, organizationId, repositoryId, analysisInputId, reviewType));
        ApiResponse<ReviewJobResponse> body =
                ApiResponse.ok(ReviewJobResponse.from(result.job()), requestId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/review-jobs/{reviewJobId}/run")
    public ApiResponse<ReviewJobResponse> runReviewJob(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID reviewJobId) {
        UUID userId = currentUser.requireUserId();
        ReviewJobResponse data = ReviewJobResponse.from(
                executionService.run(userId, organizationId, repositoryId, reviewJobId).job());
        return ApiResponse.ok(data, requestId());
    }

    @GetMapping("/review-jobs")
    public ApiResponse<List<ReviewJobResponse>> listReviewJobs(
            @PathVariable UUID organizationId, @PathVariable UUID repositoryId) {
        UUID userId = currentUser.requireUserId();
        List<ReviewJobResponse> data = reviewJobService.list(userId, organizationId, repositoryId).stream()
                .map(ReviewJobResponse::from)
                .toList();
        return ApiResponse.ok(data, requestId());
    }

    @GetMapping("/review-jobs/{reviewJobId}")
    public ApiResponse<ReviewJobResponse> getReviewJob(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID reviewJobId) {
        UUID userId = currentUser.requireUserId();
        ReviewJobResponse data =
                ReviewJobResponse.from(reviewJobService.get(userId, organizationId, repositoryId, reviewJobId));
        return ApiResponse.ok(data, requestId());
    }

    @GetMapping("/review-jobs/{reviewJobId}/findings")
    public ApiResponse<List<ReviewFindingResponse>> listFindings(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID reviewJobId) {
        UUID userId = currentUser.requireUserId();
        List<ReviewFindingResponse> data =
                reviewJobService.listFindings(userId, organizationId, repositoryId, reviewJobId).stream()
                        .map(ReviewFindingResponse::from)
                        .toList();
        return ApiResponse.ok(data, requestId());
    }

    @GetMapping("/review-jobs/{reviewJobId}/events")
    public ApiResponse<List<ReviewJobEventResponse>> listEvents(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID reviewJobId) {
        UUID userId = currentUser.requireUserId();
        List<ReviewJobEventResponse> data =
                reviewJobService.listEvents(userId, organizationId, repositoryId, reviewJobId).stream()
                        .map(ReviewJobEventResponse::from)
                        .toList();
        return ApiResponse.ok(data, requestId());
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
