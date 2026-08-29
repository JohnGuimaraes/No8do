package com.no8do.api.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<WorkspaceMember> findByUserId(UUID userId);
    boolean existsByUserIdAndRole(UUID userId, WorkspaceRole role);
    @EntityGraph(attributePaths = "user")
    List<WorkspaceMember> findByWorkspaceIdOrderByUserNameAsc(UUID workspaceId);
    void deleteByWorkspaceId(UUID workspaceId);
}
