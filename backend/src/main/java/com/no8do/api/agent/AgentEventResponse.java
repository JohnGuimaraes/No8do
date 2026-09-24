package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentEventResponse(UUID eventId, AgentEventType type, UUID sessionId, UUID workspaceId,
        Instant occurredAt, AgentEventMetadata metadata) {
    public static AgentEventResponse from(AgentEvent event) {
        return new AgentEventResponse(event.eventId(), event.type(), event.sessionId(), event.workspaceId(),
                event.occurredAt(), event.metadata());
    }
}
