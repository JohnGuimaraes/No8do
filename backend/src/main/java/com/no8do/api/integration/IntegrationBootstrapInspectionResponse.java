package com.no8do.api.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IntegrationBootstrapInspectionResponse(
        UUID requestId,
        IntegrationHostType hostType,
        String displayLabel,
        String integrationVersion,
        Instant expiresAt,
        IntegrationBootstrapState state,
        List<IntegrationBootstrapWorkspaceOption> eligibleWorkspaces
) {}
