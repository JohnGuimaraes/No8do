package com.no8do.api.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Pure, deterministic resolver for derived AgentSession presence. */
public final class AgentPresenceResolver {
    public AgentPresenceStatus resolve(Instant registeredAt, Instant lastSeenAt, Instant lastActivityAt,
            Instant currentTime, AgentPresenceProperties thresholds) {
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(currentTime, "currentTime");
        Objects.requireNonNull(thresholds, "thresholds");
        Instant effectiveLastSeenAt = lastSeenAt == null ? registeredAt : lastSeenAt;
        if (Duration.between(effectiveLastSeenAt, currentTime).compareTo(thresholds.disconnectTimeout()) > 0) {
            return AgentPresenceStatus.DISCONNECTED;
        }
        if (lastActivityAt == null) return AgentPresenceStatus.CONNECTED;
        if (Duration.between(lastActivityAt, currentTime).compareTo(thresholds.activeWindow()) > 0) {
            return AgentPresenceStatus.IDLE;
        }
        return AgentPresenceStatus.ACTIVE;
    }
}
