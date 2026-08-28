package com.no8do.api.library;

import java.time.Instant;
import java.util.UUID;

public record LibraryItemResponse(
        UUID id,
        UUID workspaceId,
        LibraryItemType type,
        String title,
        String description,
        String content,
        String url,
        UUID createdBy,
        String createdByName,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {

    static LibraryItemResponse from(LibraryItem item) {
        return new LibraryItemResponse(
            item.getId(),
            item.getWorkspace().getId(),
            item.getType(),
            item.getTitle(),
            item.getDescription(),
            item.getContent(),
            item.getUrl(),
            item.getCreatedBy().getId(),
            item.getCreatedBy().getName(),
            item.getArchivedAt(),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }
}
