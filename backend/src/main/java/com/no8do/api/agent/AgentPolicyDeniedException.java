package com.no8do.api.agent;

public final class AgentPolicyDeniedException extends RuntimeException {
    private final AgentSession session;
    private final AgentPolicyDecision decision;

    public AgentPolicyDeniedException(AgentSession session, AgentPolicyDecision decision) {
        super("AGENT_POLICY_DENIED");
        this.session = session;
        this.decision = decision;
    }

    public AgentSession session() { return session; }
    public AgentPolicyDecision decision() { return decision; }
}
