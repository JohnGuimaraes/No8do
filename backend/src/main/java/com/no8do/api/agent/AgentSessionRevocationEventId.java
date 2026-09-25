package com.no8do.api.agent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable id shared by the mandatory audit row and the later after-commit event. */
final class AgentSessionRevocationEventId {
    private static final String PREFIX = "no8do:agent-session-revoked:";

    private AgentSessionRevocationEventId() {}

    static UUID forSession(UUID sessionId) {
        return UUID.nameUUIDFromBytes((PREFIX + sessionId).getBytes(StandardCharsets.UTF_8));
    }
}
