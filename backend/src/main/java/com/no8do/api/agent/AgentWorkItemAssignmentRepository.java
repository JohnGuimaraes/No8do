package com.no8do.api.agent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentWorkItemAssignmentRepository extends JpaRepository<AgentWorkItemAssignment, UUID> {

    @Query("""
        select new com.no8do.api.agent.AgentWorkItemAssignmentResponse(
            workItem.id, project.id, project.name, workItem.type, workItem.status, workItem.title,
            workItem.dueDate, assignment.assignedAt)
        from AgentWorkItemAssignment assignment
        join assignment.agent agent
        join assignment.workItem workItem
        join workItem.project project
        where agent.id = :agentId
            and agent.workspace.id = :workspaceId
            and project.workspace.id = :workspaceId
        order by assignment.assignedAt desc, workItem.id asc
        """)
    List<AgentWorkItemAssignmentResponse> findWorkItemSummaries(@Param("workspaceId") UUID workspaceId,
            @Param("agentId") UUID agentId);

    @Modifying
    @Query(value = """
        insert into agent_work_item_assignments (id, agent_id, work_item_id, assigned_at, assigned_by_user_id)
        values (:id, :agentId, :workItemId, :assignedAt, :assignedByUserId)
        on conflict (agent_id, work_item_id) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("agentId") UUID agentId,
            @Param("workItemId") UUID workItemId, @Param("assignedAt") Instant assignedAt,
            @Param("assignedByUserId") UUID assignedByUserId);

    @Modifying
    @Query(value = "delete from agent_work_item_assignments where agent_id = :agentId and work_item_id = :workItemId",
            nativeQuery = true)
    int deleteAssignment(@Param("agentId") UUID agentId, @Param("workItemId") UUID workItemId);

    Optional<AgentWorkItemAssignment> findByAgent_IdAndWorkItem_Id(UUID agentId, UUID workItemId);

    long countByAgent_Id(UUID agentId);
}
