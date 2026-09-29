package com.no8do.api.agent;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AgentOperationalContextSnapshotReader {
    private final AgentSessionRepository sessions;
    private final AgentOperationalContextRepository contexts;

    public AgentOperationalContextSnapshotReader(AgentSessionRepository sessions,
            AgentOperationalContextRepository contexts) {
        this.sessions = sessions;
        this.contexts = contexts;
    }

    @Transactional(readOnly = true)
    public OperationalContextSnapshot read(UUID sessionId, UUID authenticatedUserId) {
        return snapshot(sessionId, authenticatedUserId, true);
    }

    @Transactional(readOnly = true)
    public OperationalContextSnapshot readInternal(UUID sessionId) {
        return snapshot(sessionId, null, false);
    }

    private OperationalContextSnapshot snapshot(UUID sessionId, UUID authenticatedUserId, boolean enforceOwner) {
        AgentSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found"));
        if (enforceOwner && !session.getUserId().equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent session access denied");
        }
        UUID workspaceId = session.getAgent() == null ? session.getWorkspaceId()
                : session.getAgent().getWorkspace().getId();
        AgentOperationalContext context = contexts.findById(sessionId).orElse(null);
        AgentOperationalContextSignal.Repository repository = context == null || context.getRepositoryName() == null
                ? null : new AgentOperationalContextSignal.Repository(context.getRepositoryVcs(),
                        context.getRepositoryProvider(), context.getRepositoryHost(), context.getRepositoryNamespace(),
                        context.getRepositoryName());
        return new OperationalContextSnapshot(sessionId, session.getUserId(), workspaceId,
                session.getRegisteredAt(), session.getLastSeenAt(), session.getLastActivityAt(),
                session.getDisconnectedAt(), session.getRevokedAt(), context == null ? null : context.getVersion(),
                context == null ? null : context.getSignalHash(), repository,
                context == null ? null : context.getBranch(), context == null ? null : context.getWorkingDirectory(),
                context == null ? java.util.List.of() : context.getReferences().stream().map(ref ->
                        new AgentOperationalContextSignal.Reference(ref.getKind(), ref.getProvider(), ref.getReferenceKey())).toList(),
                context == null ? null : context.getProjectResolutionRepositoryId());
    }
}
