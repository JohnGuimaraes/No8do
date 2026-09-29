package com.no8do.api.github;

import com.no8do.api.project.Project;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectGithubRepositoryRepository extends JpaRepository<ProjectGithubRepository, UUID> {

    void deleteByProjectId(UUID projectId);

    @Query("select association.project from ProjectGithubRepository association "
            + "where association.repositoryId = :repositoryId and association.project.workspace.id = :workspaceId")
    List<Project> findProjectsByRepositoryIdAndWorkspaceId(@Param("repositoryId") long repositoryId,
            @Param("workspaceId") UUID workspaceId);
}
