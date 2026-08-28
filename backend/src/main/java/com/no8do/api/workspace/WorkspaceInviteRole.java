package com.no8do.api.workspace;

public enum WorkspaceInviteRole {
    ADMIN,
    VIEWER;

    WorkspaceRole toWorkspaceRole() {
        return WorkspaceRole.valueOf(name());
    }
}
