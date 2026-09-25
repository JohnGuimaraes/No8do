package com.no8do.api.agent;

import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Revocation core shared by the authenticated API and session governance services. */
@Service
public class AgentSessionRevocationService {
    private final AgentSessionRepository repository;
    private final AgentAuditTrailService auditTrailService;
    private final AgentEventFactory eventFactory;
    private final AgentEventPublisher eventPublisher;
    private final AgentGatewayMetrics metrics;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final Clock clock;

    public AgentSessionRevocationService(AgentSessionRepository repository,
            AgentAuditTrailService auditTrailService,
            AgentEventFactory eventFactory,
            AgentEventPublisher eventPublisher,
            AgentGatewayMetrics metrics,
            WorkspaceAuthorizationService workspaceAuthorizationService, Clock clock) {
        this.repository = repository;
        this.auditTrailService = auditTrailService;
        this.eventFactory = eventFactory;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.clock = clock;
    }

    @Transactional
    public AgentSessionRevocationResult revoke(UUID sessionId, UUID actorUserId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        AgentSession session = repository.findByIdForUpdate(sessionId)
                .orElseThrow(AgentSessionRevocationService::notFound);
        requireAuthority(session, actorUserId);

        boolean newlyRevoked = session.revoke(clock.instant().truncatedTo(ChronoUnit.MICROS), actorUserId);
        if (newlyRevoked) {
            repository.save(session);
            auditTrailService.recordRevocation(session.getId(), session.getUserId(), actorUserId,
                    session.getWorkspaceId(), session.getRevokedAt());
            eventPublisher.publish(eventFactory.sessionRevoked(session));
            metrics.sessionRevokedAfterCommit();
        }
        return new AgentSessionRevocationResult(session.getRevokedAt(), session.getRevokedByUserId(), newlyRevoked);
    }

    private void requireAuthority(AgentSession session, UUID actorUserId) {
        if (session.getWorkspaceId() == null) {
            if (!session.getUserId().equals(actorUserId)) throw notFound();
            return;
        }
        try {
            workspaceAuthorizationService.requireWorkspaceManager(session.getWorkspaceId(), actorUserId);
        } catch (ResponseStatusException denied) {
            if (denied.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) throw notFound();
            throw denied;
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found");
    }
}
