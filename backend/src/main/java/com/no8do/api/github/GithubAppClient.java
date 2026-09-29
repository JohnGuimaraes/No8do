package com.no8do.api.github;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public interface GithubAppClient {

    String installationUrl(String state);

    GithubAppInstallationMetadata exchangeCodeAndFindInstallation(String code, long installationId);

    GithubAppInstallationAccessToken createInstallationAccessToken(long installationId);

    GithubAppRepositoryPageResponse listInstallationRepositories(GithubAppInstallationAccessToken accessToken, int page, int perPage);

    GithubAppRepositoryResponse getInstallationRepository(GithubAppInstallationAccessToken accessToken, long repositoryId);

    GithubAppRepositoryResponse getInstallationRepositoryByFullName(
            GithubAppInstallationAccessToken accessToken, String owner, String repository);

    GithubAppRepositoryPreviewResponse previewInstallationRepository(GithubAppInstallationAccessToken accessToken, long repositoryId);

    static ResponseStatusException configurationMissing() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub App integration is not configured");
    }
}
