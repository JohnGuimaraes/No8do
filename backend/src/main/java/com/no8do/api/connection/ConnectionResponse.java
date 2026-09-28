package com.no8do.api.connection;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record ConnectionResponse(
        UUID id,
        UUID workspaceId,
        String provider,
        String name,
        ConnectionStatus status,
        ConnectionCredentialReferenceType credentialReferenceType,
        JsonNode metadata,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant disconnectedAt
) {

    static ConnectionResponse from(Connection connection) {
        return new ConnectionResponse(connection.getId(), connection.getWorkspace().getId(),
                connection.getProvider(), connection.getName(), connection.getStatus(),
                connection.getCredentialReferenceType(), connection.getMetadata().deepCopy(),
                connection.getCreatedByUser() == null ? null : connection.getCreatedByUser().getId(),
                connection.getCreatedAt(), connection.getUpdatedAt(), connection.getDisconnectedAt());
    }
}
