package com.no8do.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.no8do.api.agent.OperationalRepositoryLocator;
import com.no8do.api.agent.OperationalRepositoryResolver;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GithubOperationalRepositoryResolverTests {
    private final WorkspaceGithubAppAuthenticationService authentication = mock(WorkspaceGithubAppAuthenticationService.class);
    private final GithubAppClient client = mock(GithubAppClient.class);
    private final GithubOperationalRepositoryResolver resolver = new GithubOperationalRepositoryResolver(authentication, client);
    private final UUID workspaceId = UUID.randomUUID();
    private final GithubAppInstallationAccessToken token = new GithubAppInstallationAccessToken("secret-token",
            Instant.parse("2030-01-01T00:00:00Z"));

    @Test
    void unsupportedProviderOrHostNeverCallsGithubOrUsesTheUntrustedHost() {
        assertThat(resolver.resolve(repository("gitlab", "github.com", "owner", "repo"), workspaceId).supported()).isFalse();
        assertThat(resolver.resolve(repository("github", "github.example.test", "owner", "repo"), workspaceId).supported()).isFalse();
        assertThat(resolver.resolve(new OperationalRepositoryLocator("HG", "github", "github.com", "owner", "repo"),
                workspaceId).supported()).isFalse();
        verifyNoInteractions(authentication, client);
    }

    @Test
    void resolvesStableProviderIdUsingWorkspaceInstallationAndValidatesExternalLocator() {
        when(authentication.accessTokenForWorkspace(workspaceId)).thenReturn(token);
        when(client.getInstallationRepositoryByFullName(token, "Owner", "Repo"))
                .thenReturn(response(123456789L, "Owner", "Repo", "Owner/Repo"));

        OperationalRepositoryResolver.RepositoryIdentityResolution result = resolver.resolve(
                repository("github", "github.com", "Owner", "Repo"), workspaceId);

        assertThat(result.supported()).isTrue();
        assertThat(result.found()).isTrue();
        assertThat(result.providerRepositoryId()).isEqualTo("123456789");
        verify(authentication).accessTokenForWorkspace(workspaceId);
    }

    @Test
    void inaccessibleRepositoryIsNegativeButTransientFailuresAreNotConvertedToUnresolved() {
        when(authentication.accessTokenForWorkspace(workspaceId)).thenReturn(token);
        when(client.getInstallationRepositoryByFullName(token, "owner", "repo"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThat(resolver.resolve(repository("github", "github.com", "owner", "repo"), workspaceId).found()).isFalse();

        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY))
                .when(client).getInstallationRepositoryByFullName(token, "owner", "repo");
        assertThatThrownBy(() -> resolver.resolve(repository("github", "github.com", "owner", "repo"), workspaceId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(failure -> assertThat(((ResponseStatusException) failure).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void invalidIdOrLocatorFromGithubFailsClosed() {
        when(authentication.accessTokenForWorkspace(workspaceId)).thenReturn(token);
        when(client.getInstallationRepositoryByFullName(token, "owner", "repo"))
                .thenReturn(response(0, "owner", "repo", "owner/repo"));
        assertBadGateway();
        when(client.getInstallationRepositoryByFullName(token, "owner", "repo"))
                .thenReturn(response(123, "other", "repo", "other/repo"));
        assertBadGateway();
        when(client.getInstallationRepositoryByFullName(token, "owner", "repo"))
                .thenReturn(response(123, "owner", "repo", "owner/wrong"));
        assertBadGateway();
    }

    @Test
    void missingWorkspaceInstallationFailsClosedWithoutFallback() {
        when(authentication.accessTokenForWorkspace(workspaceId)).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "GitHub App is not installed"));
        assertThatThrownBy(() -> resolver.resolve(repository("github", "github.com", "owner", "repo"), workspaceId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(failure -> assertThat(((ResponseStatusException) failure).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(client);
    }

    private void assertBadGateway() {
        assertThatThrownBy(() -> resolver.resolve(repository("github", "github.com", "owner", "repo"), workspaceId))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private static OperationalRepositoryLocator repository(String provider, String host,
            String owner, String name) {
        return new OperationalRepositoryLocator("GIT", provider, host, owner, name);
    }

    private static GithubAppRepositoryResponse response(long id, String owner, String name, String fullName) {
        return new GithubAppRepositoryResponse(id, name, fullName, owner, true, false, false,
                "https://github.com/" + fullName, null, null, null, Instant.parse("2026-09-29T00:00:00Z"), List.of());
    }
}
