package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

/** Safe, non-persisted operational projection for the Agent Hub. */
public record AgentOverviewResponse(
        UUID agentId,
        String name,
        AgentLifecycleStatus lifecycleStatus,
        AgentPresenceStatus operationalPresence,
        long totalSessions,
        long activeSessions,
        Instant lastSeenAt,
        Instant lastActivityAt,
        Instant lastRegisteredAt) {}
