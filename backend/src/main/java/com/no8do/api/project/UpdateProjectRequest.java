package com.no8do.api.project;

import java.util.UUID;

public record UpdateProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String currentState,
        UUID clientId
) {

    public UpdateProjectRequest(String name, String description, ProjectStatus status, String currentState) {
        this(name, description, status, currentState, null);
    }
}
