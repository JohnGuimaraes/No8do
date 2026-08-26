package com.no8do.api.github;

import java.time.Instant;

public record WorkspaceGithubLinkResponse(boolean linked, String login, String avatarUrl, Instant linkedAt) {

    public static WorkspaceGithubLinkResponse unlinked() {
        return new WorkspaceGithubLinkResponse(false, null, null, null);
    }

    public static WorkspaceGithubLinkResponse from(WorkspaceGithubLink link) {
        UserGithubConnection connection = link.getGithubConnection();
        return new WorkspaceGithubLinkResponse(true, connection.getGithubLogin(), connection.getAvatarUrl(), link.getLinkedAt());
    }
}
