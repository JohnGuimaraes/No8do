package com.no8do.api.agent;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentSessionPresenceService {
    private final AgentSessionRepository repository;
    private final Clock clock;
    private final AgentEventFactory eventFactory;
    private final AgentEventPublisher eventPublisher;
    private final AgentGatewayMetrics metrics;

    public AgentSessionPresenceService(AgentSessionRepository repository, Clock clock,
            AgentEventFactory eventFactory, AgentEventPublisher eventPublisher, AgentGatewayMetrics metrics) {
        this.repository = repository;
        this.clock = clock;
        this.eventFactory = eventFactory;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    @Transactional
    public Instant heartbeat(UUID sessionId, UUID authenticatedUserId) {
        requireConnected(requireOwned(sessionId, authenticatedUserId));
        Instant now = clock.instant();
        if (repository.updateLastSeenAt(sessionId, authenticatedUserId, now) != 1) {
            requireConnected(requireOwned(sessionId, authenticatedUserId));
            throw notFound();
        }
        metrics.heartbeatAcceptedAfterCommit();
        return now;
    }

    @Transactional
    public void touchActivity(UUID sessionId, UUID authenticatedUserId) {
        requireConnected(requireOwned(sessionId, authenticatedUserId));
        Instant now = clock.instant();
        if (repository.updateActivityTimestamps(sessionId, authenticatedUserId, now) != 1) {
            requireConnected(requireOwned(sessionId, authenticatedUserId));
            throw notFound();
        }
    }

    @Transactional
    public Instant disconnect(UUID sessionId, UUID authenticatedUserId) {
        AgentSession session = requireOwned(sessionId, authenticatedUserId);
        if (session.getDisconnectedAt() != null) return session.getDisconnectedAt();
        Instant now = clock.instant();
        if (repository.updateDisconnectedAtIfAbsent(sessionId, authenticatedUserId, now) == 1) {
            metrics.sessionDisconnectedAfterCommit();
            eventPublisher.publish(eventFactory.disconnected(session));
            return now;
        }
        session = requireOwned(sessionId, authenticatedUserId);
        if (session.getDisconnectedAt() != null) return session.getDisconnectedAt();
        throw notFound();
    }

    private AgentSession requireOwned(UUID sessionId, UUID authenticatedUserId) {
        AgentSession session = repository.findById(sessionId).orElseThrow(AgentSessionPresenceService::notFound);
        if (!session.getUserId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent session access denied");
        }
        return session;
    }

    private static void requireConnected(AgentSession session) {
        if (session.getDisconnectedAt() != null) throw new AgentSessionDisconnectedException(session.getId());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found");
    }
}
