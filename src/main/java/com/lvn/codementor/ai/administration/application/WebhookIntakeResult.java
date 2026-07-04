package com.lvn.codementor.ai.administration.application;

import com.lvn.codementor.ai.administration.domain.WebhookEvent;

public record WebhookIntakeResult(WebhookEvent event, boolean replay) {
}
