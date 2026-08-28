package com.no8do.api.idea;

import java.time.Instant;
import java.util.UUID;

public record IdeaResponse(
        UUID id,
        UUID workspaceId,
        String title,
        String description,
        IdeaType type,
        IdeaStatus status,
        UUID convertedProjectId,
        String convertedProjectName,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {

    static IdeaResponse from(Idea idea) {
        return new IdeaResponse(
            idea.getId(),
            idea.getWorkspace().getId(),
            idea.getTitle(),
            idea.getDescription(),
            idea.getType(),
            idea.getStatus(),
            idea.getConvertedProject() == null ? null : idea.getConvertedProject().getId(),
            idea.getConvertedProject() == null ? null : idea.getConvertedProject().getName(),
            idea.getCreatedBy().getId(),
            idea.getCreatedBy().getName(),
            idea.getCreatedAt(),
            idea.getUpdatedAt()
        );
    }
}
