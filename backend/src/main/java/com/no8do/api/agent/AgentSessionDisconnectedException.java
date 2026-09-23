package com.no8do.api.agent;

import java.util.UUID;

public final class AgentSessionDisconnectedException extends RuntimeException {
    private final UUID sessionId;

    public AgentSessionDisconnectedException(UUID sessionId) {
        super("AGENT_SESSION_DISCONNECTED");
        this.sessionId = sessionId;
    }

    public UUID sessionId() { return sessionId; }
}
