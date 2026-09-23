package com.no8do.api.agent;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentSessionContextService {
    private final AgentSessionRepository repository;
    private final AgentSessionContextResolver contextResolver;

    public AgentSessionContextService(AgentSessionRepository repository,
            AgentSessionContextResolver contextResolver) {
        this.repository = repository;
        this.contextResolver = contextResolver;
    }

    @Transactional(readOnly = true)
    public AgentSessionContextResponse getContext(UUID sessionId, UUID authenticatedUserId) {
        return AgentSessionContextResponse.from(contextResolver.resolve(sessionId, authenticatedUserId));
    }

    @Transactional
    public AgentSessionContextResponse updateRuntimeMode(UUID sessionId, UUID authenticatedUserId,
            AgentRuntimeMode runtimeMode) {
        AgentSession session = contextResolver.resolve(sessionId, authenticatedUserId).session();
        session.setRuntimeMode(runtimeMode);
        repository.save(session);
        return AgentSessionContextResponse.from(contextResolver.resolve(sessionId, authenticatedUserId));
    }
}
