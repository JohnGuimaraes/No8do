package com.no8do.api.note;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectNoteRepository extends JpaRepository<ProjectNote, UUID> {

    List<ProjectNote> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    java.util.Optional<ProjectNote> findByIdAndProjectId(UUID id, UUID projectId);

    void deleteByProjectId(UUID projectId);
}
