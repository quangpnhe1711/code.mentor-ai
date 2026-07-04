package com.lvn.codementor.ai.administration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvn.codementor.ai.administration.domain.WebhookEvent;
import com.lvn.codementor.ai.administration.domain.WebhookProvider;
import com.lvn.codementor.ai.administration.persistence.WebhookEventJpaRepository;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInputStatus;
import com.lvn.codementor.ai.codeanalysis.persistence.CodeAnalysisInputJpaRepository;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.review.application.ReviewJobService;
import com.lvn.codementor.ai.review.application.command.CreateReviewJobCommand;
import com.lvn.codementor.ai.review.application.result.CreateReviewJobResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubWebhookService {

    private static final List<String> REVIEWABLE_PULL_REQUEST_ACTIONS = List.of("opened", "reopened", "synchronize");

    private final ObjectMapper objectMapper;
    private final GitHubWebhookSignatureVerifier signatureVerifier;
    private final WebhookEventJpaRepository webhookEvents;
    private final ImportedRepositoryJpaRepository repositories;
    private final CodeAnalysisInputJpaRepository analysisInputs;
    private final ReviewJobService reviewJobService;

    public GitHubWebhookService(
            ObjectMapper objectMapper,
            GitHubWebhookSignatureVerifier signatureVerifier,
            WebhookEventJpaRepository webhookEvents,
            ImportedRepositoryJpaRepository repositories,
            CodeAnalysisInputJpaRepository analysisInputs,
            ReviewJobService reviewJobService) {
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
        this.webhookEvents = webhookEvents;
        this.repositories = repositories;
        this.analysisInputs = analysisInputs;
        this.reviewJobService = reviewJobService;
    }

    @Transactional
    public WebhookIntakeResult receive(String eventType, String deliveryId, String signature, String payload) {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "X-GitHub-Delivery is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "X-GitHub-Event is required");
        }
        Optional<WebhookEvent> existing = webhookEvents.findByProviderAndDeliveryId(WebhookProvider.GITHUB, deliveryId);
        if (existing.isPresent()) {
            return new WebhookIntakeResult(existing.get(), true);
        }

        boolean verified = signatureVerifier.verify(payload, signature);
        JsonNode root = parse(payload);
        String action = text(root.path("action"));
        String externalRepoId = text(root.path("repository").path("id"));
        String fullName = text(root.path("repository").path("full_name"));

        WebhookEvent event = webhookEvents.save(new WebhookEvent(
                WebhookProvider.GITHUB,
                deliveryId,
                eventType,
                action,
                externalRepoId,
                fullName,
                sha256(payload),
                verified));

        process(event, root);
        return new WebhookIntakeResult(event, false);
    }

    private void process(WebhookEvent event, JsonNode root) {
        if (!"pull_request".equals(event.getEventType())) {
            event.markIgnored("Unsupported GitHub event type");
            return;
        }
        if (!REVIEWABLE_PULL_REQUEST_ACTIONS.contains(event.getAction())) {
            event.markIgnored("Pull request action does not trigger review");
            return;
        }
        if (event.getExternalRepoId() == null || event.getExternalRepoId().isBlank()) {
            event.markFailed("Repository id is missing from webhook payload");
            return;
        }
        int pullRequestNumber = root.path("pull_request").path("number").asInt(0);
        if (pullRequestNumber <= 0) {
            pullRequestNumber = root.path("number").asInt(0);
        }
        if (pullRequestNumber <= 0) {
            event.markFailed("Pull request number is missing from webhook payload");
            return;
        }

        List<ImportedRepository> importedRepositories =
                repositories.findByProviderAndExternalRepoId(RepositoryProvider.GITHUB, event.getExternalRepoId());
        if (importedRepositories.isEmpty()) {
            event.markIgnored("Repository is not imported");
            return;
        }

        for (ImportedRepository repository : importedRepositories) {
            CodeAnalysisInput input = analysisInputs
                    .findFirstByOrganizationIdAndRepositoryIdAndStatusOrderByCreatedAtDesc(
                            repository.getOrganizationId(), repository.getId(), CodeAnalysisInputStatus.READY)
                    .orElse(null);
            if (input == null) {
                continue;
            }
            try {
                CreateReviewJobResult result = reviewJobService.create(new CreateReviewJobCommand(
                        repository.getImportedByUserId(),
                        repository.getOrganizationId(),
                        repository.getId(),
                        input.getId(),
                        "PULL_REQUEST",
                        pullRequestNumber,
                        null));
                event.markProcessed(result.job().getId());
                return;
            } catch (AppException ex) {
                if (ex.code() == ErrorCode.REVIEW_ALREADY_RUNNING) {
                    event.markIgnored("A review is already queued or running for this pull request");
                    return;
                }
                throw ex;
            }
        }
        event.markIgnored("No READY analysis input found for imported repository");
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid GitHub webhook payload");
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Webhook payload hashing failed");
        }
    }
}
