package com.no8do.api.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRegistryAuditEntryRepository extends JpaRepository<AgentRegistryAuditEntry, UUID> {

    List<AgentRegistryAuditEntry> findByAgentIdOrderByOccurredAtAscIdAsc(UUID agentId);

    List<AgentRegistryAuditEntry> findByAgentIdAndEventTypeIn(UUID agentId,
            List<AgentRegistryAuditEventType> eventTypes, Pageable pageable);
}
