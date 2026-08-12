package com.no8do.api.project;

public record UpdateProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String currentState
) {
}
