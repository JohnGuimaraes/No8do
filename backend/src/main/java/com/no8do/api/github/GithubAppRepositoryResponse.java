package com.no8do.api.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record GithubAppRepositoryResponse(
        long repositoryId,
        String name,
        String fullName,
        String ownerLogin,
        @JsonProperty("private") boolean privateRepository,
        boolean fork,
        boolean archived,
        String htmlUrl,
        String description,
        String defaultBranch,
        String language,
        Instant updatedAt,
        List<String> topics) {
}
