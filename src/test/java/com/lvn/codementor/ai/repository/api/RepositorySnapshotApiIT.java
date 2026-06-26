package com.lvn.codementor.ai.repository.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.JwtService;
import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.github.application.GitCloneResult;
import com.lvn.codementor.ai.github.application.GitCloneSpec;
import com.lvn.codementor.ai.identity.domain.User;
import com.lvn.codementor.ai.identity.persistence.UserJpaRepository;
import com.lvn.codementor.ai.organization.domain.OrganizationMember;
import com.lvn.codementor.ai.organization.domain.OrganizationRole;
import com.lvn.codementor.ai.organization.persistence.OrganizationMemberJpaRepository;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryFileEntry;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshotStatus;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositoryFileEntryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositorySnapshotJpaRepository;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** Repository clone + snapshot preparation (clone client mocked; no real GitHub network). */
class RepositorySnapshotApiIT extends AbstractWebIT {

    @Autowired
    GitProviderConnectionJpaRepository connections;

    @Autowired
    ImportedRepositoryJpaRepository repositories;

    @Autowired
    RepositorySnapshotJpaRepository snapshots;

    @Autowired
    RepositoryFileEntryJpaRepository fileEntries;

    @Autowired
    UserJpaRepository users;

    @Autowired
    OrganizationMemberJpaRepository members;

    @Autowired
    JwtService jwtService;

    private String url(UUID orgId, UUID repoId) {
        return "/api/organizations/" + orgId + "/repositories/" + repoId + "/snapshots";
    }

