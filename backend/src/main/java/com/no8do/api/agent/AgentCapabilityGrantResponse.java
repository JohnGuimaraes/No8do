package com.no8do.api.agent;

import java.time.Instant;

/** Administrative capability grant projection without entity or actor internals. */
public record AgentCapabilityGrantResponse(String capability, String description, boolean readOnly,
        Instant grantedAt) {
    static AgentCapabilityGrantResponse from(AgentCapabilityGrant grant) {
        AgentCapability capability = grant.getCapability();
        return new AgentCapabilityGrantResponse(capability.id(), capability.description(), capability.readOnly(),
                grant.getGrantedAt());
    }
}
