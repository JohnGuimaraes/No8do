package com.no8do.api.agent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Whitelisted activity projection; metadata is constructed from safe fields, never returned raw. */
public record AgentActivityResponse(String eventType, Instant occurredAt, UUID sessionId,
        Map<String, Object> metadata) {
    public AgentActivityResponse {
        metadata = Map.copyOf(metadata);
    }
}
