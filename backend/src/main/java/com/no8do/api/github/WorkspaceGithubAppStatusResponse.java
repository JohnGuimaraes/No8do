package com.no8do.api.github;

public record WorkspaceGithubAppStatusResponse(
        boolean configured,
        String accountLogin,
        GithubAppInstallationAccountType accountType
) {

    public static WorkspaceGithubAppStatusResponse notConfigured() {
        return new WorkspaceGithubAppStatusResponse(false, null, null);
    }

    public static WorkspaceGithubAppStatusResponse from(WorkspaceGithubAppInstallation installation) {
        return new WorkspaceGithubAppStatusResponse(
            true,
            installation.getAccountLogin(),
            installation.getAccountType()
        );
    }
}
