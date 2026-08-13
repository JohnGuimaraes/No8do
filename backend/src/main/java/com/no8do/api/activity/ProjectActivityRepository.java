package com.no8do.api.activity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectActivityRepository extends JpaRepository<ProjectActivity, UUID> {

    List<ProjectActivity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
