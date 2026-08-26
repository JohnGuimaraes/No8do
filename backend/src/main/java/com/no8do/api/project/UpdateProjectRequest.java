package com.no8do.api.project;

import java.util.UUID;

public record UpdateProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String currentState,
        UUID clientId,
        String repositoryUrl
) {

    public UpdateProjectRequest(String name, String description, ProjectStatus status, String currentState) {
        this(name, description, status, currentState, null, null);
    }

    public UpdateProjectRequest(String name, String description, ProjectStatus status, String currentState, UUID clientId) {
        this(name, description, status, currentState, clientId, null);
    }
}
