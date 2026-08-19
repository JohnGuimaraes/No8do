package com.no8do.api.note;

import java.time.Instant;
import java.util.UUID;

public record ProjectNoteResponse(
        UUID id,
        UUID projectId,
        UUID createdBy,
        String createdByName,
        String content,
        Instant createdAt
) {

    static ProjectNoteResponse from(ProjectNote note) {
        return new ProjectNoteResponse(
            note.getId(),
            note.getProject().getId(),
            note.getCreatedBy().getId(),
            note.getCreatedBy().getName(),
            note.getContent(),
            note.getCreatedAt()
        );
    }
}
