package com.no8do.api.agent;

import java.time.Clock;
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
    private final AgentPresenceResolver presenceResolver;
    private final AgentPresenceProperties presenceProperties;
    private final Clock clock;

    public AgentSessionContextResolver(AgentSessionRepository repository,
            No8doAgentProtocolProvider protocolProvider,
            AgentEffectiveCapabilityResolver capabilityResolver,
            AgentPresenceProperties presenceProperties, Clock clock) {
        this.repository = repository;
        this.protocolProvider = protocolProvider;
        this.capabilityResolver = capabilityResolver;
        this.presenceResolver = new AgentPresenceResolver();
        this.presenceProperties = presenceProperties;
        this.clock = clock;
    }

    public AgentSessionContext resolve(UUID sessionId, UUID authenticatedUserId) {
        AgentSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found"));
        if (!session.getUserId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent session access denied");
        }
        No8doAgentProtocol protocol = protocolProvider.current();
        return new AgentSessionContext(session,
                capabilityResolver.resolve(protocol, session.getRuntimeMode()), protocol.policies(),
                presenceResolver.resolve(session.getRegisteredAt(), session.getLastSeenAt(), session.getLastActivityAt(),
                        session.getDisconnectedAt(), clock.instant(), presenceProperties), session.getLastSeenAt(),
                session.getLastActivityAt(), session.getDisconnectedAt());
    }
}
