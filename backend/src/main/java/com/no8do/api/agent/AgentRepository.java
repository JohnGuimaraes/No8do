package com.no8do.api.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<Agent> findByWorkspaceIdOrderByUpdatedAtDescIdAsc(UUID workspaceId);
}
