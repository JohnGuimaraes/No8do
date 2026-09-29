package com.no8do.api.agent;

import java.util.List;

/** Advertises protocol integrations, not authorization capabilities or grants. */
public record AgentProtocolIntegrationManifest(List<AgentProtocolIntegrationExtension> extensions) {
    public AgentProtocolIntegrationManifest {
        extensions = List.copyOf(extensions);
    }
}
