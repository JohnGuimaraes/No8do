package com.no8do.api.replay;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplayRelationRepository extends JpaRepository<ReplayRelation, UUID> {
    List<ReplayRelation> findByWorkspaceIdAndSourceReplayIdOrWorkspaceIdAndTargetReplayIdOrderByCreatedAtDesc(UUID sourceWorkspaceId, UUID sourceReplayId, UUID targetWorkspaceId, UUID targetReplayId);
    boolean existsByWorkspaceIdAndSourceReplayIdAndTargetReplayIdAndType(UUID workspaceId, UUID sourceReplayId, UUID targetReplayId, ReplayRelationType type);
    Optional<ReplayRelation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
