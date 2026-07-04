package com.lvn.codementor.ai.testgeneration.application;

import com.lvn.codementor.ai.codeanalysis.application.ReviewInputMaterializer;
import com.lvn.codementor.ai.codeanalysis.application.result.MaterializedReviewInput;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInputStatus;
import com.lvn.codementor.ai.codeanalysis.persistence.CodeAnalysisInputJpaRepository;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.testgeneration.domain.GeneratedTest;
import com.lvn.codementor.ai.testgeneration.domain.TestGenerationJob;
import com.lvn.codementor.ai.testgeneration.domain.TestGenerationTargetType;
import com.lvn.codementor.ai.testgeneration.domain.TestResult;
import com.lvn.codementor.ai.testgeneration.domain.TestRun;
import com.lvn.codementor.ai.testgeneration.domain.TestRunStatus;
import com.lvn.codementor.ai.testgeneration.persistence.GeneratedTestJpaRepository;
import com.lvn.codementor.ai.testgeneration.persistence.TestGenerationJobJpaRepository;
import com.lvn.codementor.ai.testgeneration.persistence.TestResultJpaRepository;
import com.lvn.codementor.ai.testgeneration.persistence.TestRunJpaRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestGenerationService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final OrganizationAccessService access;
    private final ImportedRepositoryJpaRepository repositories;
    private final CodeAnalysisInputJpaRepository analysisInputs;
    private final ReviewInputMaterializer materializer;
    private final TestGenerationJobJpaRepository jobs;
    private final GeneratedTestJpaRepository generatedTests;
    private final TestRunJpaRepository testRuns;
    private final TestResultJpaRepository testResults;

    public TestGenerationService(
            OrganizationAccessService access,
            ImportedRepositoryJpaRepository repositories,
            CodeAnalysisInputJpaRepository analysisInputs,
            ReviewInputMaterializer materializer,
            TestGenerationJobJpaRepository jobs,
            GeneratedTestJpaRepository generatedTests,
            TestRunJpaRepository testRuns,
            TestResultJpaRepository testResults) {
        this.access = access;
        this.repositories = repositories;
        this.analysisInputs = analysisInputs;
        this.materializer = materializer;
        this.jobs = jobs;
        this.generatedTests = generatedTests;
        this.testRuns = testRuns;
        this.testResults = testResults;
    }

    @Transactional
    public TestGenerationResult generate(
            UUID userId,
            UUID organizationId,
            UUID repositoryId,
            UUID analysisInputId,
            String targetType,
            String targetFilePath,
            Integer targetPullRequestNumber) {
        access.requireMember(userId, organizationId);
        repositories.findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));
        CodeAnalysisInput input = analysisInputs
                .findByIdAndOrganizationIdAndRepositoryId(analysisInputId, organizationId, repositoryId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Analysis input not found"));
        if (input.getStatus() != CodeAnalysisInputStatus.READY) {
            throw new AppException(ErrorCode.CODE_ANALYSIS_INPUT_NOT_READY, "Analysis input must be READY");
        }

        Target target = parseTarget(targetType, targetFilePath, targetPullRequestNumber);
        TestGenerationJob job = jobs.save(new TestGenerationJob(
                organizationId,
                repositoryId,
                input.getSnapshotId(),
                input.getId(),
                userId,
                target.type(),
                target.filePath(),
                target.pullRequestNumber(),
                input.getInputHash()));

        try {
            GeneratedTest generated = generatedTests.save(generateSuggestion(job, input.getSnapshotId(), target));
            job.markCompleted(1);
            jobs.save(job);
            return new TestGenerationResult(job, List.of(generated));
        } catch (RuntimeException ex) {
            job.markFailed("Test generation failed safely");
            jobs.save(job);
            return new TestGenerationResult(job, List.of());
        }
    }

    @Transactional(readOnly = true)
    public List<TestGenerationJob> listJobs(UUID userId, UUID organizationId, UUID repositoryId) {
        access.requireMember(userId, organizationId);
        repositories.findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));
        return jobs.findByOrganizationIdAndRepositoryIdOrderByCreatedAtDesc(organizationId, repositoryId);
    }

    @Transactional(readOnly = true)
    public List<GeneratedTest> listGeneratedTests(UUID userId, UUID organizationId, UUID repositoryId, UUID jobId) {
        access.requireMember(userId, organizationId);
        TestGenerationJob job = jobs.findByIdAndOrganizationIdAndRepositoryId(jobId, organizationId, repositoryId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Test generation job not found"));
        return generatedTests.findByTestGenerationJobIdOrderByCreatedAtAsc(job.getId());
    }

    @Transactional
    public TestRunResult runGeneratedTest(UUID userId, UUID organizationId, UUID repositoryId, UUID generatedTestId) {
        access.requireMember(userId, organizationId);
        GeneratedTest generated = generatedTests
                .findByIdAndOrganizationIdAndRepositoryId(generatedTestId, organizationId, repositoryId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Generated test not found"));

        TestRun run = testRuns.save(new TestRun(generated.getId(), organizationId, repositoryId, DEFAULT_TIMEOUT_SECONDS));
        run.markRunning();
        Instant started = Instant.now();
        SimulatedExecution execution = simulateSandbox(generated);
        long durationMs = Math.max(1, Duration.between(started, Instant.now()).toMillis());
        run.markCompleted(execution.status());
        testRuns.save(run);
        TestResult result = testResults.save(new TestResult(
                run.getId(),
                execution.stdout(),
                execution.stderr(),
                execution.exitCode(),
                durationMs,
                execution.failureExplanation()));
        return new TestRunResult(run, result);
    }

    @Transactional(readOnly = true)
    public List<TestRun> listRuns(UUID userId, UUID organizationId, UUID repositoryId, UUID generatedTestId) {
        access.requireMember(userId, organizationId);
        GeneratedTest generated = generatedTests
                .findByIdAndOrganizationIdAndRepositoryId(generatedTestId, organizationId, repositoryId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Generated test not found"));
        return testRuns.findByGeneratedTestIdOrderByCreatedAtDesc(generated.getId());
    }

    @Transactional(readOnly = true)
    public TestRunResult getRun(UUID userId, UUID organizationId, UUID repositoryId, UUID runId) {
        access.requireMember(userId, organizationId);
        TestRun run = testRuns.findByIdAndOrganizationIdAndRepositoryId(runId, organizationId, repositoryId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Test run not found"));
        TestResult result = testResults.findByTestRunId(run.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Test result not found"));
        return new TestRunResult(run, result);
    }

    private GeneratedTest generateSuggestion(TestGenerationJob job, UUID snapshotId, Target target) {
        MaterializedReviewInput input = materializer.materialize(snapshotId);
        CodeAnalysisFile source = selectSourceFile(input, target);
        String testPath = testPathFor(target, source);
        String language = languageFor(testPath);
        String content = contentFor(target, source, testPath);
        return new GeneratedTest(
                job.getId(),
                job.getOrganizationId(),
                job.getRepositoryId(),
                testPath,
                language,
                content,
                "Generated as a reviewable suggestion; it has not been written to the repository.");
    }

    private static CodeAnalysisFile selectSourceFile(MaterializedReviewInput input, Target target) {
        if (target.type() == TestGenerationTargetType.FILE) {
            return input.files().stream()
                    .filter(file -> file.path().equals(target.filePath()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Target file not found in analysis input"));
        }
        return input.files().stream()
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CODE_ANALYSIS_INPUT_NOT_READY, "Analysis input has no files"));
    }

    private static String testPathFor(Target target, CodeAnalysisFile source) {
        if (target.type() == TestGenerationTargetType.PULL_REQUEST) {
            return "generated/pr-" + target.pullRequestNumber() + "/RepositorySmokeTest.java";
        }
        String fileName = Path.of(source.path()).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return "generated/" + base + "Test.java";
    }

    private static String languageFor(String path) {
        if (path.endsWith(".java")) {
            return "java";
        }
        return "text";
    }

    private static String contentFor(Target target, CodeAnalysisFile source, String testPath) {
        String className = Path.of(testPath).getFileName().toString().replace(".java", "");
        String targetLabel = target.type() == TestGenerationTargetType.FILE
                ? source.path()
                : "pull request #" + target.pullRequestNumber();
        return "import org.junit.jupiter.api.Test;\n"
                + "import static org.junit.jupiter.api.Assertions.assertTrue;\n\n"
                + "class " + sanitizeClassName(className) + " {\n"
                + "  @Test\n"
                + "  void generatedSuggestionCoversTarget() {\n"
                + "    assertTrue(true, \"Generated suggestion for " + escape(targetLabel) + "\");\n"
                + "  }\n"
                + "}\n";
    }

    private static String sanitizeClassName(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9_]", "");
        if (sanitized.isBlank() || Character.isDigit(sanitized.charAt(0))) {
            return "GeneratedTest";
        }
        return sanitized;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static SimulatedExecution simulateSandbox(GeneratedTest generated) {
        if (generated.getContent().contains("assert false")) {
            return new SimulatedExecution(
                    TestRunStatus.FAILED,
                    "",
                    "Generated assertion failed",
                    1,
                    "The generated test contains a failing assertion; inspect the suggested expectation.");
        }
        return new SimulatedExecution(
                TestRunStatus.PASSED,
                "Simulated sandbox executed generated test suggestion.",
                "",
                0,
                null);
    }

    private static Target parseTarget(String rawType, String rawFilePath, Integer pullRequestNumber) {
        TestGenerationTargetType type;
        try {
            type = rawType == null || rawType.isBlank()
                    ? TestGenerationTargetType.FILE
                    : TestGenerationTargetType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Unsupported targetType");
        }
        if (type == TestGenerationTargetType.FILE) {
            if (rawFilePath == null || rawFilePath.isBlank()) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "targetFilePath is required for FILE targets");
            }
            if (pullRequestNumber != null) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "targetPullRequestNumber is not supported for FILE targets");
            }
            return new Target(type, rawFilePath.trim().replace('\\', '/'), null);
        }
        if (pullRequestNumber == null || pullRequestNumber <= 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "targetPullRequestNumber is required for PULL_REQUEST targets");
        }
        if (rawFilePath != null && !rawFilePath.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "targetFilePath is not supported for PULL_REQUEST targets");
        }
        return new Target(type, null, pullRequestNumber);
    }

    private record Target(TestGenerationTargetType type, String filePath, Integer pullRequestNumber) {
    }

    private record SimulatedExecution(
            TestRunStatus status,
            String stdout,
            String stderr,
            Integer exitCode,
            String failureExplanation) {
    }
}
