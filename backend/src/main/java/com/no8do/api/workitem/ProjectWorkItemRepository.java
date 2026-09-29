package com.no8do.api.workitem;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectWorkItemRepository extends JpaRepository<ProjectWorkItem, UUID> {

    @Query("""
        select item
        from ProjectWorkItem item
        where item.project.id = :projectId
        order by
            case when item.status = com.no8do.api.workitem.ProjectWorkItemStatus.OPEN then 0 else 1 end,
            item.updatedAt desc
        """)
    List<ProjectWorkItem> findByProjectIdForProjectView(@Param("projectId") UUID projectId);

    @Query("""
        select item
        from ProjectWorkItem item
        join fetch item.project project
        left join fetch item.createdBy
        where project.workspace.id = :workspaceId
            and project.archivedAt is null
            and (:status is null or item.status = :status)
            and (:type is null or item.type = :type)
        order by
            case item.type
                when com.no8do.api.workitem.ProjectWorkItemType.BLOCKER then 0
                when com.no8do.api.workitem.ProjectWorkItemType.PENDING then 1
                else 2
            end,
            item.updatedAt desc
        """)
    List<ProjectWorkItem> findByWorkspaceIdForWorkspaceView(
        @Param("workspaceId") UUID workspaceId,
        @Param("status") ProjectWorkItemStatus status,
        @Param("type") ProjectWorkItemType type
    );

    Optional<ProjectWorkItem> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ProjectWorkItem> findByIdAndProject_Workspace_Id(UUID id, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select item
        from ProjectWorkItem item
        join item.project project
        where item.id = :workItemId
            and project.workspace.id = :workspaceId
        """)
    Optional<ProjectWorkItem> findScopedForAgentAssignment(@Param("workItemId") UUID workItemId,
            @Param("workspaceId") UUID workspaceId);

    void deleteByProjectId(UUID projectId);
}
