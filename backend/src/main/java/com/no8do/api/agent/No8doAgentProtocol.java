package com.no8do.api.agent;

/** Canonical, self-describing contract for agent interoperability with No8do. */
public record No8doAgentProtocol(String protocolName, int protocolVersion, String systemName,
        String purpose, ReplayAgentGuidance replayGuidance,
        AgentCapabilityManifest capabilities, AgentPolicyManifest policies,
        AgentProtocolIntegrationManifest integrationExtensions) {
    public No8doAgentProtocol {
        if (protocolName == null || protocolName.isBlank()) throw new IllegalArgumentException("protocolName é obrigatório.");
        if (protocolVersion <= 0) throw new IllegalArgumentException("protocolVersion deve ser positivo.");
        if (systemName == null || systemName.isBlank()) throw new IllegalArgumentException("systemName é obrigatório.");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose é obrigatório.");
        if (replayGuidance == null || capabilities == null || policies == null || integrationExtensions == null) {
            throw new IllegalArgumentException("guidance, capabilities, policies e integrationExtensions são obrigatórios.");
        }
    }

    public No8doAgentProtocol(String protocolName, int protocolVersion, String systemName,
            String purpose, ReplayAgentGuidance replayGuidance,
            AgentCapabilityManifest capabilities, AgentPolicyManifest policies) {
        this(protocolName, protocolVersion, systemName, purpose, replayGuidance, capabilities, policies,
                new AgentProtocolIntegrationManifest(java.util.List.of()));
    }
}
