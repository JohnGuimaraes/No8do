package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentAuditEntryResponse(UUID id, UUID eventId, AgentEventType eventType, UUID sessionId,
        UUID workspaceId, Instant occurredAt, AgentEventMetadata metadata, Instant recordedAt) {}
