package com.no8do.api.github;

import java.time.Instant;

public record WorkspaceGithubAppInstallationResponse(
        boolean installed,
        Long installationId,
        Long accountId,
        String accountLogin,
        GithubAppInstallationAccountType accountType,
        Instant configuredAt
) {

    public static WorkspaceGithubAppInstallationResponse notInstalled() {
        return new WorkspaceGithubAppInstallationResponse(false, null, null, null, null, null);
    }

    public static WorkspaceGithubAppInstallationResponse from(WorkspaceGithubAppInstallation installation) {
        return new WorkspaceGithubAppInstallationResponse(
            true,
            installation.getInstallationId(),
            installation.getAccountId(),
            installation.getAccountLogin(),
            installation.getAccountType(),
            installation.getConfiguredAt()
        );
    }
}
