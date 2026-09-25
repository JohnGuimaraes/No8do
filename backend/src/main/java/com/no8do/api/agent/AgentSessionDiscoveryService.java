package com.no8do.api.agent;

import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentSessionDiscoveryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AgentSessionRepository repository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final AgentPresenceResolver presenceResolver = new AgentPresenceResolver();
    private final AgentPresenceProperties presenceProperties;
    private final Clock clock;

    public AgentSessionDiscoveryService(AgentSessionRepository repository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            AgentPresenceProperties presenceProperties, Clock clock) {
        this.repository = repository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.presenceProperties = presenceProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AgentSessionPageResponse list(UUID currentUserId, UUID workspaceId,
            AgentRuntimeMode runtimeMode, String clientName, int page, int size) {
        validatePage(page, size);
        if (workspaceId != null) {
            workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        }
        String normalizedClientName = clientName == null || clientName.isBlank() ? "" : clientName.trim();
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("registeredAt"), Sort.Order.desc("id")));
        Page<AgentSessionSummaryResponse> result = repository
                .findOwnedForDiscovery(currentUserId, workspaceId, runtimeMode, normalizedClientName, pageable)
                .map(this::toSummary);
        return AgentSessionPageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public AgentSessionSummaryResponse get(UUID sessionId, UUID currentUserId) {
        AgentSession session = repository.findByIdAndUserId(sessionId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found"));
        return toSummary(session);
    }

    private AgentSessionSummaryResponse toSummary(AgentSession session) {
        AgentPresenceStatus status = presenceResolver.resolve(session.getRegisteredAt(), session.getLastSeenAt(),
                session.getLastActivityAt(), session.getDisconnectedAt(), session.getRevokedAt(),
                clock.instant(), presenceProperties);
        return AgentSessionSummaryResponse.from(session, status);
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page deve ser maior ou igual a zero");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size deve estar entre 1 e 100");
        }
    }
}
