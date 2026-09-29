package com.no8do.api.agent;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentOperationalContextRepository extends JpaRepository<AgentOperationalContext, UUID> {
    @EntityGraph(attributePaths = "references")
    Optional<AgentOperationalContext> findWithReferencesBySessionId(UUID sessionId);
}
