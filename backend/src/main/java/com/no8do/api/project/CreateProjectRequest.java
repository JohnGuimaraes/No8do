package com.no8do.api.project;

public record CreateProjectRequest(
        String name,
        String description,
        ProjectStatus status,
        String currentState
) {
}
