package com.no8do.api.agent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentProjectAssignmentRepository extends JpaRepository<AgentProjectAssignment, UUID> {

    @Query("""
        select new com.no8do.api.agent.AgentProjectAssignmentResponse(
            project.id, project.name, project.status, project.archivedAt, assignment.assignedAt)
        from AgentProjectAssignment assignment
        join assignment.agent agent
        join assignment.project project
        where agent.id = :agentId
            and agent.workspace.id = :workspaceId
            and project.workspace.id = :workspaceId
        order by assignment.assignedAt desc, project.id asc
        """)
    List<AgentProjectAssignmentResponse> findProjectSummaries(@Param("workspaceId") UUID workspaceId,
            @Param("agentId") UUID agentId);

    @Modifying
    @Query(value = """
        insert into agent_project_assignments (id, agent_id, project_id, assigned_at, assigned_by_user_id)
        values (:id, :agentId, :projectId, :assignedAt, :assignedByUserId)
        on conflict (agent_id, project_id) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("agentId") UUID agentId,
            @Param("projectId") UUID projectId, @Param("assignedAt") Instant assignedAt,
            @Param("assignedByUserId") UUID assignedByUserId);

    @Modifying
    @Query(value = "delete from agent_project_assignments where agent_id = :agentId and project_id = :projectId",
            nativeQuery = true)
    int deleteAssignment(@Param("agentId") UUID agentId, @Param("projectId") UUID projectId);

    Optional<AgentProjectAssignment> findByAgent_IdAndProject_Id(UUID agentId, UUID projectId);

    long countByAgent_Id(UUID agentId);
}
