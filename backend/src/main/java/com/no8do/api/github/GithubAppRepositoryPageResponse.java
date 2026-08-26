package com.no8do.api.github;

import java.util.List;

public record GithubAppRepositoryPageResponse(
        List<GithubAppRepositoryResponse> items,
        int page,
        int perPage,
        int totalCount) {
}
