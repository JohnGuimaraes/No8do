package com.no8do.api.note;

import java.time.Instant;
import java.util.UUID;

public record ProjectNoteResponse(
        UUID id,
        UUID projectId,
        UUID createdBy,
        String createdByName,
        String content,
        ProjectNoteType type,
        Instant createdAt,
        Instant updatedAt
) {

    static ProjectNoteResponse from(ProjectNote note) {
        return new ProjectNoteResponse(
            note.getId(),
            note.getProject().getId(),
            note.getCreatedBy() == null ? null : note.getCreatedBy().getId(),
            note.getCreatedBy() == null ? "Usuário excluído" : note.getCreatedBy().getName(),
            note.getContent(),
            note.getType(), note.getCreatedAt(), note.getUpdatedAt()
        );
    }
}
