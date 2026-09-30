package com.no8do.api.agent;

import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.integration.IntegrationPrincipal;
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
    private final AgentCredentialVerificationService credentialVerificationService;
    private final AgentAuditTrailService auditTrailService;

    public AgentSessionRegistry(AgentSessionRepository repository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            No8doAgentProtocolProvider protocolProvider, Clock clock, AgentEventFactory eventFactory,
            AgentEventPublisher eventPublisher, AgentGatewayMetrics metrics,
            AgentCredentialVerificationService credentialVerificationService,
            AgentAuditTrailService auditTrailService) {
        this.repository = repository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.protocolProvider = protocolProvider;
        this.clock = clock;
        this.eventFactory = eventFactory;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.credentialVerificationService = credentialVerificationService;
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    public AgentSessionResponse register(UUID authenticatedUserId, AgentSessionRegistrationRequest request) {
        return register(authenticatedUserId, request, null);
    }

    @Transactional
    public AgentSessionResponse register(UUID authenticatedUserId, AgentSessionRegistrationRequest request,
            String presentedCredential) {
        AgentClientIdentity clientIdentity = request.clientIdentity();
        VerifiedAgentCredential verified = null;
        UUID workspaceId = request.workspaceId();
        UUID agentId = null;
        UUID credentialId = null;
        if (presentedCredential != null) {
            verified = credentialVerificationService.verifyForSessionBinding(presentedCredential)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                            "Invalid agent credential"));
            if (workspaceId != null && !workspaceId.equals(verified.workspaceId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace access denied");
            }
            workspaceId = verified.workspaceId();
            agentId = verified.agentId();
            credentialId = verified.credentialId();
        }
        if (workspaceId != null) {
            workspaceAuthorizationService.requireWorkspaceMember(workspaceId, authenticatedUserId);
        }
        No8doAgentProtocol protocol = protocolProvider.current();
        java.time.Instant registeredAt = clock.instant();
        int inserted = repository.insertIfAbsent(UUID.randomUUID(), authenticatedUserId, workspaceId,
                clientIdentity.clientName(), clientIdentity.clientVersion(), request.transport().name(), protocol.protocolName(),
                protocol.protocolVersion(), registeredAt, request.transportSessionFingerprint(), agentId, credentialId, null);
        AgentSession session = repository.findByTransportAndTransportSessionFingerprintAndRevokedAtIsNull(
                request.transport(), request.transportSessionFingerprint()).orElseThrow();
        if (!session.getUserId().equals(authenticatedUserId)
                || !java.util.Objects.equals(session.getWorkspaceId(), workspaceId)
                || !session.getClientName().equals(clientIdentity.clientName())
                || !session.getClientVersion().equals(clientIdentity.clientVersion())
                || session.getTransport() != request.transport()
                || !java.util.Objects.equals(session.getAgent() == null ? null : session.getAgent().getId(), agentId)
                || !java.util.Objects.equals(session.getAgentCredential() == null ? null : session.getAgentCredential().getId(), credentialId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transport session identity conflicts with registration");
        }
        if (inserted == 1) {
            if (credentialId != null) {
                auditTrailService.recordSessionBound(session.getId(), authenticatedUserId, workspaceId,
                        agentId, credentialId, registeredAt);
            }
            metrics.sessionRegisteredAfterCommit();
            eventPublisher.publish(eventFactory.connected(session));
        }
        return AgentSessionResponse.from(session);
    }

    @Transactional
    public AgentSessionResponse register(IntegrationPrincipal principal, AgentSessionRegistrationRequest request) {
        if (request.workspaceId() != null && !request.workspaceId().equals(principal.workspaceId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace access denied");
        }
        AgentClientIdentity clientIdentity = request.clientIdentity();
        No8doAgentProtocol protocol = protocolProvider.current();
        java.time.Instant registeredAt = clock.instant();
        int inserted = repository.insertIfAbsent(UUID.randomUUID(), null, principal.workspaceId(),
                clientIdentity.clientName(), clientIdentity.clientVersion(), request.transport().name(),
                protocol.protocolName(), protocol.protocolVersion(), registeredAt,
                request.transportSessionFingerprint(), principal.agentId(), null,
                principal.integrationAuthorizationId());
        AgentSession session = repository.findByTransportAndTransportSessionFingerprintAndRevokedAtIsNull(
                request.transport(), request.transportSessionFingerprint()).orElseThrow();
        if (session.getUserId() != null || !java.util.Objects.equals(session.getWorkspaceId(), principal.workspaceId())
                || !java.util.Objects.equals(session.getAgent() == null ? null : session.getAgent().getId(), principal.agentId())
                || session.getAgentCredential() != null
                || !java.util.Objects.equals(session.getIntegrationAuthorization() == null ? null
                        : session.getIntegrationAuthorization().getId(), principal.integrationAuthorizationId())
                || !session.getClientName().equals(clientIdentity.clientName())
                || !session.getClientVersion().equals(clientIdentity.clientVersion())
                || session.getTransport() != request.transport()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transport session identity conflicts with registration");
        }
        if (inserted == 1) {
            auditTrailService.recordIntegrationSessionBound(session.getId(), principal.grantorUserId(),
                    principal.workspaceId(), principal.agentId(), principal.integrationAuthorizationId(), registeredAt);
            metrics.sessionRegisteredAfterCommit();
            eventPublisher.publish(eventFactory.connected(session));
        }
        return AgentSessionResponse.from(session);
    }
}
