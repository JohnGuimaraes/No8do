package com.no8do.api.workitem;

import java.time.Instant;
import java.util.UUID;

public record ProjectWorkItemResponse(
        UUID id,
        UUID projectId,
        ProjectWorkItemType type,
        ProjectWorkItemStatus status,
        String title,
        String details,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {

    static ProjectWorkItemResponse from(ProjectWorkItem item) {
        return new ProjectWorkItemResponse(
            item.getId(),
            item.getProject().getId(),
            item.getType(),
            item.getStatus(),
            item.getTitle(),
            item.getDetails(),
            item.getCreatedBy().getId(),
            item.getCreatedBy().getName(),
            item.getCreatedAt(),
            item.getUpdatedAt(),
            item.getCompletedAt()
        );
    }
}
