package com.no8do.api.agent;

import com.no8do.api.connection.Connection;
import com.no8do.api.connection.ConnectionRepository;
import com.no8do.api.connection.ConnectionStatus;
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
public class AgentConnectionAssignmentService {

    private final AgentRepository agentRepository;
    private final ConnectionRepository connectionRepository;
    private final AgentConnectionAssignmentRepository assignmentRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final AgentRegistryAuditService auditService;
    private final Clock clock;

    public AgentConnectionAssignmentService(AgentRepository agentRepository, ConnectionRepository connectionRepository,
            AgentConnectionAssignmentRepository assignmentRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService, AgentRegistryAuditService auditService,
            Clock clock) {
        this.agentRepository = agentRepository;
        this.connectionRepository = connectionRepository;
        this.assignmentRepository = assignmentRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AgentConnectionAssignmentResponse> list(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId);
        return assignmentRepository.findConnectionSummaries(workspaceId, agent.getId());
    }

    @Transactional
    public void assign(UUID workspaceId, UUID agentId, UUID connectionId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgentForUpdate(workspaceId, agentId, actorUserId);
        Connection connection = findConnection(workspaceId, connectionId);
        if (agent.getLifecycleStatus() == AgentLifecycleStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived Agents cannot receive Connection assignments");
        }
        if (connection.getStatus() != ConnectionStatus.CONFIGURED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Disconnected Connections cannot be assigned");
        }

        Instant assignedAt = clock.instant();
        int inserted = assignmentRepository.insertIfAbsent(UUID.randomUUID(), agent.getId(), connection.getId(),
                assignedAt, actorUserId);
        if (inserted == 1) auditService.recordConnectionAssigned(actorUserId, agent, connection.getId(), assignedAt);
    }

    @Transactional
    public void unassign(UUID workspaceId, UUID agentId, UUID connectionId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId);
        Connection connection = findConnection(workspaceId, connectionId);
        int deleted = assignmentRepository.deleteAssignment(agent.getId(), connection.getId());
        if (deleted == 1) auditService.recordConnectionUnassigned(actorUserId, agent, connection.getId(), clock.instant());
    }

    private Agent requireManagerScopedAgent(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = agentRepository.findByIdAndWorkspaceId(agentId, workspaceId)
                .orElseThrow(AgentConnectionAssignmentService::notFound);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        return agent;
    }

    private Agent requireManagerScopedAgentForUpdate(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = agentRepository.findByIdAndWorkspaceIdForUpdate(agentId, workspaceId)
                .orElseThrow(AgentConnectionAssignmentService::notFound);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        return agent;
    }

    private Connection findConnection(UUID workspaceId, UUID connectionId) {
        return connectionRepository.findByIdAndWorkspace_Id(connectionId, workspaceId)
                .orElseThrow(AgentConnectionAssignmentService::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
    }
}
