package com.no8do.api.agent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentOperationalContextService {
    private final AgentSessionRepository sessionRepository;
    private final AgentOperationalContextRepository contextRepository;
    private final AgentOperationalContextValidator validator;
    private final AgentEventFactory eventFactory;
    private final AgentEventPublisher eventPublisher;
    private final AgentPresenceResolver presenceResolver = new AgentPresenceResolver();
    private final AgentPresenceProperties presenceProperties;
    private final Clock clock;

    public AgentOperationalContextService(AgentSessionRepository sessionRepository,
            AgentOperationalContextRepository contextRepository, AgentOperationalContextValidator validator,
            AgentEventFactory eventFactory, AgentEventPublisher eventPublisher,
            AgentPresenceProperties presenceProperties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.contextRepository = contextRepository;
        this.validator = validator;
        this.eventFactory = eventFactory;
        this.eventPublisher = eventPublisher;
        this.presenceProperties = presenceProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AgentOperationalContextResponse get(UUID sessionId, UUID authenticatedUserId) {
        AgentSession session = requireOwned(sessionId, authenticatedUserId);
        if (session.getRevokedAt() != null) throw new AgentSessionRevokedException();
        AgentOperationalContext context = contextRepository.findWithReferencesBySessionId(sessionId)
                .orElseThrow(AgentOperationalContextService::notFound);
        return AgentOperationalContextResponse.from(context);
    }

    @Transactional
    public AgentOperationalContextResponse replace(UUID sessionId, UUID authenticatedUserId,
            AgentOperationalContextUpdateRequest request) {
        AgentSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(AgentOperationalContextService::notFound);
        requireOwner(session, authenticatedUserId);
        requireWritable(session);
        UUID effectiveWorkspaceId = session.getAgent() == null ? session.getWorkspaceId()
                : session.getAgent().getWorkspace().getId();
        if (request.workspaceHint() != null && !request.workspaceHint().equals(effectiveWorkspaceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operational context is inconsistent");
        }

        AgentOperationalContextSignal signal = validator.canonicalize(request);
        String hash = signalHash(signal);
        AgentOperationalContext context = contextRepository.findWithReferencesBySessionId(sessionId).orElse(null);
        List<String> changedFields;
        if (context == null) {
            context = new AgentOperationalContext(sessionId, signal, hash);
            context = contextRepository.saveAndFlush(context);
            changedFields = List.of("repository", "branch", "workingDirectory", "references", "resolution");
        } else {
            if (context.getSignalHash().equals(hash)) return AgentOperationalContextResponse.from(context);
            changedFields = changedFields(context, signal);
            context.applySignal(signal, hash);
            try {
                context = contextRepository.saveAndFlush(context);
            } catch (OptimisticLockingFailureException conflict) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Operational context changed concurrently");
            }
        }
        eventPublisher.publish(eventFactory.operationalContextChanged(session, context.getVersion(), changedFields,
                context.getProjectResolutionStatus(), context.getWorkItemResolutionStatus()));
        return AgentOperationalContextResponse.from(context);
    }

    private AgentSession requireOwned(UUID sessionId, UUID userId) {
        AgentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(AgentOperationalContextService::notFound);
        requireOwner(session, userId);
        return session;
    }

    private static void requireOwner(AgentSession session, UUID userId) {
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent session access denied");
        }
    }

    private void requireWritable(AgentSession session) {
        if (session.getRevokedAt() != null) throw new AgentSessionRevokedException();
        AgentPresenceStatus status = presenceResolver.resolve(session.getRegisteredAt(), session.getLastSeenAt(),
                session.getLastActivityAt(), session.getDisconnectedAt(), session.getRevokedAt(), clock.instant(),
                presenceProperties);
        if (status == AgentPresenceStatus.DISCONNECTED) throw new AgentSessionDisconnectedException(session.getId());
    }

    private static List<String> changedFields(AgentOperationalContext previous,
            AgentOperationalContextSignal next) {
        List<String> fields = new ArrayList<>();
        AgentOperationalContextSignal.Repository repository = next.repository();
        if (!java.util.Objects.equals(previous.getRepositoryVcs(), repository == null ? null : repository.vcs())
                || !java.util.Objects.equals(previous.getRepositoryProvider(), repository == null ? null : repository.provider())
                || !java.util.Objects.equals(previous.getRepositoryHost(), repository == null ? null : repository.host())
                || !java.util.Objects.equals(previous.getRepositoryNamespace(), repository == null ? null : repository.namespace())
                || !java.util.Objects.equals(previous.getRepositoryName(), repository == null ? null : repository.name())) {
            fields.add("repository");
        }
        if (!java.util.Objects.equals(previous.getBranch(), next.branch())) fields.add("branch");
        if (!java.util.Objects.equals(previous.getWorkingDirectory(), next.workingDirectory())) fields.add("workingDirectory");
        List<String> oldReferences = previous.getReferences().stream()
                .map(ref -> ref.getKind().name() + ":" + ref.getProvider() + ":" + ref.getReferenceKey()).sorted().toList();
        List<String> newReferences = next.references().stream()
                .map(ref -> ref.kind().name() + ":" + ref.provider() + ":" + ref.key()).sorted().toList();
        if (!oldReferences.equals(newReferences)) fields.add("references");
        if (fields.isEmpty()) fields.add("signal");
        return List.copyOf(fields);
    }

    private static String signalHash(AgentOperationalContextSignal signal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            put(digest, signal.repository() == null ? null : signal.repository().vcs());
            put(digest, signal.repository() == null ? null : signal.repository().provider());
            put(digest, signal.repository() == null ? null : signal.repository().host());
            put(digest, signal.repository() == null ? null : signal.repository().namespace());
            put(digest, signal.repository() == null ? null : signal.repository().name());
            put(digest, signal.branch());
            put(digest, signal.workingDirectory());
            for (AgentOperationalContextSignal.Reference reference : signal.references()) {
                put(digest, reference.kind().name());
                put(digest, reference.provider());
                put(digest, reference.key());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    private static void put(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent session not found");
    }
}
