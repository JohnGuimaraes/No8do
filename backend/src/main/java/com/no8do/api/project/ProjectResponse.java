package com.no8do.api.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String description,
        ProjectStatus status,
        String currentState,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {

    static ProjectResponse from(Project project) {
        return new ProjectResponse(
            project.getId(),
            project.getWorkspace().getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getCurrentState(),
            project.getCreatedBy().getId(),
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }
}
