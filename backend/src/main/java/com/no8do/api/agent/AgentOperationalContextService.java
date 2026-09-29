package com.no8do.api.agent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentOperationalContextService {
    private final AgentSessionRepository sessionRepository;
    private final AgentOperationalContextRepository contextRepository;
    private final AgentOperationalContextValidator validator;
    private final AgentPresenceResolver presenceResolver = new AgentPresenceResolver();
    private final AgentPresenceProperties presenceProperties;
    private final Clock clock;
    private final AgentOperationalContextSnapshotReader snapshotReader;
    private final AgentOperationalContextWriteService writeService;
    private final OperationalRepositoryResolver repositoryResolver;
    private final OperationalProjectRepositoryAssociationLookup projectAssociationLookup;

    public AgentOperationalContextService(AgentSessionRepository sessionRepository,
            AgentOperationalContextRepository contextRepository, AgentOperationalContextValidator validator,
            AgentPresenceProperties presenceProperties, Clock clock,
            AgentOperationalContextSnapshotReader snapshotReader, AgentOperationalContextWriteService writeService,
            OperationalRepositoryResolver repositoryResolver,
            OperationalProjectRepositoryAssociationLookup projectAssociationLookup) {
        this.sessionRepository = sessionRepository;
        this.contextRepository = contextRepository;
        this.validator = validator;
        this.presenceProperties = presenceProperties;
        this.clock = clock;
        this.snapshotReader = snapshotReader;
        this.writeService = writeService;
        this.repositoryResolver = repositoryResolver;
        this.projectAssociationLookup = projectAssociationLookup;
    }

    @Transactional(readOnly = true)
    public AgentOperationalContextResponse get(UUID sessionId, UUID authenticatedUserId) {
        AgentSession session = requireOwned(sessionId, authenticatedUserId);
        if (session.getRevokedAt() != null) throw new AgentSessionRevokedException();
        AgentOperationalContext context = contextRepository.findWithReferencesBySessionId(sessionId)
                .orElseThrow(AgentOperationalContextService::notFound);
        return AgentOperationalContextResponse.from(context);
    }

    public AgentOperationalContextResponse replace(UUID sessionId, UUID authenticatedUserId,
            AgentOperationalContextUpdateRequest request) {
        OperationalContextSnapshot snapshot = snapshotReader.read(sessionId, authenticatedUserId);
        requireWritable(snapshot);
        UUID effectiveWorkspaceId = snapshot.workspaceId();
        if (request.workspaceHint() != null && !request.workspaceHint().equals(effectiveWorkspaceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operational context is inconsistent");
        }
        AgentOperationalContextSignal signal = validator.canonicalize(request);
        String hash = signalHash(signal);
        boolean repositoryChanged = !sameRepositoryIdentity(snapshot.repository(), signal.repository());
        String providerRepositoryId = repositoryChanged ? null : snapshot.projectResolutionRepositoryId();
        if (repositoryChanged && signal.repository() != null) {
            boolean githubSupported = "GIT".equals(signal.repository().vcs())
                    && "github".equals(signal.repository().provider()) && "github.com".equals(signal.repository().host());
            if (githubSupported && effectiveWorkspaceId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Workspace GitHub integration is unavailable");
            }
        }
        if (repositoryChanged && signal.repository() != null && effectiveWorkspaceId != null) {
            OperationalRepositoryResolver.RepositoryIdentityResolution identity =
                    repositoryResolver.resolve(OperationalRepositoryLocator.from(signal.repository()), effectiveWorkspaceId);
            if (identity.found()) providerRepositoryId = identity.providerRepositoryId();
        }
        ProjectResolution resolution = resolveProject(effectiveWorkspaceId, providerRepositoryId,
                providerRepositoryId != null && signal.repository() != null
                        && "github".equals(signal.repository().provider()) && "github.com".equals(signal.repository().host()));
        try {
            return writeService.persist(sessionId, authenticatedUserId, snapshot, signal, hash,
                    resolution.status(), resolution.projectId(), resolution.confidence(), providerRepositoryId,
                    resolution.evidence());
        } catch (ResponseStatusException conflict) {
            if (conflict.getStatusCode() != HttpStatus.CONFLICT) throw conflict;
            OperationalContextSnapshot current = snapshotReader.read(sessionId, authenticatedUserId);
            boolean sameRepository = sameRepositoryIdentity(current.repository(), signal.repository());
            boolean providerIdentityStillCurrent = !repositoryChanged
                    || java.util.Objects.equals(current.projectResolutionRepositoryId(), providerRepositoryId);
            if (!sameRepository || !providerIdentityStillCurrent) throw conflict;
            String currentProviderId = current.projectResolutionRepositoryId();
            ProjectResolution currentResolution = resolveProject(current.workspaceId(), currentProviderId,
                    currentProviderId != null);
            return writeService.persist(sessionId, authenticatedUserId, current, signal, hash,
                    currentResolution.status(), currentResolution.projectId(), currentResolution.confidence(),
                    currentProviderId, currentResolution.evidence());
        }
    }

    public void refreshRepositoryResolution(UUID sessionId) {
        OperationalContextSnapshot snapshot = snapshotReader.readInternal(sessionId);
        if (!snapshot.hasContext()) return;
        refresh(snapshot);
    }

    public void refreshRepositoryResolutionForWorkspace(UUID workspaceId, long repositoryId) {
        if (workspaceId == null || repositoryId <= 0) return;
        for (UUID sessionId : contextRepository.findSessionIdsForRepositoryResolution(workspaceId,
                Long.toString(repositoryId))) refreshRepositoryResolution(sessionId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshRepositoryResolutionForWorkspaceAfterCommit(UUID workspaceId, long repositoryId) {
        refreshRepositoryResolutionForWorkspace(workspaceId, repositoryId);
    }

    private void refresh(OperationalContextSnapshot snapshot) {
        if (snapshot.projectResolutionRepositoryId() == null || snapshot.workspaceId() == null) return;
        ProjectResolution resolution = resolveProject(snapshot.workspaceId(), snapshot.projectResolutionRepositoryId(), true);
        AgentOperationalContextSignal signal = signalFromSnapshot(snapshot);
        writeService.persist(snapshot.sessionId(), snapshot.userId(), snapshot, signal, snapshot.signalHash(),
                resolution.status(), resolution.projectId(), resolution.confidence(),
                snapshot.projectResolutionRepositoryId(), resolution.evidence());
    }

    private ProjectResolution resolveProject(UUID workspaceId, String repositoryId, boolean supported) {
        if (!supported || workspaceId == null || repositoryId == null) {
            return new ProjectResolution(OperationalContextResolutionStatus.UNRESOLVED, null, null, null);
        }
        List<UUID> candidates = projectAssociationLookup.findProjectIds(workspaceId, repositoryId);
        if (candidates.isEmpty()) return new ProjectResolution(OperationalContextResolutionStatus.UNRESOLVED,
                null, null, "REPOSITORY_PROVIDER_ID");
        if (candidates.size() == 1) return new ProjectResolution(OperationalContextResolutionStatus.RESOLVED,
                candidates.getFirst(), OperationalContextConfidence.HIGH, "REPOSITORY_PROVIDER_ID");
        return new ProjectResolution(OperationalContextResolutionStatus.AMBIGUOUS, null, null,
                "REPOSITORY_PROVIDER_ID");
    }

    private static AgentOperationalContextSignal signalFromSnapshot(OperationalContextSnapshot snapshot) {
        return new AgentOperationalContextSignal(snapshot.repository(), snapshot.branch(), snapshot.workingDirectory(),
                snapshot.references());
    }

    private static boolean sameRepositoryIdentity(AgentOperationalContextSignal.Repository first,
            AgentOperationalContextSignal.Repository second) {
        if (first == null || second == null) return first == second;
        return first.vcs().equalsIgnoreCase(second.vcs()) && first.provider().equalsIgnoreCase(second.provider())
                && first.host().equalsIgnoreCase(second.host()) && first.namespace().equalsIgnoreCase(second.namespace())
                && first.name().equalsIgnoreCase(second.name());
    }

    private void requireWritable(OperationalContextSnapshot snapshot) {
        if (snapshot.revokedAt() != null) throw new AgentSessionRevokedException();
        AgentPresenceStatus status = presenceResolver.resolve(snapshot.registeredAt(), snapshot.lastSeenAt(),
                snapshot.lastActivityAt(), snapshot.disconnectedAt(), snapshot.revokedAt(), clock.instant(),
                presenceProperties);
        if (status == AgentPresenceStatus.DISCONNECTED) throw new AgentSessionDisconnectedException(snapshot.sessionId());
    }

    private record ProjectResolution(OperationalContextResolutionStatus status, UUID projectId,
            OperationalContextConfidence confidence, String evidence) {}

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
