package com.no8do.api.idea;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaRepository extends JpaRepository<Idea, UUID> {

    Optional<Idea> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Idea> findByWorkspaceIdAndStatusNotOrderByUpdatedAtDesc(UUID workspaceId, IdeaStatus status);

    List<Idea> findByWorkspaceIdAndStatusOrderByUpdatedAtDesc(UUID workspaceId, IdeaStatus status);

    Optional<Idea> findByConvertedProjectId(UUID projectId);
    void deleteByWorkspaceId(UUID workspaceId);
}
