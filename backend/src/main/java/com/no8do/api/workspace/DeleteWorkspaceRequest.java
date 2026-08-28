package com.no8do.api.workspace;

import jakarta.validation.constraints.NotBlank;

public record DeleteWorkspaceRequest(@NotBlank String confirmationName) { }
