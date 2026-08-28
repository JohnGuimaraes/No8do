package com.no8do.api.credential;

import java.time.Instant;
import java.util.UUID;

public record ProjectCredentialResponse(
        UUID id,
        UUID projectId,
        String label,
        ProjectCredentialType type,
        String username,
        String notes,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {

    static ProjectCredentialResponse from(ProjectCredential credential) {
        return new ProjectCredentialResponse(
            credential.getId(),
            credential.getProject().getId(),
            credential.getLabel(),
            credential.getType(),
            credential.getUsername(),
            credential.getNotes(),
            credential.getCreatedBy() == null ? null : credential.getCreatedBy().getId(),
            credential.getCreatedBy() == null ? "Usuário excluído" : credential.getCreatedBy().getName(),
            credential.getCreatedAt(),
            credential.getUpdatedAt()
        );
    }
}
