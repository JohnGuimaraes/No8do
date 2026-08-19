package com.no8do.api.credential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCredentialRepository extends JpaRepository<ProjectCredential, UUID> {

    List<ProjectCredential> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    Optional<ProjectCredential> findByIdAndProjectId(UUID id, UUID projectId);
}
