package com.no8do.api.agent;

/** Versioned MCP extension contract for infrastructure used by the No8do Integration. */
public record AgentProtocolIntegrationExtension(String id, int version,
        OperationalContextIntegrationContract operationalContext) {
    public AgentProtocolIntegrationExtension {
        if (id == null || id.isBlank() || version <= 0 || operationalContext == null) {
            throw new IllegalArgumentException("Integration extension contract is invalid.");
        }
    }
}
