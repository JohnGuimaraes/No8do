package com.no8do.api.agent;

public final class AgentCapabilityDeniedException extends RuntimeException {
    private final AgentSession session;
    private final AgentCapability requiredCapability;

    public AgentCapabilityDeniedException(AgentSession session, AgentCapability requiredCapability) {
        super("AGENT_CAPABILITY_DENIED");
        this.session = session;
        this.requiredCapability = requiredCapability;
    }

    public AgentSession session() { return session; }
    public AgentCapability requiredCapability() { return requiredCapability; }
}
