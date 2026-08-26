package com.no8do.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WorkspaceGithubAppRepositoryCatalogServiceTests {

    @Test
    void allowsWorkspaceMembersToReadRepositoriesFromTheInstallation() {
        UUID workspaceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        WorkspaceAuthorizationService authorizationService = mock(WorkspaceAuthorizationService.class);
        WorkspaceGithubAppAuthenticationService authenticationService = mock(WorkspaceGithubAppAuthenticationService.class);
        GithubAppClient githubAppClient = mock(GithubAppClient.class);
        GithubAppInstallationAccessToken token = new GithubAppInstallationAccessToken("temporary", Instant.parse("2030-01-01T00:00:00Z"));
        GithubAppRepositoryPageResponse expected = new GithubAppRepositoryPageResponse(List.of(repository()), 2, 25, 1);
        when(authenticationService.accessTokenForWorkspace(workspaceId)).thenReturn(token);
        when(githubAppClient.listInstallationRepositories(token, 2, 25)).thenReturn(expected);

        GithubAppRepositoryPageResponse result = new WorkspaceGithubAppRepositoryCatalogService(
            authorizationService, authenticationService, githubAppClient
        ).list(workspaceId, memberId, 2, 25);

        assertThat(result).isEqualTo(expected);
        verify(authorizationService).requireWorkspaceMember(workspaceId, memberId);
        verify(authenticationService).accessTokenForWorkspace(workspaceId);
        verify(githubAppClient).listInstallationRepositories(token, 2, 25);
    }

    @Test
    void preventsUsersOutsideTheWorkspaceFromObtainingAnInstallationToken() {
        UUID workspaceId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        WorkspaceAuthorizationService authorizationService = mock(WorkspaceAuthorizationService.class);
        WorkspaceGithubAppAuthenticationService authenticationService = mock(WorkspaceGithubAppAuthenticationService.class);
        GithubAppClient githubAppClient = mock(GithubAppClient.class);
        ResponseStatusException denied = new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace access denied");
        when(authorizationService.requireWorkspaceMember(workspaceId, outsiderId)).thenThrow(denied);

        WorkspaceGithubAppRepositoryCatalogService service = new WorkspaceGithubAppRepositoryCatalogService(
            authorizationService, authenticationService, githubAppClient
        );

        assertThatThrownBy(() -> service.list(workspaceId, outsiderId, 1, 30)).isSameAs(denied);
        verifyNoInteractions(authenticationService, githubAppClient);
    }

    @Test
    void reportsWhenTheWorkspaceHasNoGithubAppInstallation() {
        UUID workspaceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        WorkspaceAuthorizationService authorizationService = mock(WorkspaceAuthorizationService.class);
        WorkspaceGithubAppAuthenticationService authenticationService = mock(WorkspaceGithubAppAuthenticationService.class);
        GithubAppClient githubAppClient = mock(GithubAppClient.class);
        ResponseStatusException missing = new ResponseStatusException(HttpStatus.CONFLICT, "GitHub App is not installed for this workspace");
        when(authenticationService.accessTokenForWorkspace(workspaceId)).thenThrow(missing);

        WorkspaceGithubAppRepositoryCatalogService service = new WorkspaceGithubAppRepositoryCatalogService(
            authorizationService, authenticationService, githubAppClient
        );

        assertThatThrownBy(() -> service.list(workspaceId, memberId, 1, 30)).isSameAs(missing);
        verifyNoInteractions(githubAppClient);
    }

    private GithubAppRepositoryResponse repository() {
        return new GithubAppRepositoryResponse(1L, "repository", "octo/repository", "octo", false,
            false, false, "https://github.com/octo/repository", null, "main", "Java", Instant.parse("2026-08-24T12:00:00Z"), List.of());
    }
}
