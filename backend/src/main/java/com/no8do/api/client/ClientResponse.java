package com.no8do.api.client;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String companyName,
        String email,
        String phone,
        String notes,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    static ClientResponse from(Client client) {
        return new ClientResponse(
            client.getId(),
            client.getWorkspace().getId(),
            client.getName(),
            client.getCompanyName(),
            client.getEmail(),
            client.getPhone(),
            client.getNotes(),
            client.getCreatedBy().getId(),
            client.getCreatedAt(),
            client.getUpdatedAt()
        );
    }
}
