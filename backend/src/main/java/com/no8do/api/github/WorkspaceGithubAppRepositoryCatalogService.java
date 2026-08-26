package com.no8do.api.github;

import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceGithubAppRepositoryCatalogService {

    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceGithubAppAuthenticationService authenticationService;
    private final GithubAppClient githubAppClient;

    public WorkspaceGithubAppRepositoryCatalogService(WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceGithubAppAuthenticationService authenticationService, GithubAppClient githubAppClient) {
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.authenticationService = authenticationService;
        this.githubAppClient = githubAppClient;
    }

    @Transactional(readOnly = true)
    public GithubAppRepositoryPageResponse list(UUID workspaceId, UUID currentUserId, int page, int perPage) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        validatePage(page, perPage);
        GithubAppInstallationAccessToken accessToken = authenticationService.accessTokenForWorkspace(workspaceId);
        return githubAppClient.listInstallationRepositories(accessToken, page, perPage);
    }

    @Transactional(readOnly = true)
    public GithubAppRepositoryPreviewResponse preview(UUID workspaceId, UUID currentUserId, long repositoryId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        if (repositoryId <= 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository id is invalid");
        GithubAppInstallationAccessToken accessToken = authenticationService.accessTokenForWorkspace(workspaceId);
        return githubAppClient.previewInstallationRepository(accessToken, repositoryId);
    }

    private void validatePage(int page, int perPage) {
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository pagination is invalid");
        }
    }
}
