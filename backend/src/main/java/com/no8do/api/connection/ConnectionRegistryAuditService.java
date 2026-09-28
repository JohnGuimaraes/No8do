package com.no8do.api.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConnectionRegistryAuditService {

    private final ConnectionRegistryAuditRepository repository;
    private final ObjectMapper objectMapper;

    public ConnectionRegistryAuditService(ConnectionRegistryAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreated(UUID actorUserId, Connection connection, Instant occurredAt) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("provider", connection.getProvider());
        metadata.put("credentialReferenceType", connection.getCredentialReferenceType().name());
        record(ConnectionRegistryAuditEventType.CONNECTION_CREATED, actorUserId, connection, occurredAt, metadata);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUpdated(UUID actorUserId, Connection connection, Instant occurredAt, List<String> changedFields) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.set("changedFields", objectMapper.valueToTree(changedFields));
        record(ConnectionRegistryAuditEventType.CONNECTION_UPDATED, actorUserId, connection, occurredAt, metadata);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDisconnected(UUID actorUserId, Connection connection, Instant occurredAt) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("status", ConnectionStatus.DISCONNECTED.name());
        record(ConnectionRegistryAuditEventType.CONNECTION_DISCONNECTED, actorUserId, connection, occurredAt, metadata);
    }

    private void record(ConnectionRegistryAuditEventType eventType, UUID actorUserId, Connection connection,
            Instant occurredAt, ObjectNode metadata) {
        repository.saveAndFlush(new ConnectionRegistryAuditEntry(UUID.randomUUID(), UUID.randomUUID(), eventType,
                actorUserId, connection.getWorkspace().getId(), connection.getId(), occurredAt, metadata));
    }
}
