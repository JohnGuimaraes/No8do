package com.no8do.api.agent;

import com.no8do.api.integration.IntegrationPrincipal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentOperationalContextWriteService {
    private final AgentSessionRepository sessions;
    private final AgentOperationalContextRepository contexts;
    private final AgentEventFactory eventFactory;
    private final AgentEventPublisher eventPublisher;
    private final AgentPresenceResolver presenceResolver = new AgentPresenceResolver();
    private final AgentPresenceProperties presenceProperties;
    private final Clock clock;
    private final AgentSessionAuthorizationService authorizationService;

    public AgentOperationalContextWriteService(AgentSessionRepository sessions,
            AgentOperationalContextRepository contexts, AgentEventFactory eventFactory,
            AgentEventPublisher eventPublisher, AgentPresenceProperties presenceProperties, Clock clock,
            AgentSessionAuthorizationService authorizationService) {
        this.sessions = sessions;
        this.contexts = contexts;
        this.eventFactory = eventFactory;
        this.eventPublisher = eventPublisher;
        this.presenceProperties = presenceProperties;
        this.clock = clock;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public AgentOperationalContextResponse persist(UUID sessionId, UUID userId,
            OperationalContextSnapshot expected, AgentOperationalContextSignal signal, String hash,
            OperationalContextResolutionStatus resolutionStatus, UUID projectId,
            OperationalContextConfidence confidence, String providerRepositoryId, String evidence) {
        return persistInternal(sessionId, userId, null, expected, signal, hash, resolutionStatus,
                projectId, confidence, providerRepositoryId, evidence);
    }

    @Transactional
    public AgentOperationalContextResponse persist(UUID sessionId, IntegrationPrincipal principal,
            OperationalContextSnapshot expected, AgentOperationalContextSignal signal, String hash,
            OperationalContextResolutionStatus resolutionStatus, UUID projectId,
            OperationalContextConfidence confidence, String providerRepositoryId, String evidence) {
        return persistInternal(sessionId, null, principal, expected, signal, hash, resolutionStatus,
                projectId, confidence, providerRepositoryId, evidence);
    }

    private AgentOperationalContextResponse persistInternal(UUID sessionId, UUID userId,
            IntegrationPrincipal principal, OperationalContextSnapshot expected, AgentOperationalContextSignal signal,
            String hash, OperationalContextResolutionStatus resolutionStatus, UUID projectId,
            OperationalContextConfidence confidence, String providerRepositoryId, String evidence) {
        AgentSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found"));
        if (principal != null) authorizationService.require(sessionId, principal);
        else if (userId != null && !userId.equals(session.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent session access denied");
        }
        requireWritable(session);
        AgentOperationalContext context = contexts.findWithReferencesBySessionId(sessionId).orElse(null);
        if (expected.hasContext() != (context != null)
                || (context != null && (context.getVersion() != expected.contextVersion()
                    || !Objects.equals(context.getSignalHash(), expected.signalHash())))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operational context changed concurrently");
        }
        boolean signalChanged = context == null || !context.getSignalHash().equals(hash);
        if (!signalChanged && Objects.equals(context.getProjectResolutionStatus(), resolutionStatus)
                && Objects.equals(context.getResolvedProjectId(), projectId)
                && Objects.equals(context.getProjectResolutionRepositoryId(), providerRepositoryId)
                && Objects.equals(context.getProjectResolutionEvidence(), evidence)) {
            return AgentOperationalContextResponse.from(context);
        }
        OperationalContextResolutionStatus previousStatus = context == null
                ? OperationalContextResolutionStatus.UNRESOLVED : context.getProjectResolutionStatus();
        UUID previousProjectId = context == null ? null : context.getResolvedProjectId();
        String previousEvidence = context == null ? null : context.getProjectResolutionEvidence();
        OperationalContextResolutionStatus workItemStatus = context == null
                ? OperationalContextResolutionStatus.UNRESOLVED : context.getWorkItemResolutionStatus();
        List<String> changedFields = context == null
                ? new ArrayList<>(List.of("repository", "branch", "workingDirectory", "references"))
                : changedFields(context, signal);
        boolean resolutionChanged = !Objects.equals(previousStatus, resolutionStatus)
                || !Objects.equals(previousProjectId, projectId) || !Objects.equals(previousEvidence, evidence);
        if (context == null) context = new AgentOperationalContext(sessionId, signal, hash);
        else if (signalChanged) {
            if (!context.referencesMatch(signal.references())) {
                context.parkReferenceIdentities();
                contexts.saveAndFlush(context);
            }
            context.applySignal(signal, hash);
        }
        context.applyProjectResolution(resolutionStatus, projectId, confidence, providerRepositoryId, evidence);
        try {
            context = contexts.saveAndFlush(context);
        } catch (OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operational context changed concurrently");
        }
        if (resolutionChanged) changedFields.add("resolution");
        if (context == null) throw new IllegalStateException("Operational context persistence failed");
        if (!changedFields.isEmpty()) {
            eventPublisher.publish(eventFactory.operationalContextChanged(session, context.getVersion(), changedFields,
                    previousStatus, resolutionStatus, workItemStatus, projectId, evidence));
        }
        return AgentOperationalContextResponse.from(context);
    }

    private void requireWritable(AgentSession session) {
        if (session.getRevokedAt() != null) throw new AgentSessionRevokedException();
        AgentPresenceStatus status = presenceResolver.resolve(session.getRegisteredAt(), session.getLastSeenAt(),
                session.getLastActivityAt(), session.getDisconnectedAt(), session.getRevokedAt(), clock.instant(),
                presenceProperties);
        if (status == AgentPresenceStatus.DISCONNECTED) throw new AgentSessionDisconnectedException(session.getId());
    }

    private static List<String> changedFields(AgentOperationalContext previous, AgentOperationalContextSignal next) {
        List<String> fields = new ArrayList<>();
        AgentOperationalContextSignal.Repository repository = next.repository();
        if (!Objects.equals(previous.getRepositoryVcs(), repository == null ? null : repository.vcs())
                || !Objects.equals(previous.getRepositoryProvider(), repository == null ? null : repository.provider())
                || !Objects.equals(previous.getRepositoryHost(), repository == null ? null : repository.host())
                || !Objects.equals(previous.getRepositoryNamespace(), repository == null ? null : repository.namespace())
                || !Objects.equals(previous.getRepositoryName(), repository == null ? null : repository.name())) fields.add("repository");
        if (!Objects.equals(previous.getBranch(), next.branch())) fields.add("branch");
        if (!Objects.equals(previous.getWorkingDirectory(), next.workingDirectory())) fields.add("workingDirectory");
        List<String> oldRefs = previous.getReferences().stream().map(r -> r.getKind() + ":" + r.getProvider() + ":" + r.getReferenceKey()).sorted().toList();
        List<String> newRefs = next.references().stream().map(r -> r.kind() + ":" + r.provider() + ":" + r.key()).sorted().toList();
        if (!oldRefs.equals(newRefs)) fields.add("references");
        return fields;
    }
}
