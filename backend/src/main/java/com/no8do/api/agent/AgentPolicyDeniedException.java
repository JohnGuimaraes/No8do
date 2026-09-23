package com.no8do.api.agent;

public final class AgentPolicyDeniedException extends RuntimeException {
    private final java.util.UUID sessionId;
    private final AgentPolicyDecision decision;

    public AgentPolicyDeniedException(AgentSession session, AgentPolicyDecision decision) {
        super("AGENT_POLICY_DENIED");
        this.sessionId = session == null ? null : session.getId();
        this.decision = decision;
    }

    public java.util.UUID sessionId() { return sessionId; }
    public AgentPolicyDecision decision() { return decision; }
}
