package com.no8do.api.agent;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentSessionContextService {
    private final AgentSessionRepository repository;
    private final AgentSessionContextResolver contextResolver;
    private final AgentEventFactory eventFactory;
    private final AgentEventPublisher eventPublisher;

    public AgentSessionContextService(AgentSessionRepository repository,
            AgentSessionContextResolver contextResolver, AgentEventFactory eventFactory,
            AgentEventPublisher eventPublisher) {
        this.repository = repository;
        this.contextResolver = contextResolver;
        this.eventFactory = eventFactory;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public AgentSessionContextResponse getContext(UUID sessionId, UUID authenticatedUserId) {
        return AgentSessionContextResponse.from(contextResolver.resolve(sessionId, authenticatedUserId));
    }

    @Transactional
    public AgentSessionContextResponse updateRuntimeMode(UUID sessionId, UUID authenticatedUserId,
            AgentRuntimeMode runtimeMode) {
        AgentSession session = contextResolver.resolve(sessionId, authenticatedUserId).session();
        AgentRuntimeMode previousMode = session.getRuntimeMode();
        if (previousMode == runtimeMode) {
            return AgentSessionContextResponse.from(contextResolver.resolve(sessionId, authenticatedUserId));
        }
        session.setRuntimeMode(runtimeMode);
        repository.save(session);
        eventPublisher.publish(eventFactory.runtimeModeChanged(session, previousMode, runtimeMode));
        return AgentSessionContextResponse.from(contextResolver.resolve(sessionId, authenticatedUserId));
    }
}
