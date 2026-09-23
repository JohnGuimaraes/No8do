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

    public AgentSessionPresenceService(AgentSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Instant heartbeat(UUID sessionId, UUID authenticatedUserId) {
        requireOwned(sessionId, authenticatedUserId);
        Instant now = clock.instant();
        if (repository.updateLastSeenAt(sessionId, authenticatedUserId, now) != 1) throw notFound();
        return now;
    }

    @Transactional
    public void touchActivity(UUID sessionId, UUID authenticatedUserId) {
        requireOwned(sessionId, authenticatedUserId);
        Instant now = clock.instant();
        if (repository.updateActivityTimestamps(sessionId, authenticatedUserId, now) != 1) throw notFound();
    }

    private AgentSession requireOwned(UUID sessionId, UUID authenticatedUserId) {
        AgentSession session = repository.findById(sessionId).orElseThrow(AgentSessionPresenceService::notFound);
        if (!session.getUserId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent session access denied");
        }
        return session;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found");
    }
}
