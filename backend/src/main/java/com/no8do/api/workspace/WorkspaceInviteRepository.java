package com.no8do.api.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, UUID> {
    Optional<WorkspaceInvite> findByTokenHash(String tokenHash);
    List<WorkspaceInvite> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    void deleteByWorkspaceId(UUID workspaceId);
}
