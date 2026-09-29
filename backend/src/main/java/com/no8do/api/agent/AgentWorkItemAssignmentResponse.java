package com.no8do.api.agent;

import com.no8do.api.workitem.ProjectWorkItemStatus;
import com.no8do.api.workitem.ProjectWorkItemType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Safe WorkItem summary for an Agent; excludes details and assignment provenance. */
public record AgentWorkItemAssignmentResponse(UUID workItemId, UUID projectId, String projectName,
        ProjectWorkItemType type, ProjectWorkItemStatus status, String title, LocalDate dueDate, Instant assignedAt) {}
