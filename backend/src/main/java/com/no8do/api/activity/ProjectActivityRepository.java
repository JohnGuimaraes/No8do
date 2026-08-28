package com.no8do.api.activity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface ProjectActivityRepository extends JpaRepository<ProjectActivity, UUID> {

    List<ProjectActivity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @Query("""
        select activity
        from ProjectActivity activity
        join fetch activity.project project
        left join fetch activity.createdBy
        where project.workspace.id = :workspaceId
        order by activity.createdAt desc
        """)
    List<ProjectActivity> findRecentByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    void deleteByProjectId(UUID projectId);
}
