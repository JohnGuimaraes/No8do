package com.no8do.api.agent;

import com.no8do.api.workitem.ProjectWorkItem;
import com.no8do.api.workitem.ProjectWorkItemRepository;
import com.no8do.api.workitem.ProjectWorkItemStatus;
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
public class AgentWorkItemAssignmentService {

    private final AgentRepository agentRepository;
    private final ProjectWorkItemRepository workItemRepository;
    private final AgentWorkItemAssignmentRepository assignmentRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final AgentRegistryAuditService auditService;
    private final Clock clock;

    public AgentWorkItemAssignmentService(AgentRepository agentRepository,
            ProjectWorkItemRepository workItemRepository, AgentWorkItemAssignmentRepository assignmentRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService, AgentRegistryAuditService auditService,
            Clock clock) {
        this.agentRepository = agentRepository;
        this.workItemRepository = workItemRepository;
        this.assignmentRepository = assignmentRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AgentWorkItemAssignmentResponse> list(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId);
        return assignmentRepository.findWorkItemSummaries(workspaceId, agent.getId());
    }

    @Transactional
    public void assign(UUID workspaceId, UUID agentId, UUID workItemId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgentForUpdate(workspaceId, agentId, actorUserId);
        ProjectWorkItem workItem = workItemRepository.findScopedForAgentAssignment(workItemId, workspaceId)
                .orElseThrow(AgentWorkItemAssignmentService::notFound);
        if (agent.getLifecycleStatus() == AgentLifecycleStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived Agents cannot receive WorkItem assignments");
        }
        if (workItem.getStatus() != ProjectWorkItemStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only OPEN WorkItems can be assigned to Agents");
        }

        Instant assignedAt = clock.instant();
        int inserted = assignmentRepository.insertIfAbsent(UUID.randomUUID(), agent.getId(), workItem.getId(),
                assignedAt, actorUserId);
        if (inserted == 1) {
            auditService.recordWorkItemAssigned(actorUserId, agent, workItem.getId(), workItem.getProject().getId(),
                    assignedAt);
        }
    }

    @Transactional
    public void unassign(UUID workspaceId, UUID agentId, UUID workItemId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId);
        ProjectWorkItem workItem = workItemRepository.findByIdAndProject_Workspace_Id(workItemId, workspaceId)
                .orElseThrow(AgentWorkItemAssignmentService::notFound);
        int deleted = assignmentRepository.deleteAssignment(agent.getId(), workItem.getId());
        if (deleted == 1) {
            auditService.recordWorkItemUnassigned(actorUserId, agent, workItem.getId(), workItem.getProject().getId(),
                    clock.instant());
        }
    }

    private Agent requireManagerScopedAgent(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = agentRepository.findByIdAndWorkspaceId(agentId, workspaceId)
                .orElseThrow(AgentWorkItemAssignmentService::notFound);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        return agent;
    }

    private Agent requireManagerScopedAgentForUpdate(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = agentRepository.findByIdAndWorkspaceIdForUpdate(agentId, workspaceId)
                .orElseThrow(AgentWorkItemAssignmentService::notFound);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        return agent;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
    }
}
