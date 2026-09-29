package com.no8do.api.agent;

/** Unambiguous authenticated snapshot read for internal Integration/MCP reconciliation. */
public record AgentOperationalContextStateResponse(boolean exists, AgentOperationalContextResponse context) {
    public AgentOperationalContextStateResponse {
        if (exists != (context != null)) throw new IllegalArgumentException("Operational Context state is inconsistent.");
    }

    static AgentOperationalContextStateResponse absent() {
        return new AgentOperationalContextStateResponse(false, null);
    }

    static AgentOperationalContextStateResponse present(AgentOperationalContextResponse context) {
        return new AgentOperationalContextStateResponse(true, context);
    }
}
