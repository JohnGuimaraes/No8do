package com.no8do.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationAuthorizationAuditService {
    private final IntegrationAuthorizationAuditRepository repository;
    private final ObjectMapper objectMapper;
    public IntegrationAuthorizationAuditService(IntegrationAuthorizationAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository; this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(IntegrationAuthorizationAuditEventType type, UUID actorUserId, UUID workspaceId,
            UUID agentId, UUID requestId, UUID authorizationId, Instant occurredAt, String outcome) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("outcome", outcome);
        repository.saveAndFlush(new IntegrationAuthorizationAuditEntry(UUID.randomUUID(), type, actorUserId,
                workspaceId, agentId, requestId, authorizationId, occurredAt, metadata));
    }
}
