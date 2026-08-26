package com.no8do.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceGithubAppAuthenticationServiceTests {

    @Test
    void requestsATokenUsingOnlyTheWorkspaceInstallationId() {
        UUID workspaceId = UUID.randomUUID();
        WorkspaceGithubAppInstallationRepository repository = mock(WorkspaceGithubAppInstallationRepository.class);
        GithubAppClient client = mock(GithubAppClient.class);
        WorkspaceGithubAppInstallation installation = mock(WorkspaceGithubAppInstallation.class);
        GithubAppInstallationAccessToken expected = new GithubAppInstallationAccessToken("temporary-token", Instant.parse("2030-01-01T00:00:00Z"));
        when(repository.findById(workspaceId)).thenReturn(Optional.of(installation));
        when(installation.getInstallationId()).thenReturn(42L);
        when(client.createInstallationAccessToken(42L)).thenReturn(expected);

        GithubAppInstallationAccessToken token = new WorkspaceGithubAppAuthenticationService(repository, client)
            .accessTokenForWorkspace(workspaceId);

        assertThat(token).isEqualTo(expected);
        verify(client).createInstallationAccessToken(42L);
    }
}
