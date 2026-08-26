package com.no8do.api.activity;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceProjectActivityResponse(
        UUID activityId,
        UUID projectId,
        String projectName,
        ProjectActivityType type,
        String content,
        String createdByName,
        Instant createdAt
) {

    static WorkspaceProjectActivityResponse from(ProjectActivity activity) {
        return new WorkspaceProjectActivityResponse(
            activity.getId(),
            activity.getProject().getId(),
            activity.getProject().getName(),
            activity.getType(),
            activity.getContent(),
            activity.getCreatedBy().getName(),
            activity.getCreatedAt()
        );
    }
}
