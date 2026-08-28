package com.no8do.api.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterWorkspaceInviteRequest(@NotBlank String name, @NotBlank @Size(min = 8) String password) {}
