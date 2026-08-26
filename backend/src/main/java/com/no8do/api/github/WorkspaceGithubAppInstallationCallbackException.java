package com.no8do.api.github;

import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

public class WorkspaceGithubAppInstallationCallbackException extends ResponseStatusException {

    private final UUID workspaceId;

    public WorkspaceGithubAppInstallationCallbackException(UUID workspaceId, ResponseStatusException cause) {
        super(cause.getStatusCode(), cause.getReason(), cause);
        this.workspaceId = workspaceId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }
}
