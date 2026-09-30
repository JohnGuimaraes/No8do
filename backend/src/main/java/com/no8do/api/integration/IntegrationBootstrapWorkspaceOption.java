package com.no8do.api.integration;

import com.no8do.api.workspace.WorkspaceRole;
import java.util.UUID;

public record IntegrationBootstrapWorkspaceOption(UUID id, String name, WorkspaceRole role) {}
