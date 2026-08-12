package com.no8do.api.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Project> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Project> findByWorkspaceId(UUID workspaceId);
}
