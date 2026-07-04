package com.lvn.codementor.ai.review.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.persistence.CodeAnalysisInputJpaRepository;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositorySnapshotJpaRepository;
import com.lvn.codementor.ai.review.persistence.ReviewJobEventJpaRepository;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Review-job creation and inspection (pre-AI phase; no provider is called). A READY analysis input is
 * persisted directly (the builder pipeline is covered by CodeAnalysisApiIT), then jobs are created and
 * read through the API to exercise the ownership chain and response shape.
 */
class ReviewJobApiIT extends AbstractWebIT {

    private static final String KNOWN_HASH = "sha256:deadbeefcafe";

    @Autowired
    GitProviderConnectionJpaRepository connections;

    @Autowired
    ImportedRepositoryJpaRepository repositories;

    @Autowired
    RepositorySnapshotJpaRepository snapshots;

    @Autowired
    CodeAnalysisInputJpaRepository analysisInputs;

    @Autowired
    ReviewJobEventJpaRepository reviewJobEvents;

    private String createUrl(UUID orgId, UUID repoId, UUID inputId) {
        return "/api/organizations/" + orgId + "/repositories/" + repoId + "/analysis-inputs/" + inputId
                + "/review-jobs";
    }

    private String jobsUrl(UUID orgId, UUID repoId) {
        return "/api/organizations/" + orgId + "/repositories/" + repoId + "/review-jobs";
    }

    // (1) requires JWT
    @Test
    void requiresPlatformJwt() throws Exception {
        mockMvc.perform(post(createUrl(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    // (2) non-member is forbidden
    @Test
    void nonMemberCannotCreate() throws Exception {
        ProvisioningOutcome owner = provision("gh-" + UUID.randomUUID(), "gho_owner");
        ProvisioningOutcome other = provision("gh-" + UUID.randomUUID(), "gho_other");
        ImportedRepository repo = persistRepository(owner);
        CodeAnalysisInput input = readyInput(owner, repo);

        mockMvc.perform(post(createUrl(owner.personalOrganizationId(), repo.getId(), input.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.tokens().accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // (3) repository must belong to the organization
    @Test
    void repositoryMustBelongToOrganization() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), UUID.randomUUID(), UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // (4) analysis input must belong to the repository in the path
    @Test
    void analysisInputMustBelongToRepository() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repoA = persistRepository(user);
        ImportedRepository repoB = persistRepository(user);
        CodeAnalysisInput inputInA = readyInput(user, repoA);

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repoB.getId(), inputInA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // (5) analysis input must belong to the organization in the path (cross-org isolation)
    @Test
    void analysisInputMustBelongToOrganization() throws Exception {
        ProvisioningOutcome orgA = provision("gh-" + UUID.randomUUID(), "gho_a");
        ProvisioningOutcome orgB = provision("gh-" + UUID.randomUUID(), "gho_b");
        ImportedRepository repoA = persistRepository(orgA);
        CodeAnalysisInput inputInA = readyInput(orgA, repoA);
        ImportedRepository repoB = persistRepository(orgB);

        // orgB member tries to create a job under orgB using orgA's input id.
        mockMvc.perform(post(createUrl(orgB.personalOrganizationId(), repoB.getId(), inputInA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgB.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // (6) analysis input must be READY
    @Test
    void analysisInputMustBeReady() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        // A freshly constructed input is BUILDING (not READY).
        CodeAnalysisInput building = analysisInputs.save(
                new CodeAnalysisInput(persistSnapshot(user, repo), repo.getId(), user.personalOrganizationId(), user.userId()));

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), building.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CODE_ANALYSIS_INPUT_NOT_READY"));
    }

    // (7,8) successful create returns a QUEUED job and copies input_hash
    @Test
    void createsQueuedJobCopyingInputHash() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyInput(user, repo);

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewType\":\"FULL_REPOSITORY\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.reviewType").value("FULL_REPOSITORY"))
                .andExpect(jsonPath("$.data.targetPullRequestNumber").doesNotExist())
                .andExpect(jsonPath("$.data.targetRef").doesNotExist())
                .andExpect(jsonPath("$.data.inputHash").value(KNOWN_HASH))
                .andExpect(jsonPath("$.data.analysisInputId").value(input.getId().toString()))
                .andExpect(jsonPath("$.data.snapshotId").value(input.getSnapshotId().toString()))
                .andExpect(jsonPath("$.data.totalFindings").value(0));
    }

    // unknown review type is rejected with a validation error
    @Test
    void unknownReviewTypeIsRejected() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyInput(user, repo);

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewType\":\"SINGLE_FILE\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createsPullRequestReviewJobWithTargetNumber() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyInput(user, repo);

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewType\":\"PULL_REQUEST\",\"targetPullRequestNumber\":17}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewType").value("PULL_REQUEST"))
                .andExpect(jsonPath("$.data.targetPullRequestNumber").value(17))
                .andExpect(jsonPath("$.data.targetRef").doesNotExist());
    }

    @Test
    void pullRequestReviewRequiresPositiveTargetNumber() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyInput(user, repo);

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewType\":\"PULL_REQUEST\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewType\":\"PULL_REQUEST\",\"targetPullRequestNumber\":0}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createsBranchReviewJobWithTargetRef() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyInput(user, repo);

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewType\":\"BRANCH\",\"targetRef\":\" feature/review \"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewType").value("BRANCH"))
                .andExpect(jsonPath("$.data.targetRef").value("feature/review"))
                .andExpect(jsonPath("$.data.targetPullRequestNumber").doesNotExist());
    }

