package com.lvn.codementor.ai.administration.api;

import com.lvn.codementor.ai.administration.api.response.WebhookEventResponse;
import com.lvn.codementor.ai.administration.application.GitHubWebhookService;
import com.lvn.codementor.ai.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/github")
public class GitHubWebhookController {

    private final GitHubWebhookService gitHubWebhookService;

    public GitHubWebhookController(GitHubWebhookService gitHubWebhookService) {
        this.gitHubWebhookService = gitHubWebhookService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WebhookEventResponse>> receive(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {
        WebhookEventResponse data = WebhookEventResponse.from(
                gitHubWebhookService.receive(eventType, deliveryId, signature, payload));
        return ResponseEntity.status(data.replay() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(data, requestId()));
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
