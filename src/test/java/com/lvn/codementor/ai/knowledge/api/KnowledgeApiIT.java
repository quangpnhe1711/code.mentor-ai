package com.lvn.codementor.ai.knowledge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.auth.application.ProvisioningOutcome;
import com.lvn.codementor.ai.knowledge.persistence.ChatMessageJpaRepository;
import com.lvn.codementor.ai.knowledge.persistence.ChatSessionJpaRepository;
import com.lvn.codementor.ai.knowledge.persistence.CitationJpaRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class KnowledgeApiIT extends AbstractWebIT {

    private static final String SOURCE_MARKER = "PaymentService handles checkout orchestration";

    @Autowired
    GitProviderConnectionJpaRepository connections;

    @Autowired
    ImportedRepositoryJpaRepository repositories;

    @Autowired
    RepositorySnapshotJpaRepository snapshots;

    @Autowired
    RepositoryFileEntryJpaRepository fileEntries;

    @Autowired
    ChatSessionJpaRepository sessions;

    @Autowired
    ChatMessageJpaRepository messages;

    @Autowired
    CitationJpaRepository citations;

    @Test
    void requiresPlatformJwt() throws Exception {
        mockMvc.perform(post(url(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void requiresReadySnapshotBeforeAnswering() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        ImportedRepository repo = persistRepository(user);

        mockMvc.perform(post(url(user.personalOrganizationId(), repo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Where is payment handled?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SNAPSHOT_NOT_READY"));
    }

    @Test
    void answersWithCitationsWithoutLeakingSourceContent() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        ImportedRepository repo = persistRepository(user);
        UUID snapshotId = createReadySnapshot(user, repo);

        MvcResult result = mockMvc.perform(post(url(user.personalOrganizationId(), repo.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.tokens().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Where is payment checkout handled?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.snapshotId").value(snapshotId.toString()))
                .andExpect(jsonPath("$.data.citations.length()").value(1))
                .andExpect(jsonPath("$.data.citations[0].filePath").value("src/main/java/PaymentService.java"))
                .andExpect(jsonPath("$.data.citations[0].lineStart").value(2))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(SOURCE_MARKER);
        assertThat(sessions.findByOrganizationIdAndRepositoryIdOrderByCreatedAtDesc(
                        user.personalOrganizationId(), repo.getId()))
                .hasSize(1);
        assertThat(messages.findAll()).hasSize(2);
        assertThat(citations.findAll()).hasSize(1);
    }

    private String url(UUID organizationId, UUID repositoryId) {
        return "/api/organizations/" + organizationId + "/repositories/" + repositoryId + "/knowledge/questions";
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

    private UUID createReadySnapshot(ProvisioningOutcome user, ImportedRepository repo) throws Exception {
        Path workspace = Files.createTempDirectory("codementor-knowledge-test-");
        Files.createDirectories(workspace.resolve("src/main/java"));
        Files.writeString(
                workspace.resolve("src/main/java/PaymentService.java"),
                "package demo;\n"
                        + SOURCE_MARKER + "\n"
                        + "class PaymentService {}\n");

        RepositorySnapshot snapshot = new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId());
        snapshot.markScanning("main", "sha-knowledge", workspace.toString());
        snapshot.markReady(1, 120);
        snapshots.save(snapshot);
        fileEntries.save(new RepositoryFileEntry(
                snapshot.getId(), "src/main/java/PaymentService.java", "java", 120, true, null));
        return snapshot.getId();
    }
}
