package com.no8do.api.integration;

import com.no8do.api.agent.Agent;
import com.no8do.api.agent.AgentSessionRepository;
import com.no8do.api.agent.AgentSessionRevocationService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal coordinator; callers hold the Agent lock and enforce human workspace authority. */
@Service
public class IntegrationAuthorizationLifecycleService {
    private final IntegrationAuthorizationRepository authorizations;
    private final IntegrationAuthorizationAuditService audit;
    private final AgentSessionRepository sessions;
    private final AgentSessionRevocationService revocations;

    public IntegrationAuthorizationLifecycleService(IntegrationAuthorizationRepository authorizations,
            IntegrationAuthorizationAuditService audit, AgentSessionRepository sessions,
            AgentSessionRevocationService revocations) {
        this.authorizations = authorizations;
        this.audit = audit;
        this.sessions = sessions;
        this.revocations = revocations;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(IntegrationAuthorization authorization, UUID actorUserId, Instant at, String reason) {
        if (!authorization.revoke(at, reason)) return;
        authorizations.saveAndFlush(authorization);
        Agent agent = authorization.getAgent();
        audit.record(IntegrationAuthorizationAuditEventType.INTEGRATION_AUTHORIZATION_REVOKED,
                actorUserId, agent.getWorkspace().getId(), agent.getId(), null, authorization.getId(), at, "REVOKED");
        for (var session : sessions.findUnrevokedIntegrationSessionsForUpdate(
                authorization.getId(), agent.getWorkspace().getId())) {
            revocations.revoke(session.getId(), actorUserId);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeActiveForArchivedAgent(UUID actorUserId, Agent agent, Instant at) {
        for (var authorization : authorizations.findActiveForAgentForUpdate(agent.getId())) {
            revoke(authorization, actorUserId, at, "AGENT_ARCHIVED");
        }
    }
}
