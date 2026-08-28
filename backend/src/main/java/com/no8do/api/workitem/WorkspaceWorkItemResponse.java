package com.no8do.api.workitem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkspaceWorkItemResponse(
        UUID id,
        UUID projectId,
        String projectName,
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

    static WorkspaceWorkItemResponse from(ProjectWorkItem item) {
        return new WorkspaceWorkItemResponse(
            item.getId(),
            item.getProject().getId(),
            item.getProject().getName(),
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
