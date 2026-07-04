package com.lvn.codementor.ai.testgeneration.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.testgeneration.api.request.CreateTestGenerationJobRequest;
import com.lvn.codementor.ai.testgeneration.api.response.GeneratedTestResponse;
import com.lvn.codementor.ai.testgeneration.api.response.TestGenerationJobResponse;
import com.lvn.codementor.ai.testgeneration.api.response.TestGenerationResultResponse;
import com.lvn.codementor.ai.testgeneration.api.response.TestRunResponse;
import com.lvn.codementor.ai.testgeneration.application.TestGenerationService;
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

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}")
public class TestGenerationController {

    private final CurrentUser currentUser;
    private final TestGenerationService testGenerationService;

    public TestGenerationController(CurrentUser currentUser, TestGenerationService testGenerationService) {
        this.currentUser = currentUser;
        this.testGenerationService = testGenerationService;
    }

    @PostMapping("/analysis-inputs/{analysisInputId}/test-generation-jobs")
    public ResponseEntity<ApiResponse<TestGenerationResultResponse>> createJob(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID analysisInputId,
            @RequestBody CreateTestGenerationJobRequest request) {
        UUID userId = currentUser.requireUserId();
        CreateTestGenerationJobRequest safeRequest = request == null
                ? new CreateTestGenerationJobRequest(null, null, null)
                : request;
        TestGenerationResultResponse data = TestGenerationResultResponse.from(testGenerationService.generate(
                userId,
                organizationId,
                repositoryId,
                analysisInputId,
                safeRequest.targetType(),
                safeRequest.targetFilePath(),
                safeRequest.targetPullRequestNumber()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    @GetMapping("/test-generation-jobs")
    public ApiResponse<List<TestGenerationJobResponse>> listJobs(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId) {
        UUID userId = currentUser.requireUserId();
        List<TestGenerationJobResponse> data = testGenerationService.listJobs(userId, organizationId, repositoryId).stream()
                .map(TestGenerationJobResponse::from)
                .toList();
        return ApiResponse.ok(data, requestId());
    }

    @GetMapping("/test-generation-jobs/{jobId}/generated-tests")
    public ApiResponse<List<GeneratedTestResponse>> listGeneratedTests(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID jobId) {
        UUID userId = currentUser.requireUserId();
        List<GeneratedTestResponse> data =
                testGenerationService.listGeneratedTests(userId, organizationId, repositoryId, jobId).stream()
                        .map(GeneratedTestResponse::from)
                        .toList();
        return ApiResponse.ok(data, requestId());
    }

    @PostMapping("/generated-tests/{generatedTestId}/runs")
    public ResponseEntity<ApiResponse<TestRunResponse>> runGeneratedTest(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID generatedTestId) {
        UUID userId = currentUser.requireUserId();
        TestRunResponse data = TestRunResponse.from(
                testGenerationService.runGeneratedTest(userId, organizationId, repositoryId, generatedTestId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    @GetMapping("/generated-tests/{generatedTestId}/runs")
    public ApiResponse<List<TestRunResponse>> listRuns(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID generatedTestId) {
        UUID userId = currentUser.requireUserId();
        List<TestRunResponse> data = testGenerationService.listRuns(userId, organizationId, repositoryId, generatedTestId)
                .stream()
                .map(TestRunResponse::from)
                .toList();
        return ApiResponse.ok(data, requestId());
    }

    @GetMapping("/test-runs/{runId}")
    public ApiResponse<TestRunResponse> getRun(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID runId) {
        UUID userId = currentUser.requireUserId();
        return ApiResponse.ok(
                TestRunResponse.from(testGenerationService.getRun(userId, organizationId, repositoryId, runId)),
                requestId());
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
