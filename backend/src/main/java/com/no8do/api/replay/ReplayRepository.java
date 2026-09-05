package com.no8do.api.replay;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReplayRepository extends JpaRepository<Replay, UUID> {

    List<Replay> findByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId);

    Optional<Replay> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    void deleteByWorkspaceId(UUID workspaceId);

    @Query(value = """
        select * from replays r
        where r.workspace_id = :workspaceId
          and (
            r.title ilike concat('%', :query, '%')
            or coalesce(r.problem, '') ilike concat('%', :query, '%')
            or coalesce(r.solution, '') ilike concat('%', :query, '%')
            or coalesce(r.context, '') ilike concat('%', :query, '%')
            or array_to_string(r.tags, ' ') ilike concat('%', :query, '%')
            or array_to_string(r.stack, ' ') ilike concat('%', :query, '%')
          )
        order by r.updated_at desc
        """, nativeQuery = true)
    List<Replay> searchByWorkspaceId(@Param("workspaceId") UUID workspaceId, @Param("query") String query);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        update replays
           set usage_count = usage_count + 1,
               success_count = success_count + :successIncrement,
               failure_count = failure_count + :failureIncrement,
               last_used_at = :usedAt
         where id = :replayId
           and workspace_id = :workspaceId
        """, nativeQuery = true)
    int incrementUsageMetrics(@Param("workspaceId") UUID workspaceId, @Param("replayId") UUID replayId,
            @Param("successIncrement") int successIncrement, @Param("failureIncrement") int failureIncrement,
            @Param("usedAt") Instant usedAt);
}
