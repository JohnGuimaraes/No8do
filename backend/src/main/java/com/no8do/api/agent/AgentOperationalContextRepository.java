package com.no8do.api.agent;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AgentOperationalContextRepository extends JpaRepository<AgentOperationalContext, UUID> {
    @EntityGraph(attributePaths = "references")
    Optional<AgentOperationalContext> findWithReferencesBySessionId(UUID sessionId);

    @Query(value = """
        select c.session_id from agent_session_operational_contexts c
        join agent_sessions s on s.id = c.session_id
        left join agents a on a.id = s.agent_id
        where c.repository_provider = 'github' and c.repository_host = 'github.com'
          and c.project_resolution_repository_id = :repositoryId
          and coalesce(a.workspace_id, s.workspace_id) = :workspaceId
        """, nativeQuery = true)
    List<UUID> findSessionIdsForRepositoryResolution(@Param("workspaceId") UUID workspaceId,
            @Param("repositoryId") String repositoryId);
}
