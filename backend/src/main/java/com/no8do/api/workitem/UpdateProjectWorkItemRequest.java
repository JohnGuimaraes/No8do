package com.no8do.api.workitem;
import java.time.LocalDate; import java.util.UUID;
public record UpdateProjectWorkItemRequest(ProjectWorkItemType type, String title, String details, UUID assigneeUserId, LocalDate dueDate) {}
