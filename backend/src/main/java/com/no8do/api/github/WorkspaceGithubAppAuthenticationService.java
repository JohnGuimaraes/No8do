package com.no8do.api.github;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceGithubAppAuthenticationService {

    private final WorkspaceGithubAppInstallationRepository installationRepository;
    private final GithubAppClient githubAppClient;

    public WorkspaceGithubAppAuthenticationService(WorkspaceGithubAppInstallationRepository installationRepository,
            GithubAppClient githubAppClient) {
        this.installationRepository = installationRepository;
        this.githubAppClient = githubAppClient;
    }

    public GithubAppInstallationAccessToken accessTokenForWorkspace(UUID workspaceId) {
        WorkspaceGithubAppInstallation installation = installationRepository.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "GitHub App is not installed for this workspace"));
        return githubAppClient.createInstallationAccessToken(installation.getInstallationId());
    }
}
