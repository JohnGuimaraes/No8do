package com.no8do.api.agent;

import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentSessionRegistry {
    private final AgentSessionRepository repository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final No8doAgentProtocolProvider protocolProvider;
    private final Clock clock;
    private final AgentEventFactory eventFactory;
    private final AgentEventPublisher eventPublisher;
    private final AgentGatewayMetrics metrics;

    public AgentSessionRegistry(AgentSessionRepository repository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            No8doAgentProtocolProvider protocolProvider, Clock clock, AgentEventFactory eventFactory,
            AgentEventPublisher eventPublisher, AgentGatewayMetrics metrics) {
        this.repository = repository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.protocolProvider = protocolProvider;
        this.clock = clock;
        this.eventFactory = eventFactory;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    @Transactional
    public AgentSessionResponse register(UUID authenticatedUserId, AgentSessionRegistrationRequest request) {
        AgentClientIdentity clientIdentity = request.clientIdentity();
        if (request.workspaceId() != null) {
            workspaceAuthorizationService.requireWorkspaceMember(request.workspaceId(), authenticatedUserId);
        }
        No8doAgentProtocol protocol = protocolProvider.current();
        int inserted = repository.insertIfAbsent(UUID.randomUUID(), authenticatedUserId, request.workspaceId(),
                clientIdentity.clientName(), clientIdentity.clientVersion(), request.transport().name(), protocol.protocolName(),
                protocol.protocolVersion(), clock.instant(), request.transportSessionFingerprint());
        AgentSession session = repository.findByTransportAndTransportSessionFingerprintAndRevokedAtIsNull(
                request.transport(), request.transportSessionFingerprint()).orElseThrow();
        if (!session.getUserId().equals(authenticatedUserId)
                || !java.util.Objects.equals(session.getWorkspaceId(), request.workspaceId())
                || !session.getClientName().equals(clientIdentity.clientName())
                || !session.getClientVersion().equals(clientIdentity.clientVersion())
                || session.getTransport() != request.transport()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transport session identity conflicts with registration");
        }
        if (inserted == 1) {
            metrics.sessionRegisteredAfterCommit();
            eventPublisher.publish(eventFactory.connected(session));
        }
        return AgentSessionResponse.from(session);
    }
}
