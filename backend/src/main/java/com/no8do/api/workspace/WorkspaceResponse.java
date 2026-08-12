package com.no8do.api.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        WorkspaceRole role,
        Instant createdAt,
        Instant updatedAt
) {

    static WorkspaceResponse from(WorkspaceMember member) {
        Workspace workspace = member.getWorkspace();
        return new WorkspaceResponse(
            workspace.getId(),
            workspace.getName(),
            member.getRole(),
            workspace.getCreatedAt(),
            workspace.getUpdatedAt()
        );
    }
}
