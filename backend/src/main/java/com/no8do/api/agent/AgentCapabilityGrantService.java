package com.no8do.api.agent;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentCapabilityGrantService {
    private final AgentRepository agentRepository;
    private final AgentCapabilityGrantRepository grantRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final UserRepository userRepository;
    private final No8doAgentProtocolProvider protocolProvider;
    private final AgentRegistryAuditService auditService;
    private final Clock clock;

    public AgentCapabilityGrantService(AgentRepository agentRepository,
            AgentCapabilityGrantRepository grantRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService, UserRepository userRepository,
            No8doAgentProtocolProvider protocolProvider, AgentRegistryAuditService auditService, Clock clock) {
        this.agentRepository = agentRepository;
        this.grantRepository = grantRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.userRepository = userRepository;
        this.protocolProvider = protocolProvider;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Bootstraps the protocol's current published capability ceiling in the Agent creation transaction. */
    @Transactional
    public void initializePublishedCapabilities(Agent agent, UUID actorUserId, Instant grantedAt) {
        List<AgentCapabilityGrant> defaults = protocolProvider.current().capabilities().capabilities().stream()
                .map(capability -> new AgentCapabilityGrant(UUID.randomUUID(), agent, capability, grantedAt, actorUserId))
                .toList();
        grantRepository.saveAllAndFlush(defaults);
    }

    @Transactional(readOnly = true)
    public List<AgentCapabilityGrantResponse> list(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId, false);
        return grantRepository.findByAgent_IdOrderByGrantedAtAscCapabilityAsc(agent.getId()).stream()
                .map(AgentCapabilityGrantResponse::from)
                .toList();
    }

    @Transactional
    public void grant(UUID workspaceId, UUID agentId, AgentCapability capability, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId, true);
        requirePublished(capability);
        if (agent.getLifecycleStatus() == AgentLifecycleStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived Agents cannot receive capability grants");
        }

        Instant grantedAt = clock.instant();
        int inserted = grantRepository.insertIfAbsent(UUID.randomUUID(), agent.getId(), capability.id(), grantedAt,
                actorUserId);
        if (inserted == 1) auditService.recordCapabilityGranted(actorUserId, agent, capability, grantedAt);
    }

    @Transactional
    public void revoke(UUID workspaceId, UUID agentId, AgentCapability capability, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId, true);
        requirePublished(capability);
        int deleted = grantRepository.deleteGrant(agent.getId(), capability.id());
        if (deleted == 1) auditService.recordCapabilityRevoked(actorUserId, agent, capability, clock.instant());
    }

    private Agent requireManagerScopedAgent(UUID workspaceId, UUID agentId, UUID actorUserId, boolean forUpdate) {
        if (workspaceId == null || actorUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Objects.requireNonNull(agentId, "agentId");
        Agent agent = (forUpdate ? agentRepository.findByIdAndWorkspaceIdForUpdate(agentId, workspaceId)
                : agentRepository.findByIdAndWorkspaceId(agentId, workspaceId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        requireEnabledActor(actorUserId);
        return agent;
    }

    private void requireEnabledActor(UUID actorUserId) {
        userRepository.findById(actorUserId).filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    private void requirePublished(AgentCapability capability) {
        if (capability == null || !protocolProvider.current().capabilities().capabilities().contains(capability)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capability is not published by the Agent protocol");
        }
    }
}
