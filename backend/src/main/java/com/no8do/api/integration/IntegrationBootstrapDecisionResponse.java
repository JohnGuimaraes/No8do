package com.no8do.api.integration;

import java.util.UUID;

public record IntegrationBootstrapDecisionResponse(IntegrationBootstrapState state, UUID requestId,
        UUID workspaceId, UUID agentId) {}
