package com.no8do.api.activity;

import java.time.Instant;
import java.util.UUID;

public record ProjectActivityResponse(
        UUID id,
        UUID projectId,
        UUID createdBy,
        ProjectActivityType type,
        String content,
        Instant createdAt
) {

    static ProjectActivityResponse from(ProjectActivity activity) {
        return new ProjectActivityResponse(
            activity.getId(),
            activity.getProject().getId(),
            activity.getCreatedBy().getId(),
            activity.getType(),
            activity.getContent(),
            activity.getCreatedAt()
        );
    }
}
