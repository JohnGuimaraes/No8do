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

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCredentialCreated(UUID actorUserId, Agent agent, AgentCredential credential, Instant occurredAt) {
        ObjectNode metadata = credentialMetadata(credential, "CREATED", AgentCredentialStatus.ACTIVE.name());
        record(AgentRegistryAuditEventType.AGENT_CREDENTIAL_CREATED, actorUserId, agent, occurredAt, metadata);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCredentialRevoked(UUID actorUserId, Agent agent, AgentCredential credential,
            Instant occurredAt, String reason) {
        ObjectNode metadata = credentialMetadata(credential, "REVOKED", AgentCredentialStatus.REVOKED.name());
        metadata.put("reason", reason);
        record(AgentRegistryAuditEventType.AGENT_CREDENTIAL_REVOKED, actorUserId, agent, occurredAt, metadata);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCredentialRotated(UUID actorUserId, Agent agent, AgentCredential previous,
            AgentCredential replacement, Instant occurredAt) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("operation", "ROTATED");
        metadata.put("status", AgentCredentialStatus.ACTIVE.name());
        metadata.put("credentialId", replacement.getId().toString());
        metadata.put("publicCredentialId", replacement.getPublicCredentialId());
        metadata.put("replacedCredentialId", previous.getId().toString());
        metadata.put("replacedPublicCredentialId", previous.getPublicCredentialId());
        record(AgentRegistryAuditEventType.AGENT_CREDENTIAL_ROTATED, actorUserId, agent, occurredAt, metadata);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordProjectAssigned(UUID actorUserId, Agent agent, UUID projectId, Instant assignedAt) {
        recordProjectAssignment(AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED, actorUserId, agent,
                projectId, assignedAt, "assignedAt");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordProjectUnassigned(UUID actorUserId, Agent agent, UUID projectId, Instant unassignedAt) {
        recordProjectAssignment(AgentRegistryAuditEventType.AGENT_PROJECT_UNASSIGNED, actorUserId, agent,
                projectId, unassignedAt, "unassignedAt");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordConnectionAssigned(UUID actorUserId, Agent agent, UUID connectionId, Instant assignedAt) {
        recordConnectionAssignment(AgentRegistryAuditEventType.AGENT_CONNECTION_ASSIGNED, actorUserId, agent,
                connectionId, assignedAt, "assignedAt");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordConnectionUnassigned(UUID actorUserId, Agent agent, UUID connectionId, Instant unassignedAt) {
        recordConnectionAssignment(AgentRegistryAuditEventType.AGENT_CONNECTION_UNASSIGNED, actorUserId, agent,
                connectionId, unassignedAt, "unassignedAt");
    }

    private void recordProjectAssignment(AgentRegistryAuditEventType eventType, UUID actorUserId, Agent agent,
            UUID projectId, Instant occurredAt, String timestampField) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("agentId", agent.getId().toString());
        metadata.put("projectId", projectId.toString());
        metadata.put("actorUserId", actorUserId.toString());
        metadata.put("workspaceId", agent.getWorkspace().getId().toString());
        metadata.put(timestampField, occurredAt.toString());
        record(eventType, actorUserId, agent, occurredAt, metadata);
    }

    private void recordConnectionAssignment(AgentRegistryAuditEventType eventType, UUID actorUserId, Agent agent,
            UUID connectionId, Instant occurredAt, String timestampField) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("agentId", agent.getId().toString());
        metadata.put("connectionId", connectionId.toString());
        metadata.put("actorUserId", actorUserId.toString());
        metadata.put("workspaceId", agent.getWorkspace().getId().toString());
        metadata.put(timestampField, occurredAt.toString());
        record(eventType, actorUserId, agent, occurredAt, metadata);
    }

    private ObjectNode credentialMetadata(AgentCredential credential, String operation, String status) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("operation", operation);
        metadata.put("status", status);
        metadata.put("credentialId", credential.getId().toString());
        metadata.put("publicCredentialId", credential.getPublicCredentialId());
        return metadata;
    }

    private void record(AgentRegistryAuditEventType eventType, UUID actorUserId, Agent agent,
            Instant occurredAt, ObjectNode metadata) {
        repository.saveAndFlush(new AgentRegistryAuditEntry(UUID.randomUUID(), UUID.randomUUID(), eventType,
                actorUserId, agent.getWorkspace().getId(), agent.getId(), occurredAt, metadata));
    }
}
