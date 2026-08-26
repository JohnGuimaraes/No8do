package com.no8do.api.github;

import java.time.Instant;

public record UserGithubConnectionResponse(boolean connected, String login, String avatarUrl, Instant connectedAt) {

    public static UserGithubConnectionResponse disconnected() {
        return new UserGithubConnectionResponse(false, null, null, null);
    }

    public static UserGithubConnectionResponse from(UserGithubConnection connection) {
        return new UserGithubConnectionResponse(true, connection.getGithubLogin(), connection.getAvatarUrl(), connection.getConnectedAt());
    }
}
