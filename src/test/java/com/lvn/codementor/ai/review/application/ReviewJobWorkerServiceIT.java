package com.lvn.codementor.ai.review.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.persistence.CodeAnalysisInputJpaRepository;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryFileEntry;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositoryFileEntryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositorySnapshotJpaRepository;
import com.lvn.codementor.ai.review.application.command.CreateReviewJobCommand;
import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.domain.ReviewJobStatus;
import com.lvn.codementor.ai.review.persistence.ReviewFindingJpaRepository;
import com.lvn.codementor.ai.review.persistence.ReviewJobJpaRepository;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewJobWorkerServiceIT extends AbstractWebIT {

    @Autowired
    GitProviderConnectionJpaRepository connections;

    @Autowired
    ImportedRepositoryJpaRepository repositories;

    @Autowired
    RepositorySnapshotJpaRepository snapshots;

    @Autowired
    RepositoryFileEntryJpaRepository fileEntries;

    @Autowired
    CodeAnalysisInputJpaRepository analysisInputs;

    @Autowired
    ReviewJobService reviewJobService;

    @Autowired
    ReviewJobWorkerService workerService;

    @Autowired
    ReviewJobExecutionService executionService;

    @Autowired
    ReviewJobJpaRepository reviewJobs;

    @Autowired
    ReviewFindingJpaRepository findings;

    @Test
    void workerProcessesQueuedReviewJobs() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_worker");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyAnalysisInputWithTodo(user, repo);
        ReviewJob job = reviewJobService.create(new CreateReviewJobCommand(
                        user.userId(),
                        user.personalOrganizationId(),
                        repo.getId(),
                        input.getId(),
                        "FULL_REPOSITORY",
                        null,
                        null))
                .job();

        assertThat(job.getStatus()).isEqualTo(ReviewJobStatus.QUEUED);

        ReviewJob processed = drainUntilTerminal(job.getId());

        assertThat(processed.getStatus()).isEqualTo(ReviewJobStatus.COMPLETED);
        assertThat(processed.getTotalFindings()).isEqualTo(1);
        assertThat(findings.findByReviewJobIdOrderByCreatedAtAsc(job.getId())).hasSize(1);
    }

    @Test
    void workerSchedulesRetryThenJobEventuallyFailsAfterMaxAttempts() {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_worker_retry");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyAnalysisInputWithMissingWorkspace(user, repo);
        ReviewJob job = reviewJobService.create(new CreateReviewJobCommand(
                        user.userId(),
                        user.personalOrganizationId(),
                        repo.getId(),
                        input.getId(),
                        "FULL_REPOSITORY",
                        null,
                        null))
                .job();

        ReviewJob retryQueued = drainUntilAttempted(job.getId());

        assertThat(retryQueued.getStatus()).isEqualTo(ReviewJobStatus.QUEUED);
        assertThat(retryQueued.getAttemptCount()).isEqualTo(1);
        assertThat(retryQueued.getMaxAttempts()).isEqualTo(3);
        assertThat(retryQueued.getNextRunAt()).isNotNull();
        assertThat(retryQueued.getLastFailureReason()).isEqualTo("Review job failed safely.");
        assertThat(retryQueued.getErrorReason()).isNull();

        executionService.runSystem(job.getId());
        ReviewJob secondRetry = reviewJobs.findById(job.getId()).orElseThrow();
        assertThat(secondRetry.getStatus()).isEqualTo(ReviewJobStatus.QUEUED);
        assertThat(secondRetry.getAttemptCount()).isEqualTo(2);

        executionService.runSystem(job.getId());
        ReviewJob failed = reviewJobs.findById(job.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(3);
        assertThat(failed.getNextRunAt()).isNull();
        assertThat(failed.getErrorReason()).isEqualTo("Review job failed safely.");
    }

    private ReviewJob drainUntilTerminal(UUID jobId) {
        for (int i = 0; i < 20; i++) {
            workerService.processNextBatch();
            ReviewJob job = reviewJobs.findById(jobId).orElseThrow();
            if (job.getStatus() == ReviewJobStatus.COMPLETED || job.getStatus() == ReviewJobStatus.FAILED) {
                return job;
            }
        }
        return reviewJobs.findById(jobId).orElseThrow();
    }

    private ReviewJob drainUntilAttempted(UUID jobId) {
        for (int i = 0; i < 20; i++) {
            workerService.processNextBatch();
            ReviewJob job = reviewJobs.findById(jobId).orElseThrow();
            if (job.getAttemptCount() > 0) {
                return job;
            }
        }
        return reviewJobs.findById(jobId).orElseThrow();
    }

    private CodeAnalysisInput readyAnalysisInputWithTodo(ProvisioningOutcome user, ImportedRepository repo)
            throws Exception {
        Path workspace = Files.createTempDirectory("codementor-worker-it-");
        Path source = workspace.resolve("App.java");
        String content = "class App {\n  // TODO: worker should find this\n}\n";
        Files.writeString(source, content, StandardCharsets.UTF_8);

        RepositorySnapshot snapshot = new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId());
        snapshot.markScanning("main", "sha-worker", workspace.toString());
        snapshot.markReady(1, Files.size(source));
        snapshots.save(snapshot);

        fileEntries.save(new RepositoryFileEntry(
                snapshot.getId(), "App.java", "Java", Files.size(source), true, null));

        CodeAnalysisInput input =
                new CodeAnalysisInput(snapshot.getId(), repo.getId(), user.personalOrganizationId(), user.userId());
        input.markReady("input-hash-" + UUID.randomUUID(), 1, 0, 0, content.length());
        return analysisInputs.save(input);
    }

    private CodeAnalysisInput readyAnalysisInputWithMissingWorkspace(ProvisioningOutcome user, ImportedRepository repo) {
        RepositorySnapshot snapshot = new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId());
        snapshot.markScanning(
                "main",
                "sha-worker-missing",
                System.getProperty("java.io.tmpdir") + "/missing-worker-workspace-" + UUID.randomUUID());
        snapshot.markReady(0, 0);
        snapshots.save(snapshot);

        CodeAnalysisInput input =
                new CodeAnalysisInput(snapshot.getId(), repo.getId(), user.personalOrganizationId(), user.userId());
        input.markReady("input-hash-" + UUID.randomUUID(), 0, 0, 0, 0);
        return analysisInputs.save(input);
    }

    private ImportedRepository persistRepository(ProvisioningOutcome user) {
        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(user.userId(), RepositoryProvider.GITHUB)
                .orElseThrow();
        return repositories.save(new ImportedRepository(
                user.personalOrganizationId(),
                connection.getId(),
                RepositoryProvider.GITHUB,
                "ext-" + UUID.randomUUID(),
                "owner",
                "repo",
                "owner/repo",
                RepositoryVisibility.PRIVATE,
                user.userId()));
    }
}
