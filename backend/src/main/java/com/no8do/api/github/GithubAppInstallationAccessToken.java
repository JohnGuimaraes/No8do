package com.no8do.api.github;

import java.time.Instant;

public record GithubAppInstallationAccessToken(String token, Instant expiresAt) {
}
