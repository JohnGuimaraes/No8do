package com.no8do.api.idea;

public record IdeaRequest(
        String title,
        String description,
        String type,
        String status
) {
}
