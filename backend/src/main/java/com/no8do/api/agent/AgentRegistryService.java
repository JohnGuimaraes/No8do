package com.no8do.api.agent;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentRegistryService {

    private final AgentRepository agentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final AgentRegistryAuditService auditService;
    private final Clock clock;

    public AgentRegistryService(AgentRepository agentRepository, WorkspaceRepository workspaceRepository,
            UserRepository userRepository, WorkspaceAuthorizationService workspaceAuthorizationService,
            AgentRegistryAuditService auditService, Clock clock) {
        this.agentRepository = agentRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Agent> listAgents(UUID workspaceId, UUID actorUserId) {
        requireIds(workspaceId, actorUserId);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        return agentRepository.findByWorkspaceIdOrderByUpdatedAtDescIdAsc(workspaceId);
    }

    @Transactional(readOnly = true)
    public Agent getAgent(UUID workspaceId, UUID agentId, UUID actorUserId) {
        requireIds(workspaceId, actorUserId);
        Objects.requireNonNull(agentId, "agentId");
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        return findByWorkspace(workspaceId, agentId);
    }

    @Transactional
    public Agent createAgent(UUID workspaceId, UUID actorUserId, String name, String description,
            String providerDescriptor) {
        requireIds(workspaceId, actorUserId);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        User actor = requireEnabledActor(actorUserId);
        Agent agent = new Agent(workspace, normalizeName(name), normalizeOptionalText(description),
                normalizeOptionalText(providerDescriptor), actor);
        Agent saved = agentRepository.saveAndFlush(agent);
        auditService.recordCreated(actorUserId, saved, clock.instant());
        return saved;
    }

    @Transactional
    public Agent updateAgent(UUID workspaceId, UUID agentId, UUID actorUserId, String name, String description,
            String providerDescriptor) {
        requireIds(workspaceId, actorUserId);
        Objects.requireNonNull(agentId, "agentId");
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        requireEnabledActor(actorUserId);
        Agent agent = findByWorkspace(workspaceId, agentId);
        String normalizedName = normalizeName(name);
        String normalizedDescription = normalizeOptionalText(description);
        String normalizedProvider = normalizeOptionalText(providerDescriptor);
        List<String> changedFields = changedFields(agent, normalizedName, normalizedDescription, normalizedProvider);
        if (changedFields.isEmpty()) return agent;
        agent.updateDetails(normalizedName, normalizedDescription, normalizedProvider);
        Agent saved = agentRepository.saveAndFlush(agent);
        auditService.recordUpdated(actorUserId, saved, clock.instant(), changedFields);
        return saved;
    }

    @Transactional
    public Agent changeLifecycle(UUID workspaceId, UUID agentId, UUID actorUserId,
            AgentLifecycleStatus nextStatus) {
        requireIds(workspaceId, actorUserId);
        Objects.requireNonNull(agentId, "agentId");
        if (nextStatus == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lifecycle status is required");
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        requireEnabledActor(actorUserId);
        Agent agent = findByWorkspace(workspaceId, agentId);
        AgentLifecycleStatus previousStatus = agent.getLifecycleStatus();
        if (previousStatus == nextStatus) return agent;
        if (!isAllowedTransition(previousStatus, nextStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agent lifecycle transition is not allowed");
        }
        agent.changeLifecycleStatus(nextStatus);
        Agent saved = agentRepository.saveAndFlush(agent);
        auditService.recordLifecycleChanged(actorUserId, saved, previousStatus, clock.instant());
        return saved;
    }

    private Agent findByWorkspace(UUID workspaceId, UUID agentId) {
        return agentRepository.findByIdAndWorkspaceId(agentId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
    }

    private User requireEnabledActor(UUID actorUserId) {
        return userRepository.findById(actorUserId).filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    private static boolean isAllowedTransition(AgentLifecycleStatus current, AgentLifecycleStatus next) {
        return switch (current) {
            case ACTIVE -> next == AgentLifecycleStatus.DISABLED || next == AgentLifecycleStatus.ARCHIVED;
            case DISABLED -> next == AgentLifecycleStatus.ACTIVE || next == AgentLifecycleStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    private static List<String> changedFields(Agent agent, String name, String description, String providerDescriptor) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(agent.getName(), name)) fields.add("name");
        if (!Objects.equals(agent.getDescription(), description)) fields.add("description");
        if (!Objects.equals(agent.getProviderDescriptor(), providerDescriptor)) fields.add("providerDescriptor");
        return fields;
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent name is required");
        }
        String normalized = name.trim();
        if (normalized.length() > 160) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent name is too long");
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static void requireIds(UUID workspaceId, UUID actorUserId) {
        if (workspaceId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace is required");
        if (actorUserId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
