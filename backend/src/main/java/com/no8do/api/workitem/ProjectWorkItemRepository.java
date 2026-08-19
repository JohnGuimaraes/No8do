package com.no8do.api.workitem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<ProjectWorkItem> findByIdAndProjectId(UUID id, UUID projectId);
}
