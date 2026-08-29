package com.no8do.api.workspace;

import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceMemberRequest(@NotNull WorkspaceRole role) {
}
