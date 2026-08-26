package com.no8do.api.project;

public record UpdateProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String currentState,
        String repositoryUrl
) {

    public UpdateProjectRequest(String name, String description, ProjectStatus status, String currentState) {
        this(name, description, status, currentState, null);
    }
}
