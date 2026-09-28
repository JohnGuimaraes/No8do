package com.no8do.api.agent;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Applies persistent Agent grants as an additional ceiling after the existing protocol/runtime resolver. */
@Component
public final class AgentPersistentCapabilityResolver {
    private final AgentEffectiveCapabilityResolver effectiveCapabilityResolver;
    private final AgentCapabilityGrantRepository grantRepository;

    public AgentPersistentCapabilityResolver(AgentEffectiveCapabilityResolver effectiveCapabilityResolver,
            AgentCapabilityGrantRepository grantRepository) {
        this.effectiveCapabilityResolver = effectiveCapabilityResolver;
        this.grantRepository = grantRepository;
    }

    public List<AgentCapability> resolve(AgentSession session, No8doAgentProtocol protocol) {
        List<AgentCapability> runtimeAllowed = effectiveCapabilityResolver.resolve(protocol, session.getRuntimeMode());
        if (session.getAgent() == null) return runtimeAllowed;

        Set<AgentCapability> granted = grantRepository.findCapabilitiesByAgentId(session.getAgent().getId());
        return runtimeAllowed.stream().filter(granted::contains).toList();
    }
}
