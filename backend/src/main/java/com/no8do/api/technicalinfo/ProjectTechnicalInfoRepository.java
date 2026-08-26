package com.no8do.api.technicalinfo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectTechnicalInfoRepository extends JpaRepository<ProjectTechnicalInfo, UUID> {

    long countByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);
}
