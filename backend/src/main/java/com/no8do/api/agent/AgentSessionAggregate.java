package com.no8do.api.agent;

import java.time.Instant;

/** Query projection for Agent overview; presence values are derived from current session timestamps. */
public record AgentSessionAggregate(
        long totalSessions,
        long operationalSessions,
        long activePresenceSessions,
        long connectedPresenceSessions,
        Instant lastSeenAt,
        Instant lastActivityAt,
        Instant lastRegisteredAt) {}
