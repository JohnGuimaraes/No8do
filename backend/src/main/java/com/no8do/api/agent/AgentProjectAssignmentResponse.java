package com.no8do.api.agent;

import com.no8do.api.project.ProjectStatus;
import java.time.Instant;
import java.util.UUID;

/** Safe summary of a Project associated with an Agent; excludes Project content and credentials. */
public record AgentProjectAssignmentResponse(UUID projectId, String name, ProjectStatus status,
        Instant archivedAt, Instant assignedAt) {}
