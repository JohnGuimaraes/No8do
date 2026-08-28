package com.no8do.api.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceInviteResponse(UUID id, String email, WorkspaceInviteRole role, Instant expiresAt, Instant acceptedAt, Instant revokedAt, Instant createdAt, String inviteUrl) {
    static WorkspaceInviteResponse from(WorkspaceInvite invite, String inviteUrl) {
        return new WorkspaceInviteResponse(invite.getId(), invite.getEmail(), invite.getRole(), invite.getExpiresAt(), invite.getAcceptedAt(), invite.getRevokedAt(), invite.getCreatedAt(), inviteUrl);
    }
}
