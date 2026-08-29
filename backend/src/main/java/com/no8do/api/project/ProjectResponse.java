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
        String repositoryUrl,
        UUID createdBy,
        String createdByName,
        boolean hasCover,
        Instant coverUpdatedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
            project.getId(),
            project.getWorkspace().getId(),
            project.getName(),
            project.getDescription(),
            project.getStatus(),
            project.getCurrentState(),
            project.getRepositoryUrl(),
            project.getCreatedBy() == null ? null : project.getCreatedBy().getId(),
            project.getCreatedBy() == null ? "Usuário excluído" : project.getCreatedBy().getName(),
            project.getCoverImageKey() != null,
            project.getCoverImageUpdatedAt(),
            project.getArchivedAt(),
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }
}
