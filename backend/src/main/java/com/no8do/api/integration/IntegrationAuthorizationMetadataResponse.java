package com.no8do.api.integration;

import java.time.Instant;
import java.util.UUID;

public record IntegrationAuthorizationMetadataResponse(
        UUID id,
        UUID agentId,
        String agentName,
        UUID installationId,
        IntegrationHostType hostType,
        String displayLabel,
        String integrationVersion,
        IntegrationAuthorizationStatus status,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt
) {
    static IntegrationAuthorizationMetadataResponse from(IntegrationAuthorization value, Instant now) {
        IntegrationAuthorizationStatus effectiveStatus = value.getStatus();
        if (effectiveStatus == IntegrationAuthorizationStatus.ACTIVE && !now.isBefore(value.getExpiresAt())) {
            effectiveStatus = IntegrationAuthorizationStatus.EXPIRED;
        }
        return new IntegrationAuthorizationMetadataResponse(value.getId(), value.getAgent().getId(),
                value.getAgent().getName(), value.getInstallationId(), value.getHostType(), value.getDisplayLabel(),
                value.getIntegrationVersion(), effectiveStatus, value.getCreatedAt(), value.getLastUsedAt(),
                value.getExpiresAt(), value.getRevokedAt());
    }
}
