package com.lvn.codementor.ai.codeanalysis.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInputStatus;
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
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Sanitized review-input build + fetch. The clone client is mocked (no real GitHub), and no AI
 * provider is involved. A real snapshot is prepared through the snapshot endpoint so the build reads
 * actual files from the pruned workspace.
 */
class CodeAnalysisApiIT extends AbstractWebIT {

    private static final String GITHUB_TOKEN = "ghp_" + "a".repeat(30);

    @Autowired
    GitProviderConnectionJpaRepository connections;

    @Autowired
    ImportedRepositoryJpaRepository repositories;

    @Autowired
    RepositorySnapshotJpaRepository snapshots;

    @Autowired
    CodeAnalysisInputJpaRepository analysisInputs;

    private String buildUrl(UUID orgId, UUID repoId, UUID snapshotId) {
        return "/api/organizations/" + orgId + "/repositories/" + repoId + "/snapshots/" + snapshotId
                + "/analysis-inputs";
    }

    private String getUrl(UUID orgId, UUID repoId, UUID analysisInputId) {
        return "/api/organizations/" + orgId + "/repositories/" + repoId + "/analysis-inputs/" + analysisInputId;
    }

    // (1) requires JWT
    @Test
    void requiresPlatformJwt() throws Exception {
        mockMvc.perform(post(buildUrl(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    // (2) non-member is forbidden
    @Test
    void nonMemberCannotBuild() throws Exception {
        ProvisioningOutcome owner = provision("gh-" + UUID.randomUUID(), "gho_owner");
        ProvisioningOutcome other = provision("gh-" + UUID.randomUUID(), "gho_other");
        ImportedRepository repo = persistRepository(owner);

        mockMvc.perform(post(buildUrl(owner.personalOrganizationId(), repo.getId(), UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.tokens().accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // (3) repository must belong to the organization
    @Test
    void repositoryMustBelongToOrganization() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");

        mockMvc.perform(post(buildUrl(user.personalOrganizationId(), UUID.randomUUID(), UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // (4) snapshot must belong to the repository
    @Test
    void snapshotMustBelongToRepository() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        // A snapshot that belongs to a DIFFERENT (real) repository in the same org.
        ImportedRepository otherRepo = persistRepository(user);
        RepositorySnapshot foreign = snapshots.save(
                new RepositorySnapshot(otherRepo.getId(), user.personalOrganizationId(), user.userId()));

        mockMvc.perform(post(buildUrl(user.personalOrganizationId(), repo.getId(), foreign.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // (5) snapshot must be READY
    @Test
    void snapshotMustBeReady() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        RepositorySnapshot pending = snapshots.save(
                new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId()));

        mockMvc.perform(post(buildUrl(user.personalOrganizationId(), repo.getId(), pending.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SNAPSHOT_NOT_READY"));
    }

    // (6-10) safe text included, secrets masked, binary + disallowed-extension skipped, sensitive already gone
    @Test
    void buildsSanitizedInputIncludingSafeFilesAndMaskingSecrets() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        UUID snapshotId = createReadySnapshot(user, repo);

        MvcResult result = mockMvc.perform(post(buildUrl(user.personalOrganizationId(), repo.getId(), snapshotId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("READY"))
                // App.java + Service.java + README.md are included
                .andExpect(jsonPath("$.data.includedFileCount").value(3))
                // config.json (binary) + notes.txt (disallowed extension) are skipped
                .andExpect(jsonPath("$.data.skippedFileCount").value(2))
                .andExpect(jsonPath("$.data.maskedSecretCount").value(2))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).path("data").path("inputHash").asText()).isNotBlank();
        // (12) never exposes secrets or source content
        assertThat(body).doesNotContain(GITHUB_TOKEN);
        assertThat(body).doesNotContain("supersecretapikeyvalue");
        assertThat(body).doesNotContain("class App");
    }

    // (11) input_hash is stable for the same sanitized input
    @Test
    void inputHashIsStableAcrossRebuilds() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        UUID snapshotId = createReadySnapshot(user, repo);

        String first = hashOfBuild(user, repo.getId(), snapshotId);
        String second = hashOfBuild(user, repo.getId(), snapshotId);

        assertThat(first).isNotBlank().isEqualTo(second);
    }

    // GET returns the persisted metadata
    @Test
    void getReturnsAnalysisInputMetadata() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        UUID snapshotId = createReadySnapshot(user, repo);

        MvcResult built = mockMvc.perform(post(buildUrl(user.personalOrganizationId(), repo.getId(), snapshotId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andReturn();
        UUID inputId = UUID.fromString(objectMapper.readTree(built.getResponse().getContentAsString())
                .path("data").path("id").asText());

        mockMvc.perform(get(getUrl(user.personalOrganizationId(), repo.getId(), inputId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(inputId.toString()))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.snapshotId").value(snapshotId.toString()));
    }

    // (13) a build failure records a SAFE error reason only (no path/content/secret)
    @Test
    void buildFailureRecordsSafeErrorReason() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");
        ImportedRepository repo = persistRepository(user);
        // A READY snapshot whose workspace path points nowhere -> build cannot read files.
        String bogusPath = System.getProperty("java.io.tmpdir") + "/missing-workspace-" + UUID.randomUUID();
        RepositorySnapshot snapshot =
                new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId());
        snapshot.markScanning("main", "sha123", bogusPath);
        snapshot.markReady(0, 0);
        snapshots.save(snapshot);

        MvcResult result = mockMvc.perform(post(buildUrl(user.personalOrganizationId(), repo.getId(), snapshot.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        UUID inputId = UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
        CodeAnalysisInput saved = analysisInputs.findById(inputId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CodeAnalysisInputStatus.FAILED);
        assertThat(saved.getErrorReason()).doesNotContain(bogusPath);
        assertThat(saved.getInputHash()).isNull();
    }

    // --- helpers ---

    private String hashOfBuild(ProvisioningOutcome user, UUID repoId, UUID snapshotId) throws Exception {
        MvcResult result = mockMvc.perform(post(buildUrl(user.personalOrganizationId(), repoId, snapshotId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("inputHash").asText();
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

    /** Prepare a real READY snapshot via the snapshot endpoint, writing a mixed file tree on clone. */
    private UUID createReadySnapshot(ProvisioningOutcome user, ImportedRepository repo) throws Exception {
        when(gitRepositoryCloneClient.cloneRepository(any())).thenAnswer(invocation -> {
            GitCloneSpec spec = invocation.getArgument(0);
            Path dir = spec.targetDirectory();
            Files.createDirectories(dir.resolve("src"));
            Files.writeString(dir.resolve("src/App.java"), "class App {}");
            Files.writeString(
                    dir.resolve("src/Service.java"),
                    "public class Service {\n"
                            + "  String token = \"" + GITHUB_TOKEN + "\";\n"
                            + "  String api_key = \"supersecretapikeyvalue\";\n"
                            + "  void run() { System.out.println(\"hello world\"); }\n"
                            + "}\n");
            Files.writeString(dir.resolve("README.md"), "# hello");
            Files.write(dir.resolve("config.json"), new byte[] {'{', 0x00, '}'}); // binary
            Files.writeString(dir.resolve("notes.txt"), "just some notes"); // disallowed extension
            Files.writeString(dir.resolve(".env"), "SECRET=abc"); // sensitive -> pruned by snapshot
            return new GitCloneResult("abc123sha", "main");
        });

        String snapshotUrl =
                "/api/organizations/" + user.personalOrganizationId() + "/repositories/" + repo.getId() + "/snapshots";
        MvcResult result = mockMvc.perform(post(snapshotUrl)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }
}
