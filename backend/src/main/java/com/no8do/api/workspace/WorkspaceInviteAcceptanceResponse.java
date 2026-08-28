package com.no8do.api.workspace;

import java.util.UUID;

public record WorkspaceInviteAcceptanceResponse(UUID workspaceId, String workspaceName, WorkspaceRole role) {}
