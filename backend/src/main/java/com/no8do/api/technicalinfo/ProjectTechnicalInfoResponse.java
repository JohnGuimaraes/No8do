package com.no8do.api.technicalinfo;

import java.time.Instant;
import java.util.UUID;

public record ProjectTechnicalInfoResponse(
        UUID projectId,
        String repositoryUrl,
        String stack,
        String productionUrl,
        String developmentUrl,
        String localPath,
        String runCommand,
        Instant createdAt,
        Instant updatedAt
) {

    static ProjectTechnicalInfoResponse empty(UUID projectId, String repositoryUrl) {
        return new ProjectTechnicalInfoResponse(projectId, repositoryUrl, null, null, null, null, null, null, null);
    }

    static ProjectTechnicalInfoResponse from(ProjectTechnicalInfo info, String repositoryUrl) {
        return new ProjectTechnicalInfoResponse(
            info.getProjectId(),
            repositoryUrl,
            info.getStack(),
            info.getProductionUrl(),
            info.getDevelopmentUrl(),
            info.getLocalPath(),
            info.getRunCommand(),
            info.getCreatedAt(),
            info.getUpdatedAt()
        );
    }
}
