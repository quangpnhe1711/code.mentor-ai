package com.lvn.codementor.ai.provisioning;

import java.util.UUID;

/** Result of first-login provisioning: the resolved user, their personal org, and issued tokens. */
public record ProvisioningOutcome(UUID userId, UUID personalOrganizationId, IssuedTokens tokens) {
}
