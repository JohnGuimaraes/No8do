package com.no8do.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRegistryAuditService {

    private final AgentRegistryAuditEntryRepository repository;
    private final ObjectMapper objectMapper;

    public AgentRegistryAuditService(AgentRegistryAuditEntryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreated(UUID actorUserId, Agent agent, Instant occurredAt) {
        record(AgentRegistryAuditEventType.AGENT_CREATED, actorUserId, agent, occurredAt,
                objectMapper.createObjectNode());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUpdated(UUID actorUserId, Agent agent, Instant occurredAt, List<String> changedFields) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.set("changedFields", objectMapper.valueToTree(changedFields));
        record(AgentRegistryAuditEventType.AGENT_UPDATED, actorUserId, agent, occurredAt, metadata);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLifecycleChanged(UUID actorUserId, Agent agent, AgentLifecycleStatus previousStatus,
            Instant occurredAt) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("previousStatus", previousStatus.name());
        metadata.put("lifecycleStatus", agent.getLifecycleStatus().name());
        record(AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED, actorUserId, agent, occurredAt, metadata);
    }

    private void record(AgentRegistryAuditEventType eventType, UUID actorUserId, Agent agent,
            Instant occurredAt, ObjectNode metadata) {
        repository.saveAndFlush(new AgentRegistryAuditEntry(UUID.randomUUID(), UUID.randomUUID(), eventType,
                actorUserId, agent.getWorkspace().getId(), agent.getId(), occurredAt, metadata));
    }
}
