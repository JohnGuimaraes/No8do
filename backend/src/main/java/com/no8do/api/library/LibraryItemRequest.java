package com.no8do.api.library;

public record LibraryItemRequest(
        String type,
        String title,
        String description,
        String content,
        String url
) {
}
