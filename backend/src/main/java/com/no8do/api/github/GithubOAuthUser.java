package com.no8do.api.github;

public record GithubOAuthUser(long id, String login, String avatarUrl, String accessToken) {
}
