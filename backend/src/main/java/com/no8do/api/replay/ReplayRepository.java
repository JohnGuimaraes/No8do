package com.no8do.api.replay;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
