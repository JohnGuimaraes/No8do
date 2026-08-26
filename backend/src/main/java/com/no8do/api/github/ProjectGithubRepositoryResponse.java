package com.no8do.api.github;

public record ProjectGithubRepositoryResponse(
        ProjectGithubRepositoryState state,
        Long repositoryId,
        GithubAppRepositoryResponse repository
) {
    static ProjectGithubRepositoryResponse notAssociated() {
        return new ProjectGithubRepositoryResponse(ProjectGithubRepositoryState.NOT_ASSOCIATED, null, null);
    }

    static ProjectGithubRepositoryResponse inaccessible(long repositoryId) {
        return new ProjectGithubRepositoryResponse(ProjectGithubRepositoryState.INACCESSIBLE, repositoryId, null);
    }

    static ProjectGithubRepositoryResponse associated(GithubAppRepositoryResponse repository) {
        return new ProjectGithubRepositoryResponse(ProjectGithubRepositoryState.ASSOCIATED, repository.repositoryId(), repository);
    }
}