    @Test
    void branchReviewRequiresTargetRef() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyInput(user, repo);

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewType\":\"BRANCH\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // (9,13) create writes a QUEUED event, exposed via the events endpoint
    @Test
    void createWritesQueuedEvent() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyInput(user, repo);
        UUID jobId = createJob(user, repo, input);

        assertThat(reviewJobEvents.findByReviewJobIdOrderByCreatedAtAsc(jobId)).hasSize(1);

        mockMvc.perform(get(jobsUrl(user.personalOrganizationId(), repo.getId()) + "/" + jobId + "/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("QUEUED"))
                .andExpect(jsonPath("$.data[0].reviewJobId").value(jobId.toString()));
    }

    // (10) list is scoped to organization + repository
    @Test
    void listIsScopedToOrganizationAndRepository() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repoA = persistRepository(user);
        ImportedRepository repoB = persistRepository(user);
        UUID jobA = createJob(user, repoA, readyInput(user, repoA));
        createJob(user, repoB, readyInput(user, repoB));

        mockMvc.perform(get(jobsUrl(user.personalOrganizationId(), repoA.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(jobA.toString()));
    }

    // (11) get is scoped to organization + repository
    @Test
    void getIsScopedToRepository() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repoA = persistRepository(user);
        ImportedRepository repoB = persistRepository(user);
        UUID jobA = createJob(user, repoA, readyInput(user, repoA));

        // Correct repo resolves.
        mockMvc.perform(get(jobsUrl(user.personalOrganizationId(), repoA.getId()) + "/" + jobA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(jobA.toString()));

        // Same job id under the wrong repo is not found.
        mockMvc.perform(get(jobsUrl(user.personalOrganizationId(), repoB.getId()) + "/" + jobA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // (12) findings are empty for a new queued job
    @Test
    void findingsAreEmptyForNewJob() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        UUID jobId = createJob(user, repo, readyInput(user, repo));

        mockMvc.perform(get(jobsUrl(user.personalOrganizationId(), repo.getId()) + "/" + jobId + "/findings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // (14) response never exposes source content, workspace path, tokens, or secrets
    @Test
    void responseExposesNoSensitiveData() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyInput(user, repo);

        MvcResult result = mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("workspacePath").doesNotContain("workspace");
        assertThat(body).doesNotContain("token").doesNotContain("gho_").doesNotContain("ghp_");
        assertThat(body).doesNotContain("content").doesNotContain("sourceContent");
    }

    // --- helpers ---

    private UUID createJob(ProvisioningOutcome user, ImportedRepository repo, CodeAnalysisInput input)
            throws Exception {
        MvcResult result = mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    private CodeAnalysisInput readyInput(ProvisioningOutcome user, ImportedRepository repo) {
        CodeAnalysisInput input = new CodeAnalysisInput(
                persistSnapshot(user, repo), repo.getId(), user.personalOrganizationId(), user.userId());
        input.markReady(KNOWN_HASH, 3, 1, 2, 4096);
        return analysisInputs.save(input);
    }

    private UUID persistSnapshot(ProvisioningOutcome user, ImportedRepository repo) {
        return snapshots
                .save(new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId()))
                .getId();
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
