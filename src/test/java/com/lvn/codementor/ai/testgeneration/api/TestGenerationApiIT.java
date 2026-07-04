package com.lvn.codementor.ai.testgeneration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
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
import com.lvn.codementor.ai.support.AbstractWebIT;
import com.lvn.codementor.ai.testgeneration.persistence.GeneratedTestJpaRepository;
import com.lvn.codementor.ai.testgeneration.persistence.TestGenerationJobJpaRepository;
import com.lvn.codementor.ai.testgeneration.persistence.TestResultJpaRepository;
import com.lvn.codementor.ai.testgeneration.persistence.TestRunJpaRepository;
import com.lvn.codementor.ai.codeanalysis.persistence.CodeAnalysisInputJpaRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class TestGenerationApiIT extends AbstractWebIT {

    private static final String SOURCE_MARKER = "very-secret-source-implementation-detail";

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
    TestGenerationJobJpaRepository jobs;

    @Autowired
    GeneratedTestJpaRepository generatedTests;

    @Autowired
    TestRunJpaRepository runs;

    @Autowired
    TestResultJpaRepository results;

    @Test
    void requiresPlatformJwt() throws Exception {
        mockMvc.perform(post(createUrl(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void analysisInputMustBeReady() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        ImportedRepository repo = persistRepository(user);
        RepositorySnapshot snapshot = readySnapshot(user, repo);
        CodeAnalysisInput input = analysisInputs.save(
                new CodeAnalysisInput(snapshot.getId(), repo.getId(), user.personalOrganizationId(), user.userId()));

        mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"FILE\",\"targetFilePath\":\"src/main/java/Calculator.java\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CODE_ANALYSIS_INPUT_NOT_READY"));
    }

    @Test
    void createsGeneratedTestSuggestionAndRunsItWithResultMetadata() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        ImportedRepository repo = persistRepository(user);
        CodeAnalysisInput input = readyAnalysisInput(user, repo);

        MvcResult created = mockMvc.perform(post(createUrl(user.personalOrganizationId(), repo.getId(), input.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"FILE\",\"targetFilePath\":\"src/main/java/Calculator.java\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.job.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.job.targetType").value("FILE"))
                .andExpect(jsonPath("$.data.generatedTests.length()").value(1))
                .andExpect(jsonPath("$.data.generatedTests[0].filePath").value("generated/CalculatorTest.java"))
                .andReturn();

        String body = created.getResponse().getContentAsString();
        assertThat(body).doesNotContain(SOURCE_MARKER);
        UUID jobId = UUID.fromString(objectMapper.readTree(body)
                .path("data").path("job").path("id").asText());
        UUID generatedTestId = UUID.fromString(objectMapper.readTree(body)
                .path("data").path("generatedTests").get(0).path("id").asText());

        assertThat(jobs.findAll()).hasSize(1);
        assertThat(generatedTests.findAll()).hasSize(1);

        mockMvc.perform(get("/api/organizations/{orgId}/repositories/{repoId}/test-generation-jobs/{jobId}/generated-tests",
                        user.personalOrganizationId(), repo.getId(), jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(generatedTestId.toString()));

        MvcResult run = mockMvc.perform(post("/api/organizations/{orgId}/repositories/{repoId}/generated-tests/{generatedTestId}/runs",
                        user.personalOrganizationId(), repo.getId(), generatedTestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PASSED"))
                .andExpect(jsonPath("$.data.sandboxMode").value("SANDBOX_SIMULATED"))
                .andExpect(jsonPath("$.data.timeoutSeconds").value(300))
                .andExpect(jsonPath("$.data.exitCode").value(0))
                .andExpect(jsonPath("$.data.stdout").isNotEmpty())
                .andExpect(jsonPath("$.data.stderr").value(""))
                .andReturn();

        UUID runId = UUID.fromString(objectMapper.readTree(run.getResponse().getContentAsString())
                .path("data").path("id").asText());
        assertThat(runs.findAll()).hasSize(1);
        assertThat(results.findAll()).hasSize(1);

        mockMvc.perform(get("/api/organizations/{orgId}/repositories/{repoId}/test-runs/{runId}",
                        user.personalOrganizationId(), repo.getId(), runId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runId.toString()))
                .andExpect(jsonPath("$.data.status").value("PASSED"));
    }

    private String createUrl(UUID organizationId, UUID repositoryId, UUID analysisInputId) {
        return "/api/organizations/" + organizationId + "/repositories/" + repositoryId
                + "/analysis-inputs/" + analysisInputId + "/test-generation-jobs";
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

    private CodeAnalysisInput readyAnalysisInput(ProvisioningOutcome user, ImportedRepository repo) throws Exception {
        RepositorySnapshot snapshot = readySnapshot(user, repo);
        CodeAnalysisInput input = new CodeAnalysisInput(
                snapshot.getId(), repo.getId(), user.personalOrganizationId(), user.userId());
        input.markReady("hash-" + UUID.randomUUID(), 1, 0, 0, 120);
        return analysisInputs.save(input);
    }

    private RepositorySnapshot readySnapshot(ProvisioningOutcome user, ImportedRepository repo) throws Exception {
        Path workspace = Files.createTempDirectory("codementor-test-generation-");
        Files.createDirectories(workspace.resolve("src/main/java"));
        Files.writeString(
                workspace.resolve("src/main/java/Calculator.java"),
                "package demo;\n"
                        + "class Calculator {\n"
                        + "  String marker = \"" + SOURCE_MARKER + "\";\n"
                        + "  int add(int a, int b) { return a + b; }\n"
                        + "}\n");
        RepositorySnapshot snapshot = new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId());
        snapshot.markScanning("main", "sha-testgen", workspace.toString());
        snapshot.markReady(1, 120);
        snapshots.save(snapshot);
        fileEntries.save(new RepositoryFileEntry(
                snapshot.getId(), "src/main/java/Calculator.java", "java", 120, true, null));
        return snapshot;
    }
}
