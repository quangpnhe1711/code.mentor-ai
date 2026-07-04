package com.lvn.codementor.ai.administration.persistence;

import com.lvn.codementor.ai.administration.domain.WebhookEvent;
import com.lvn.codementor.ai.administration.domain.WebhookProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventJpaRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByProviderAndDeliveryId(WebhookProvider provider, String deliveryId);
}
