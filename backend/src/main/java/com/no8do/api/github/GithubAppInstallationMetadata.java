package com.no8do.api.github;

public record GithubAppInstallationMetadata(
        long installationId,
        long accountId,
        String accountLogin,
        GithubAppInstallationAccountType accountType
) {
}
