package com.no8do.api.github;

import java.util.List;

public record GithubAppRepositoryPreviewResponse(
        GithubAppRepositoryResponse repository,
        String readme,
        List<String> rootFiles,
        List<GithubAppRepositoryStack> detectedStacks) {
}
