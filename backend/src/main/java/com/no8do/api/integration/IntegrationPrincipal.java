package com.no8do.api.integration;

import java.util.UUID;

/** Runtime identity for one verified Integration Authorization. */
public record IntegrationPrincipal(UUID integrationAuthorizationId, UUID agentId, UUID workspaceId,
        UUID grantorUserId) {}
