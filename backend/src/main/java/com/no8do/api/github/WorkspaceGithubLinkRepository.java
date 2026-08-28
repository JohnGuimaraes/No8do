package com.no8do.api.github;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceGithubLinkRepository extends JpaRepository<WorkspaceGithubLink, UUID> {

    Optional<WorkspaceGithubLink> findByWorkspaceId(UUID workspaceId);

    void deleteByWorkspaceId(UUID workspaceId);
}
