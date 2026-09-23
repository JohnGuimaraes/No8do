package com.no8do.api.agent;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class AgentSessionContextResolver {
    public static final String REQUEST_ATTRIBUTE = AgentSessionContextResolver.class.getName() + ".context";

    private final AgentSessionRepository repository;
    private final No8doAgentProtocolProvider protocolProvider;
    private final AgentEffectiveCapabilityResolver capabilityResolver;

    public AgentSessionContextResolver(AgentSessionRepository repository,
            No8doAgentProtocolProvider protocolProvider,
            AgentEffectiveCapabilityResolver capabilityResolver) {
        this.repository = repository;
        this.protocolProvider = protocolProvider;
        this.capabilityResolver = capabilityResolver;
    }

    public AgentSessionContext resolve(UUID sessionId, UUID authenticatedUserId) {
        AgentSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found"));
        if (!session.getUserId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent session access denied");
        }
        return new AgentSessionContext(session,
                capabilityResolver.resolve(protocolProvider.current(), session.getRuntimeMode()));
    }
}
