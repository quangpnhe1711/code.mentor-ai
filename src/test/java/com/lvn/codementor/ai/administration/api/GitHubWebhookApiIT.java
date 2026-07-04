package com.lvn.codementor.ai.administration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lvn.codementor.ai.administration.domain.WebhookProcessingStatus;
import com.lvn.codementor.ai.administration.domain.WebhookProvider;
import com.lvn.codementor.ai.administration.persistence.WebhookEventJpaRepository;
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
import com.lvn.codementor.ai.review.domain.ReviewJob;
import com.lvn.codementor.ai.review.persistence.ReviewJobJpaRepository;
import com.lvn.codementor.ai.support.AbstractWebIT;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class GitHubWebhookApiIT extends AbstractWebIT {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final String EXTERNAL_REPO_ID = "987654321";

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

    @Autowired
    WebhookEventJpaRepository webhookEvents;

    @Test
    void invalidSignatureIsRejectedBeforePlatformJwt() throws Exception {
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-" + UUID.randomUUID())
                        .header("X-Hub-Signature-256", "sha256=bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("opened", 7, EXTERNAL_REPO_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void pullRequestOpenedQueuesReviewJobAndReplayDoesNotDuplicate() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        String externalRepoId = uniqueExternalRepoId();
        ImportedRepository repo = persistRepository(user, externalRepoId);
        readyAnalysisInput(user, repo);
        String deliveryId = "delivery-" + UUID.randomUUID();
        String payload = payload("opened", 42, externalRepoId);

        MvcResult first = mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", deliveryId)
                        .header("X-Hub-Signature-256", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.processingStatus").value("PROCESSED"))
                .andExpect(jsonPath("$.data.signatureVerified").value(true))
                .andExpect(jsonPath("$.data.replay").value(false))
                .andReturn();

        UUID reviewJobId = UUID.fromString(objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("reviewJobId").asText());
        ReviewJob job = reviewJobs.findById(reviewJobId).orElseThrow();
        assertThat(job.getReviewType().name()).isEqualTo("PULL_REQUEST");
        assertThat(job.getTargetPullRequestNumber()).isEqualTo(42);

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", deliveryId)
                        .header("X-Hub-Signature-256", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replay").value(true))
                .andExpect(jsonPath("$.data.reviewJobId").value(reviewJobId.toString()));

        assertThat(reviewJobs.findAll())
                .filteredOn(candidate -> candidate.getRepositoryId().equals(repo.getId()))
                .hasSize(1);
        assertThat(webhookEvents.findByProviderAndDeliveryId(WebhookProvider.GITHUB, deliveryId)).isPresent();
    }

    @Test
    void unsupportedPullRequestActionIsStoredAsIgnored() throws Exception {
        ProvisioningOutcome user = provision("gh-" + UUID.randomUUID(), "token");
        String externalRepoId = uniqueExternalRepoId();
        ImportedRepository repo = persistRepository(user, externalRepoId);
        readyAnalysisInput(user, repo);
        String payload = payload("closed", 12, externalRepoId);
        String deliveryId = "delivery-" + UUID.randomUUID();

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", deliveryId)
                        .header("X-Hub-Signature-256", signature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.processingStatus").value("IGNORED"))
                .andExpect(jsonPath("$.data.errorReason").value("Pull request action does not trigger review"));

        assertThat(reviewJobs.findAll())
                .filteredOn(candidate -> candidate.getRepositoryId().equals(repo.getId()))
                .isEmpty();
        assertThat(webhookEvents.findByProviderAndDeliveryId(WebhookProvider.GITHUB, deliveryId))
                .hasValueSatisfying(event -> assertThat(event.getProcessingStatus())
                        .isEqualTo(WebhookProcessingStatus.IGNORED));
    }

    private ImportedRepository persistRepository(ProvisioningOutcome user, String externalRepoId) {
        GitProviderConnection connection = connections
                .findFirstByUserIdAndProvider(user.userId(), RepositoryProvider.GITHUB)
                .orElseThrow();
        return repositories.save(new ImportedRepository(
                user.personalOrganizationId(),
                connection.getId(),
                RepositoryProvider.GITHUB,
                externalRepoId,
                "owner",
                "repo",
                "owner/repo",
                RepositoryVisibility.PRIVATE,
                user.userId()));
    }

    private CodeAnalysisInput readyAnalysisInput(ProvisioningOutcome user, ImportedRepository repo) {
        RepositorySnapshot snapshot = new RepositorySnapshot(repo.getId(), user.personalOrganizationId(), user.userId());
        snapshot.markScanning("main", "sha-webhook", System.getProperty("java.io.tmpdir"));
        snapshot.markReady(0, 0);
        snapshots.save(snapshot);
        CodeAnalysisInput input = new CodeAnalysisInput(
                snapshot.getId(), repo.getId(), user.personalOrganizationId(), user.userId());
        input.markReady("input-hash-" + UUID.randomUUID(), 1, 0, 0, 100);
        return analysisInputs.save(input);
    }

    private static String uniqueExternalRepoId() {
        return Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
    }

    private static String payload(String action, int prNumber, String externalRepoId) {
        return """
                {
                  "action": "%s",
                  "number": %d,
                  "repository": {
                    "id": %s,
                    "full_name": "owner/repo"
                  },
                  "pull_request": {
                    "number": %d
                  }
                }
                """.formatted(action, prNumber, externalRepoId, prNumber);
    }

    private static String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
