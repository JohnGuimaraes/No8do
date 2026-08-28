package com.no8do.api.workitem;

import java.time.Instant;
import java.time.LocalDate;
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
        UUID assigneeUserId, String assigneeName, LocalDate dueDate,
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
            item.getAssignee() == null ? null : item.getAssignee().getId(), item.getAssignee() == null ? null : item.getAssignee().getName(), item.getDueDate(),
            item.getCreatedAt(),
            item.getUpdatedAt(),
            item.getCompletedAt()
        );
    }
}
