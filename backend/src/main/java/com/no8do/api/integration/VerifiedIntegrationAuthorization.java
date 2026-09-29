package com.no8do.api.integration;

import java.util.UUID;

public record VerifiedIntegrationAuthorization(UUID authorizationId, UUID authorizedByUserId,
        UUID agentId, UUID workspaceId) {}
