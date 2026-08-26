package com.no8do.api.github;

import jakarta.validation.constraints.Positive;

public record UpdateProjectGithubRepositoryRequest(@Positive long repositoryId) {
}
