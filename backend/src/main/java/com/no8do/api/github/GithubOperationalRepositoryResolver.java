package com.no8do.api.github;

import com.no8do.api.agent.OperationalRepositoryLocator;
import com.no8do.api.agent.OperationalRepositoryResolver;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GithubOperationalRepositoryResolver implements OperationalRepositoryResolver {
    private final WorkspaceGithubAppAuthenticationService authenticationService;
    private final GithubAppClient githubAppClient;

    public GithubOperationalRepositoryResolver(WorkspaceGithubAppAuthenticationService authenticationService,
            GithubAppClient githubAppClient) {
        this.authenticationService = authenticationService;
        this.githubAppClient = githubAppClient;
    }

    @Override
    public RepositoryIdentityResolution resolve(OperationalRepositoryLocator repository, UUID workspaceId) {
        if (repository == null || !"GIT".equals(repository.vcs()) || !"github".equals(repository.provider())
                || !"github.com".equals(repository.host())) return RepositoryIdentityResolution.unsupported();
        String owner = repository.namespace();
        if (owner == null || owner.contains("/")) return RepositoryIdentityResolution.unsupported();
        try {
            GithubAppInstallationAccessToken token = authenticationService.accessTokenForWorkspace(workspaceId);
            GithubAppRepositoryResponse response = githubAppClient.getInstallationRepositoryByFullName(
                    token, owner, repository.name());
            if (response == null || response.repositoryId() <= 0
                    || !equalsLocator(response.ownerLogin(), owner)
                    || !equalsLocator(response.name(), repository.name())
                    || !equalsLocator(response.fullName(), owner + "/" + repository.name())) {
                throw invalidExternalResponse();
            }
            return new RepositoryIdentityResolution(true, true, Long.toString(response.repositoryId()));
        } catch (ResponseStatusException failure) {
            if (failure.getStatusCode() == HttpStatus.NOT_FOUND) return RepositoryIdentityResolution.inaccessible();
            throw failure;
        }
    }

    private static boolean equalsLocator(String actual, String expected) {
        return actual != null && expected != null && actual.toLowerCase(Locale.ROOT).equals(expected.toLowerCase(Locale.ROOT));
    }

    private static ResponseStatusException invalidExternalResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repository identity could not be verified");
    }
}
