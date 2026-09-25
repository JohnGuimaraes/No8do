package com.no8do.api.agent;

public final class AgentSessionRevokedException extends RuntimeException {
    public AgentSessionRevokedException() {
        super("AGENT_SESSION_REVOKED");
    }
}
