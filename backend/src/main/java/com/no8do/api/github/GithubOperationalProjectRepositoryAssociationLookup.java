package com.no8do.api.github;

import com.no8do.api.agent.OperationalProjectRepositoryAssociationLookup;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GithubOperationalProjectRepositoryAssociationLookup implements OperationalProjectRepositoryAssociationLookup {
    private final ProjectGithubRepositoryRepository repository;

    public GithubOperationalProjectRepositoryAssociationLookup(ProjectGithubRepositoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<UUID> findProjectIds(UUID workspaceId, String providerRepositoryId) {
        try {
            long repositoryId = Long.parseLong(providerRepositoryId);
            if (repositoryId <= 0) return List.of();
            return repository.findProjectsByRepositoryIdAndWorkspaceId(repositoryId, workspaceId).stream()
                    .map(project -> project.getId()).toList();
        } catch (NumberFormatException malformed) {
            return List.of();
        }
    }
}
