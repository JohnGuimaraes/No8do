package com.no8do.api.workitem;

import java.time.LocalDate; import java.util.UUID;
public record CreateProjectWorkItemRequest(
        ProjectWorkItemType type,
        String title,
        String details, UUID assigneeUserId, LocalDate dueDate
) {
    public CreateProjectWorkItemRequest(ProjectWorkItemType type, String title, String details) { this(type, title, details, null, null); }
}
