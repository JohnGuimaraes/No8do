package com.no8do.api.replay;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReplayVersionRepository extends JpaRepository<ReplayVersion, UUID> {
    List<ReplayVersion> findByReplayIdOrderByVersionDesc(UUID replayId);

    Optional<ReplayVersion> findByReplayIdAndVersion(UUID replayId, int version);

    @Query("""
            select replayVersion from ReplayVersion replayVersion
            join replayVersion.replay replay
            where replayVersion.workspace.id = :workspaceId
              and replay.workspace.id = :workspaceId
              and replayVersion.version = replay.version
              and replayVersion.status in :statuses
            order by replayVersion.createdAt asc, replayVersion.id asc
            """)
    Page<ReplayVersion> findEligibleCurrentByWorkspaceId(
            @Param("workspaceId") UUID workspaceId,
            @Param("statuses") List<ReplayStatus> statuses,
            Pageable pageable);
}
