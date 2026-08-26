package com.no8do.api.note;

public record CreateProjectNoteRequest(String content, ProjectNoteType type) {
    public CreateProjectNoteRequest(String content) { this(content, ProjectNoteType.NOTE); }
}
