package com.no8do.api.workitem;

public record CreateProjectWorkItemRequest(
        ProjectWorkItemType type,
        String title,
        String details
) {
}
