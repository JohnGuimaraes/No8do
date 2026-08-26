package com.no8do.api.project;

public record CreateProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String currentState,
        String repositoryUrl
) {

    public CreateProjectRequest(String name, String description, ProjectStatus status, String currentState) {
        this(name, description, status, currentState, null);
    }
}
