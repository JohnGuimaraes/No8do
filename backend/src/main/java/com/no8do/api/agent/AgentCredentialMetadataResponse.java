package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentCredentialMetadataResponse(
        UUID id,
        String publicCredentialId,
        AgentCredentialStatus status,
        Instant createdAt,
        Instant revokedAt
) {
    static AgentCredentialMetadataResponse from(AgentCredential credential) {
        return new AgentCredentialMetadataResponse(credential.getId(), credential.getPublicCredentialId(),
                credential.getStatus(), credential.getCreatedAt(), credential.getRevokedAt());
    }
}
