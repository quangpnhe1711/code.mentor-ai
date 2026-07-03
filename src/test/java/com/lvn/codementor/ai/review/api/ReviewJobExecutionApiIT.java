package com.lvn.codementor.ai.review.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.persistence.CodeAnalysisInputJpaRepository;
import com.lvn.codementor.ai.github.application.GitCloneResult;
import com.lvn.codementor.ai.github.application.GitCloneSpec;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositorySnapshotJpaRepository;
import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.domain.ReviewJobStatus;
import com.lvn.codementor.ai.review.domain.ReviewType;
import com.lvn.codementor.ai.review.persistence.ReviewJobJpaRepository;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Review-execution engine end-to-end. The clone client is mocked (no real GitHub) and no external AI
 * provider is involved: a real READY snapshot is prepared with code containing detectable patterns,
 * then a review job is created and run through the local deterministic analyzer.
 */
class ReviewJobExecutionApiIT extends AbstractWebIT {

    // Unique markers so tests can assert source snippets never leak into findings/responses.
    private static final String TODO_TOKEN = "uniquetodotoken";
    private static final String PRINT_TOKEN = "uniqueprinttoken";
    private static final String CONSOLE_TOKEN = "uniqueconsoletoken";

    @Autowired
    GitProviderConnectionJpaRepository connections;

    @Autowired
    ImportedRepositoryJpaRepository repositories;

    @Autowired
    RepositorySnapshotJpaRepository snapshots;

    @Autowired
    CodeAnalysisInputJpaRepository analysisInputs;

    @Autowired
    ReviewJobJpaRepository reviewJobs;

    private String runUrl(UUID orgId, UUID repoId, UUID jobId) {
        return "/api/organizations/" + orgId + "/repositories/" + repoId + "/review-jobs/" + jobId + "/run";
    }

    private String findingsUrl(UUID orgId, UUID repoId, UUID jobId) {
        return "/api/organizations/" + orgId + "/repositories/" + repoId + "/review-jobs/" + jobId + "/findings";
    }

    private String eventsUrl(UUID orgId, UUID repoId, UUID jobId) {
        return "/api/organizations/" + orgId + "/repositories/" + repoId + "/review-jobs/" + jobId + "/events";
    }

