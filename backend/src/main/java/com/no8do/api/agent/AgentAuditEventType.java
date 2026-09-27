package com.no8do.api.agent;

/** Persisted audit types; deliberately separate from realtime AgentEventType. */
public enum AgentAuditEventType {
    AGENT_CONNECTED,
    AGENT_DISCONNECTED,
    RUNTIME_MODE_CHANGED,
    CAPABILITY_DENIED,
    POLICY_DENIED,
    REPLAY_USAGE_RECORDED,
    AGENT_SESSION_REVOKED,
    AGENT_SESSION_BOUND
}
