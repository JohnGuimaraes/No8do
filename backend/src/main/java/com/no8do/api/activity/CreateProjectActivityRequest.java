package com.no8do.api.activity;

public record CreateProjectActivityRequest(
        String content,
        ProjectActivityType type
) {
}
