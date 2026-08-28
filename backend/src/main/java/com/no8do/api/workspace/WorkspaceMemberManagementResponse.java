package com.no8do.api.workspace;

import java.util.UUID;

public record WorkspaceMemberManagementResponse(UUID userId, String name, String email, WorkspaceRole role) {
    static WorkspaceMemberManagementResponse from(WorkspaceMember member) {
        return new WorkspaceMemberManagementResponse(member.getUser().getId(), member.getUser().getName(), member.getUser().getEmail(), member.getRole());
    }
}
