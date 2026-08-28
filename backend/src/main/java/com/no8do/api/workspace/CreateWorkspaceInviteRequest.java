package com.no8do.api.workspace;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateWorkspaceInviteRequest(@NotBlank @Email String email, @NotNull WorkspaceInviteRole role) {
    public CreateWorkspaceInviteRequest {
        email = email == null ? null : email.trim();
    }
}
