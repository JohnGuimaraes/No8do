package com.no8do.api.agent;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentCredentialService {

    private final AgentCredentialRepository credentialRepository;
    private final AgentRepository agentRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final AgentRegistryAuditService auditService;
    private final AgentCredentialSecretCodec secretCodec;
    private final Clock clock;

    public AgentCredentialService(AgentCredentialRepository credentialRepository, AgentRepository agentRepository,
            UserRepository userRepository, WorkspaceAuthorizationService workspaceAuthorizationService,
            AgentRegistryAuditService auditService, AgentCredentialSecretCodec secretCodec, Clock clock) {
        this.credentialRepository = credentialRepository;
        this.agentRepository = agentRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.auditService = auditService;
        this.secretCodec = secretCodec;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AgentCredentialMetadataResponse> list(UUID workspaceId, UUID agentId, UUID actorUserId) {
        authorize(workspaceId, actorUserId, false);
        Agent agent = findAgent(workspaceId, agentId);
        return credentialRepository.findByAgentIdOrderByCreatedAtDescIdAsc(agent.getId()).stream()
                .map(AgentCredentialMetadataResponse::from)
                .toList();
    }

    @Transactional
    public AgentCredentialIssueResponse create(UUID workspaceId, UUID agentId, UUID actorUserId) {
        authorize(workspaceId, actorUserId, true);
        Agent agent = findAgentForUpdate(workspaceId, agentId);
        requireActiveAgent(agent);
        AgentCredentialSecretCodec.IssuedCredential issued = secretCodec.issue();
        Instant now = clock.instant();
        AgentCredential credential = new AgentCredential(UUID.randomUUID(), agent, issued.publicCredentialId(),
                issued.secretHash(), now);
        AgentCredential saved = credentialRepository.saveAndFlush(credential);
        auditService.recordCredentialCreated(actorUserId, agent, saved, now);
        return issueResponse(saved, issued);
    }

    /** Revocation is idempotent; only the ACTIVE to REVOKED transition emits an audit event. */
    @Transactional
    public AgentCredentialMetadataResponse revoke(UUID workspaceId, UUID agentId, UUID credentialId,
            UUID actorUserId) {
        authorize(workspaceId, actorUserId, true);
        Agent agent = findAgentForUpdate(workspaceId, agentId);
        AgentCredential credential = findCredential(agent, credentialId);
        if (credential.revoke(clock.instant())) {
            Instant revokedAt = credential.getRevokedAt();
            credentialRepository.saveAndFlush(credential);
            auditService.recordCredentialRevoked(actorUserId, agent, credential, revokedAt, "ADMIN");
        }
        return AgentCredentialMetadataResponse.from(credential);
    }

    @Transactional
    public AgentCredentialIssueResponse rotate(UUID workspaceId, UUID agentId, UUID credentialId,
            UUID actorUserId) {
        authorize(workspaceId, actorUserId, true);
        Agent agent = findAgentForUpdate(workspaceId, agentId);
        requireActiveAgent(agent);
        AgentCredential previous = findCredential(agent, credentialId);
        if (previous.getStatus() != AgentCredentialStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent credential is not active");
        }

        AgentCredentialSecretCodec.IssuedCredential issued = secretCodec.issue();
        Instant now = clock.instant();
        previous.revoke(now);
        credentialRepository.saveAndFlush(previous);
        AgentCredential replacement = new AgentCredential(UUID.randomUUID(), agent, issued.publicCredentialId(),
                issued.secretHash(), now);
        AgentCredential savedReplacement = credentialRepository.saveAndFlush(replacement);
        auditService.recordCredentialRotated(actorUserId, agent, previous, savedReplacement, now);
        return issueResponse(savedReplacement, issued);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<VerifiedAgentCredential> verify(String presentedCredential) {
        AgentCredentialSecretCodec.ParsedCredential parsed = secretCodec.parse(presentedCredential).orElse(null);
        if (parsed == null) return java.util.Optional.empty();
        AgentCredential credential = credentialRepository.findByPublicCredentialId(parsed.publicCredentialId())
                .orElse(null);
        String storedHash = credential == null ? null : credential.getSecretHash();
        boolean secretMatches = secretCodec.matches(storedHash, parsed.secret());
        if (credential == null || credential.getStatus() != AgentCredentialStatus.ACTIVE || !secretMatches) {
            return java.util.Optional.empty();
        }
        Agent agent = credential.getAgent();
        if (agent.getLifecycleStatus() != AgentLifecycleStatus.ACTIVE) return java.util.Optional.empty();
        return java.util.Optional.of(new VerifiedAgentCredential(credential.getId(), agent.getId(),
                agent.getWorkspace().getId()));
    }

    private void authorize(UUID workspaceId, UUID actorUserId, boolean requireEnabledActor) {
        if (workspaceId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace is required");
        if (actorUserId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        if (requireEnabledActor) requireEnabledActor(actorUserId);
    }

    private void requireEnabledActor(UUID actorUserId) {
        userRepository.findById(actorUserId).filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    private Agent findAgent(UUID workspaceId, UUID agentId) {
        return agentRepository.findByIdAndWorkspaceId(agentId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
    }

    private Agent findAgentForUpdate(UUID workspaceId, UUID agentId) {
        return agentRepository.findByIdAndWorkspaceIdForUpdate(agentId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
    }

    private AgentCredential findCredential(Agent agent, UUID credentialId) {
        return credentialRepository.findByIdAndAgentId(credentialId, agent.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent credential not found"));
    }

    private static void requireActiveAgent(Agent agent) {
        if (agent.getLifecycleStatus() != AgentLifecycleStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent must be ACTIVE to issue credentials");
        }
    }

    private static AgentCredentialIssueResponse issueResponse(AgentCredential credential,
            AgentCredentialSecretCodec.IssuedCredential issued) {
        return new AgentCredentialIssueResponse(credential.getId(), credential.getPublicCredentialId(),
                credential.getStatus(), credential.getCreatedAt(), issued.serialized());
    }
}