    // (1) requires JWT
    @Test
    void requiresPlatformJwt() throws Exception {
        mockMvc.perform(post(runUrl(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    // (2) non-member cannot run
    @Test
    void nonMemberCannotRun() throws Exception {
        ProvisioningOutcome owner = provision("gh-" + UUID.randomUUID(), "gho_owner");
        ProvisioningOutcome other = provision("gh-" + UUID.randomUUID(), "gho_other");
        ImportedRepository repo = persistRepository(owner);

        mockMvc.perform(post(runUrl(owner.personalOrganizationId(), repo.getId(), UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.tokens().accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // (3) job must belong to the organization + repository in the path
    @Test
    void jobMustBelongToRepository() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repoA = persistRepository(user);
        ImportedRepository repoB = persistRepository(user);
        ReviewJob job = persistQueuedJob(user, repoA, bogusReadySnapshot(user, repoA));

        mockMvc.perform(post(runUrl(user.personalOrganizationId(), repoB.getId(), job.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // (4) only a QUEUED job can be run; a second run is rejected
    @Test
    void onlyQueuedJobCanRun() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        UUID jobId = createJobFromCode(user, repo);

        // First run completes.
        mockMvc.perform(post(runUrl(user.personalOrganizationId(), repo.getId(), jobId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Second run is rejected (COMPLETED → RUNNING not allowed).
        mockMvc.perform(post(runUrl(user.personalOrganizationId(), repo.getId(), jobId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REVIEW_JOB_NOT_RUNNABLE"));
    }

    // (5,6,7,8,10) run transitions to COMPLETED, records events, and finds TODO + debug statements
    @Test
    void runProducesFindingsAndCompletes() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        UUID jobId = createJobFromCode(user, repo);

        mockMvc.perform(post(runUrl(user.personalOrganizationId(), repo.getId(), jobId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                // TODO (App.java) + System.out.println (App.java) + console.log (app.ts) = 3
                .andExpect(jsonPath("$.data.totalFindings").value(3));

        // (6) RUNNING and COMPLETED events exist (plus the QUEUED event from creation).
        MvcResult events = mockMvc.perform(get(eventsUrl(user.personalOrganizationId(), repo.getId(), jobId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andReturn();
        String eventsBody = events.getResponse().getContentAsString();
        assertThat(eventsBody).contains("QUEUED").contains("RUNNING").contains("COMPLETED");

        // (7,8) findings include maintainability (TODO) and debug-code categories.
        mockMvc.perform(get(findingsUrl(user.personalOrganizationId(), repo.getId(), jobId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    // (11) findings endpoint returns persisted findings; (9,13) no source snippet is exposed
    @Test
    void findingsArePersistedWithoutSourceSnippets() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        UUID jobId = createJobFromCode(user, repo);

        mockMvc.perform(post(runUrl(user.personalOrganizationId(), repo.getId(), jobId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get(findingsUrl(user.personalOrganizationId(), repo.getId(), jobId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("MAINTAINABILITY").contains("DEBUG_CODE");
        // The actual source content (comment text, string arguments) never leaves the service.
        assertThat(body).doesNotContain(TODO_TOKEN).doesNotContain(PRINT_TOKEN).doesNotContain(CONSOLE_TOKEN);
    }

    // (10) total_findings on the job equals the persisted finding count
    @Test
    void totalFindingsEqualsPersistedCount() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        UUID jobId = createJobFromCode(user, repo);

        MvcResult run = mockMvc.perform(post(runUrl(user.personalOrganizationId(), repo.getId(), jobId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andReturn();
        int total = objectMapper.readTree(run.getResponse().getContentAsString())
                .path("data").path("totalFindings").asInt();

        MvcResult findings = mockMvc.perform(get(findingsUrl(user.personalOrganizationId(), repo.getId(), jobId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andReturn();
        int listed = objectMapper.readTree(findings.getResponse().getContentAsString()).path("data").size();

        assertThat(total).isEqualTo(listed).isEqualTo(3);
    }

    // (12,13) a failing run marks the job FAILED with a safe reason and leaks no workspace path
    @Test
    void failedRunRecordsSafeReason() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        // A READY snapshot whose workspace path points nowhere -> materialize fails during run.
        String bogusPath = System.getProperty("java.io.tmpdir") + "/missing-review-workspace-" + UUID.randomUUID();
        RepositorySnapshot snapshot =
                new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId());
        snapshot.markScanning("main", "sha123", bogusPath);
        snapshot.markReady(0, 0);
        snapshots.save(snapshot);
        ReviewJob job = persistQueuedJob(user, repo, snapshot.getId());

        MvcResult result = mockMvc.perform(post(runUrl(user.personalOrganizationId(), repo.getId(), job.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorReason").value("Review job failed safely."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(bogusPath).doesNotContain("missing-review-workspace");
        assertThat(reviewJobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(ReviewJobStatus.FAILED);
    }

    // --- helpers ---

    /** Full pipeline: READY snapshot with detectable code → READY analysis input → QUEUED review job. */
    private UUID createJobFromCode(ProvisioningOutcome user, ImportedRepository repo) throws Exception {
        UUID snapshotId = createReadySnapshotWithCode(user, repo);
        UUID inputId = buildAnalysisInput(user, repo, snapshotId);
        return createReviewJob(user, repo, inputId);
    }

    private UUID createReadySnapshotWithCode(ProvisioningOutcome user, ImportedRepository repo) throws Exception {
        when(gitRepositoryCloneClient.cloneRepository(any())).thenAnswer(invocation -> {
            GitCloneSpec spec = invocation.getArgument(0);
            Path dir = spec.targetDirectory();
            Files.createDirectories(dir.resolve("src"));
            Files.writeString(
                    dir.resolve("src/App.java"),
                    "class App {\n"
                            + "  void run() {\n"
                            + "    // TODO: " + TODO_TOKEN + "\n"
                            + "    System.out.println(\"" + PRINT_TOKEN + "\");\n"
                            + "  }\n"
                            + "}\n");
            Files.writeString(
                    dir.resolve("app.ts"),
                    "export function f() {\n  console.log(\"" + CONSOLE_TOKEN + "\");\n}\n");
            return new GitCloneResult("abc123sha", "main");
        });

        String url = "/api/organizations/" + user.personalOrganizationId() + "/repositories/" + repo.getId()
                + "/snapshots";
        MvcResult result = mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    private UUID buildAnalysisInput(ProvisioningOutcome user, ImportedRepository repo, UUID snapshotId)
            throws Exception {
        String url = "/api/organizations/" + user.personalOrganizationId() + "/repositories/" + repo.getId()
                + "/snapshots/" + snapshotId + "/analysis-inputs";
        MvcResult result = mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    private UUID createReviewJob(ProvisioningOutcome user, ImportedRepository repo, UUID inputId) throws Exception {
        String url = "/api/organizations/" + user.personalOrganizationId() + "/repositories/" + repo.getId()
                + "/analysis-inputs/" + inputId + "/review-jobs";
        MvcResult result = mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    /** A READY snapshot with a bogus workspace (fine for guard tests that never materialize). */
    private UUID bogusReadySnapshot(ProvisioningOutcome user, ImportedRepository repo) {
        RepositorySnapshot snapshot =
                new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId());
        snapshot.markScanning("main", "sha", System.getProperty("java.io.tmpdir") + "/none-" + UUID.randomUUID());
        snapshot.markReady(0, 0);
        return snapshots.save(snapshot).getId();
    }

    /** Persist a QUEUED job directly (with a READY input) for guard tests that fail before execution. */
    private ReviewJob persistQueuedJob(ProvisioningOutcome user, ImportedRepository repo, UUID snapshotId) {
        CodeAnalysisInput input = new CodeAnalysisInput(
                snapshotId, repo.getId(), user.personalOrganizationId(), user.userId());
        input.markReady("sha256:test", 0, 0, 0, 0);
        analysisInputs.save(input);
        return reviewJobs.save(new ReviewJob(
                user.personalOrganizationId(),
                repo.getId(),
                snapshotId,
                input.getId(),
                user.userId(),
                ReviewType.FULL_REPOSITORY,
                input.getInputHash()));
    }

    private ImportedRepository persistRepository(ProvisioningOutcome user) {
        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(user.userId(), RepositoryProvider.GITHUB)
                .orElseThrow();
        ImportedRepository repo = new ImportedRepository(
                user.personalOrganizationId(),
                connection.getId(),
                RepositoryProvider.GITHUB,
                "ext-" + UUID.randomUUID(),
                "owner",
                "repo",
                "owner/repo",
                RepositoryVisibility.PRIVATE,
                user.userId());
        return repositories.save(repo);
    }
}
