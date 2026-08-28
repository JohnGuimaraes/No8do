package com.no8do.api.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceInvitePublicResponse(UUID workspaceId, String workspaceName, String email, WorkspaceInviteRole role, Instant expiresAt, String status) {}