    @Test
    void requiresPlatformJwt() throws Exception {
        mockMvc.perform(post(url(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void nonMemberCannotCreateSnapshot() throws Exception {
        ProvisioningOutcome owner = provision("gh-" + UUID.randomUUID(), "gho_owner");
        ProvisioningOutcome other = provision("gh-" + UUID.randomUUID(), "gho_other");
        ImportedRepository repo = persistRepository(owner, connectionIdFor(owner));

        mockMvc.perform(post(url(owner.personalOrganizationId(), repo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.tokens().accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void repositoryMustBelongToOrganization() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_x");

        mockMvc.perform(post(url(user.personalOrganizationId(), UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void missingProviderConnectionReturnsStableError() throws Exception {
        // Owner has a connection (used for the repo's FK); a second member has NO GitHub connection and
        // therefore cannot obtain a token to clone with.
        ProvisioningOutcome owner = provision("gh-" + UUID.randomUUID(), "gho_owner");
        ImportedRepository repo = persistRepository(owner, connectionIdFor(owner));

        User member = users.save(new User("ghNoConn-" + UUID.randomUUID(), "noconn", null, null, null));
        members.save(new OrganizationMember(owner.personalOrganizationId(), member.getId(), OrganizationRole.DEVELOPER));
        String memberToken = jwtService.issue(member.getId()).token();

        mockMvc.perform(post(url(owner.personalOrganizationId(), repo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_CONNECTION_REQUIRED"));
    }

    @Test
    void successfulSnapshotCreatesRowFileEntriesAndSkipsSensitiveLargeAndDirectories() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_secret_access");
        ImportedRepository repo = persistRepository(user, connectionIdFor(user));
        stubCloneWritingSampleTree();

        MvcResult result = mockMvc.perform(post(url(user.personalOrganizationId(), repo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.commitSha").value("abc123sha"))
                .andExpect(jsonPath("$.data.sourceRef").value("main"))
                .andExpect(jsonPath("$.data.fileCount").value(2)) // App.java + README.md
                .andReturn();

        UUID snapshotId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // (5) snapshot row persisted READY
        RepositorySnapshot saved = snapshots.findById(snapshotId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RepositorySnapshotStatus.READY);

        // (6) file entries persisted
        List<RepositoryFileEntry> entries = fileEntries.findBySnapshotId(snapshotId);
        assertThat(entries).isNotEmpty();

        RepositoryFileEntry app = entry(entries, "src/main/java/App.java");
        assertThat(app.isIncluded()).isTrue();
        assertThat(app.getLanguage()).isEqualTo("java");

        // (7) sensitive file skipped
        RepositoryFileEntry env = entry(entries, ".env");
        assertThat(env.isIncluded()).isFalse();
        assertThat(env.getSkipReason()).isEqualTo("SENSITIVE_FILE");

        // (8) large file skipped
        RepositoryFileEntry big = entry(entries, "big.bin");
        assertThat(big.isIncluded()).isFalse();
        assertThat(big.getSkipReason()).isEqualTo("OVERSIZE");

        // (9) skipped directories produce no entries at all
        assertThat(entries).noneMatch(e -> e.getPath().startsWith("node_modules/"));
        assertThat(entries).noneMatch(e -> e.getPath().startsWith(".git/"));
    }

    @Test
    void snapshotResponseDoesNotExposeTokenOrWorkspacePath() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_super_secret_access");
        ImportedRepository repo = persistRepository(user, connectionIdFor(user));
        stubCloneWritingSampleTree();

        MvcResult result = mockMvc.perform(post(url(user.personalOrganizationId(), repo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("gho_super_secret_access");
        assertThat(body).doesNotContain("workspacePath");
        assertThat(body).doesNotContain("codementor-ai-test"); // workspace root fragment
        assertThat(body).doesNotContain("encrypted").doesNotContain("tokenHash");
    }

    @Test
    void cloneErrorMarksSnapshotFailedSafely() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "gho_secret_access");
        ImportedRepository repo = persistRepository(user, connectionIdFor(user));
        // Simulate a clone failure whose message could leak the remote URL/token; it must not surface.
        when(gitRepositoryCloneClient.cloneRepository(any()))
                .thenThrow(new RuntimeException("fatal: clone https://x:gho_secret_access@github.com/owner/repo.git"));

        MvcResult result = mockMvc.perform(post(url(user.personalOrganizationId(), repo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("gho_secret_access");
        UUID snapshotId = UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
        RepositorySnapshot saved = snapshots.findById(snapshotId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RepositorySnapshotStatus.FAILED);
        assertThat(saved.getErrorReason()).doesNotContain("gho_secret_access");
    }

    // --- helpers ---

    private UUID connectionIdFor(ProvisioningOutcome user) {
        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(user.userId(), RepositoryProvider.GITHUB)
                .orElseThrow();
        return connection.getId();
    }

    private ImportedRepository persistRepository(ProvisioningOutcome user, UUID connectionId) {
        ImportedRepository repo = new ImportedRepository(
                user.personalOrganizationId(),
                connectionId,
                RepositoryProvider.GITHUB,
                "ext-" + UUID.randomUUID(),
                "owner",
                "repo",
                "owner/repo",
                RepositoryVisibility.PRIVATE,
                user.userId());
        return repositories.save(repo);
    }

    private void stubCloneWritingSampleTree() {
        when(gitRepositoryCloneClient.cloneRepository(any())).thenAnswer(invocation -> {
            GitCloneSpec spec = invocation.getArgument(0);
            Path dir = spec.targetDirectory();
            Files.createDirectories(dir.resolve("src/main/java"));
            Files.writeString(dir.resolve("src/main/java/App.java"), "class App {}");
            Files.writeString(dir.resolve("README.md"), "# hello");
            Files.writeString(dir.resolve(".env"), "SECRET=abc"); // sensitive
            Files.writeString(dir.resolve("big.bin"), "x".repeat(2000)); // > 1024 byte test limit
            Files.createDirectories(dir.resolve("node_modules/pkg"));
            Files.writeString(dir.resolve("node_modules/pkg/index.js"), "module.exports={}");
            Files.createDirectories(dir.resolve(".git"));
            Files.writeString(dir.resolve(".git/config"), "[core]");
            return new GitCloneResult("abc123sha", "main");
        });
    }

    private static RepositoryFileEntry entry(List<RepositoryFileEntry> entries, String path) {
        return entries.stream()
                .filter(e -> e.getPath().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No file entry for " + path));
    }
}
