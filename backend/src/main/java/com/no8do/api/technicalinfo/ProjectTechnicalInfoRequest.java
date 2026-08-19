package com.no8do.api.technicalinfo;

public record ProjectTechnicalInfoRequest(
        String repositoryUrl,
        String stack,
        String productionUrl,
        String developmentUrl,
        String localPath,
        String runCommand
) {
}
