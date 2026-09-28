package com.no8do.api.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentConnectionAssignmentRepository extends JpaRepository<AgentConnectionAssignment, UUID> {

    @Query("""
        select new com.no8do.api.agent.AgentConnectionAssignmentResponse(
            connection.id, connection.name, connection.provider, connection.status, assignment.assignedAt)
        from AgentConnectionAssignment assignment
        join assignment.agent agent
        join assignment.connection connection
        where agent.id = :agentId
            and agent.workspace.id = :workspaceId
            and connection.workspace.id = :workspaceId
        order by assignment.assignedAt desc, connection.id asc
        """)
    List<AgentConnectionAssignmentResponse> findConnectionSummaries(@Param("workspaceId") UUID workspaceId,
            @Param("agentId") UUID agentId);

    @Modifying
    @Query(value = """
        insert into agent_connection_assignments (id, agent_id, connection_id, assigned_at, assigned_by_user_id)
        values (:id, :agentId, :connectionId, :assignedAt, :assignedByUserId)
        on conflict (agent_id, connection_id) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("agentId") UUID agentId,
            @Param("connectionId") UUID connectionId, @Param("assignedAt") Instant assignedAt,
            @Param("assignedByUserId") UUID assignedByUserId);

    @Modifying
    @Query(value = "delete from agent_connection_assignments where agent_id = :agentId and connection_id = :connectionId",
            nativeQuery = true)
    int deleteAssignment(@Param("agentId") UUID agentId, @Param("connectionId") UUID connectionId);

    long countByAgent_Id(UUID agentId);
}
